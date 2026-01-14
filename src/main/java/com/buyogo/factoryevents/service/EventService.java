package com.buyogo.factoryevents.service;

import com.buyogo.factoryevents.dto.*;
import com.buyogo.factoryevents.entity.Event;
import com.buyogo.factoryevents.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
        
        for (EventRequest request : events) {
            try {
                // Validate event
                String validationError = validateEvent(request, now);
                if (validationError != null) {
                    rejected++;
                    rejections.add(RejectionDetail.builder()
                        .eventId(request.getEventId())
                        .reason(validationError)
                        .build());
                    continue;
                }
                
                // Set receivedTime (ignore any from request)
                Instant receivedTime = now;
                
                // Calculate payload hash
                String payloadHash = calculatePayloadHash(request);
                
                // Check for existing event
                Optional<Event> existingEvent = eventRepository.findByEventId(request.getEventId());
                
                if (existingEvent.isPresent()) {
                    Event existing = existingEvent.get();
                    
                    // Check if payload is identical (dedupe case)
                    if (existing.getPayloadHash().equals(payloadHash)) {
                        deduped++;
                        log.debug("Deduped event: {}", request.getEventId());
                    } else {
                        // Different payload - check receivedTime for update logic
                        if (receivedTime.isAfter(existing.getReceivedTime())) {
                            // Update with new data
                            updateEvent(existing, request, receivedTime, payloadHash);
                            eventRepository.save(existing);
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
                    Event newEvent = createEvent(request, receivedTime, payloadHash);
                    eventRepository.save(newEvent);
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
