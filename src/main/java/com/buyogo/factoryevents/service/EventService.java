package com.buyogo.factoryevents.service;

import com.buyogo.factoryevents.dto.*;
import com.buyogo.factoryevents.entity.Event;
import com.buyogo.factoryevents.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {
    
    private static final long MAX_DURATION_MS = 21_600_000L; // 6 hours
    private static final long FUTURE_TIME_THRESHOLD_MINUTES = 15;
    
    private final EventRepository eventRepository;
    
    @Transactional
    public BatchIngestionResponse processBatch(List<EventRequest> events) {
        int accepted = 0;
        int deduped = 0;
        int updated = 0;
        int rejected = 0;
        List<RejectionDetail> rejections = new ArrayList<>();

        Instant now = Instant.now();

        // First pass: validate and collect valid event IDs
        List<EventRequest> validEvents = new ArrayList<>();
        List<String> eventIds = new ArrayList<>();

        for (EventRequest request : events) {
            String validationError = validateEvent(request, now);
            if (validationError != null) {
                rejected++;
                rejections.add(RejectionDetail.builder()
                    .eventId(request.getEventId())
                    .reason(validationError)
                    .build());
            } else {
                validEvents.add(request);
                eventIds.add(request.getEventId());
            }
        }

        // Bulk fetch all existing events in a single query
        List<Event> existingEvents = eventRepository.findAllById(eventIds);
        java.util.Map<String, Event> existingEventMap = new java.util.HashMap<>();
        for (Event event : existingEvents) {
            existingEventMap.put(event.getEventId(), event);
        }

        // Prepare lists for batch operations
        List<Event> eventsToSave = new ArrayList<>();
        List<Event> eventsToUpdate = new ArrayList<>();
        List<String> newEventIds = new ArrayList<>();

        // Process valid events
        for (EventRequest request : validEvents) {
            try {
                String payloadHash = calculatePayloadHash(request);
                Event existing = existingEventMap.get(request.getEventId());

                if (existing != null) {
                    // Check if payload is identical (dedupe case)
                    if (existing.getPayloadHash().equals(payloadHash)) {
                        deduped++;
                        log.debug("Deduped event: {}", request.getEventId());
                    } else {
                        // Different payload - check receivedTime for update logic
                        if (now.isAfter(existing.getReceivedTime())) {
                            // Update with new data
                            updateEvent(existing, request, now, payloadHash);
                            eventsToUpdate.add(existing);
                            updated++;
                            log.debug("Updated event: {}", request.getEventId());
                        } else {
                            // Older receivedTime - ignore
                            deduped++;
                            log.debug("Ignored older update for event: {}", request.getEventId());
                        }
                    }
                } else {
                    // New event
                    Event newEvent = createEvent(request, now, payloadHash);
                    eventsToSave.add(newEvent);
                    newEventIds.add(request.getEventId());
                    accepted++;
                    log.debug("Accepted new event: {}", request.getEventId());
                }
            } catch (Exception e) {
                rejected++;
                rejections.add(RejectionDetail.builder()
                    .eventId(request.getEventId())
                    .reason("PROCESSING_ERROR: " + e.getMessage())
                    .build());
                log.error("Error processing event {}: {}", request.getEventId(), e.getMessage());
            }
        }
        
        // Batch save new events
        if (!eventsToSave.isEmpty()) {
            try {
                eventRepository.saveAll(eventsToSave);
            } catch (DataIntegrityViolationException ex) {
                // Concurrent insert race: another transaction inserted the same event_id(s).
                // Treat these as deduped and proceed.
                log.debug("Concurrent insert conflict during batch save; treating as deduped", ex);

                // Re-fetch by IDs to confirm what exists now.
                List<Event> nowExisting = eventRepository.findAllById(newEventIds);
                int actuallyInserted = nowExisting.size();

                // We already counted all new events as accepted. Adjust counters:
                // accepted = number actually inserted by this transaction (best effort)
                // deduped += acceptedPreviously - actuallyInserted
                int conflicted = Math.max(0, accepted - actuallyInserted);
                deduped += conflicted;
                accepted = actuallyInserted;
            }
        }

        // Batch save updated events
        if (!eventsToUpdate.isEmpty()) {
            eventRepository.saveAll(eventsToUpdate);
        }

        return BatchIngestionResponse.builder()
                .accepted(accepted)
                .deduped(deduped)
                .updated(updated)
                .rejected(rejected)
                .rejections(rejections)
                .build();
    }
    
    private String validateEvent(EventRequest request, Instant now) {
        // Check required fields
        if (request.getEventId() == null || request.getEventId().isEmpty()) {
            return "MISSING_EVENT_ID";
        }
        if (request.getEventTime() == null) {
            return "MISSING_EVENT_TIME";
        }
        if (request.getMachineId() == null || request.getMachineId().isEmpty()) {
            return "MISSING_MACHINE_ID";
        }
        if (request.getDurationMs() == null) {
            return "MISSING_DURATION";
        }
        if (request.getDefectCount() == null) {
            return "MISSING_DEFECT_COUNT";
        }
        
        // Validate duration
        if (request.getDurationMs() < 0 || request.getDurationMs() > MAX_DURATION_MS) {
            return "INVALID_DURATION";
        }
        
        // Validate eventTime is not too far in the future
        Instant maxFutureTime = now.plus(Duration.ofMinutes(FUTURE_TIME_THRESHOLD_MINUTES));
        if (request.getEventTime().isAfter(maxFutureTime)) {
            return "FUTURE_EVENT_TIME";
        }
        
        return null;
    }
    
    private String calculatePayloadHash(EventRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder payload = new StringBuilder()
                .append(request.getEventTime())
                .append(request.getMachineId())
                .append(request.getLineId() != null ? request.getLineId() : "")
                .append(request.getFactoryId() != null ? request.getFactoryId() : "")
                .append(request.getDurationMs())
                .append(request.getDefectCount());
            
            byte[] hash = digest.digest(payload.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    private Event createEvent(EventRequest request, Instant receivedTime, String payloadHash) {
        return Event.builder()
            .eventId(request.getEventId())
            .eventTime(request.getEventTime())
            .receivedTime(receivedTime)
            .machineId(request.getMachineId())
            .lineId(request.getLineId())
            .factoryId(request.getFactoryId())
            .durationMs(request.getDurationMs())
            .defectCount(request.getDefectCount())
            .payloadHash(payloadHash)
            .build();
    }
    
    private void updateEvent(Event event, EventRequest request, Instant receivedTime, String payloadHash) {
        event.setEventTime(request.getEventTime());
        event.setReceivedTime(receivedTime);
        event.setMachineId(request.getMachineId());
        event.setLineId(request.getLineId());
        event.setFactoryId(request.getFactoryId());
        event.setDurationMs(request.getDurationMs());
        event.setDefectCount(request.getDefectCount());
        event.setPayloadHash(payloadHash);
    }
}
