package com.buyogo.factoryevents.service;

import com.buyogo.factoryevents.dto.BatchIngestionResponse;
import com.buyogo.factoryevents.dto.EventRequest;
import com.buyogo.factoryevents.dto.RejectionDetail;
import com.buyogo.factoryevents.entity.Event;
import com.buyogo.factoryevents.repository.EventRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventService eventService;

    private Instant baseTime;
    private static final long MAX_DURATION_MS = 21_600_000L; // 6 hours

    @BeforeEach
    void setUp() {
        // Use current time to avoid time-based comparison issues with the service
        baseTime = Instant.now();
    }

    @AfterEach
    void tearDown() {
        reset(eventRepository);
    }

    @Test
    @Order(1)
    @DisplayName("Test 1: Identical duplicate eventId should be deduped")
    void testIdenticalDuplicateIsDeduped() {
        // Arrange
        EventRequest request = createValidEventRequest("event-001", baseTime, "machine-1", 1000L, 5);
        
        // Create existing event with same payload
        Event existingEvent = createEventFromRequest(request, baseTime);
        
        when(eventRepository.findAllById(List.of("event-001"))).thenReturn(List.of(existingEvent));

        // Act
        BatchIngestionResponse response = eventService.processBatch(List.of(request));

        // Assert
        assertThat(response.getAccepted()).isEqualTo(0);
        assertThat(response.getDeduped()).isEqualTo(1);
        assertThat(response.getUpdated()).isEqualTo(0);
        assertThat(response.getRejected()).isEqualTo(0);
        
        verify(eventRepository, times(1)).findAllById(List.of("event-001"));
        verify(eventRepository, never()).saveAll(anyList());
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Different payload with newer receivedTime should trigger update")
    void testDifferentPayloadNewerTimeUpdates() {
        // Arrange
        EventRequest newRequest = createValidEventRequest("event-002", baseTime, "machine-1", 2000L, 10);
        
        // Create existing event with different payload and older receivedTime
        Instant olderTime = baseTime.minus(Duration.ofMinutes(5));
        Event existingEvent = Event.builder()
                .eventId("event-002")
                .eventTime(baseTime.minus(Duration.ofHours(1)))
                .receivedTime(olderTime)
                .machineId("machine-1")
                .durationMs(1000L)
                .defectCount(5)
                .payloadHash("different-hash")
                .build();
        
        when(eventRepository.findAllById(List.of("event-002"))).thenReturn(List.of(existingEvent));
        when(eventRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        BatchIngestionResponse response = eventService.processBatch(List.of(newRequest));

        // Assert
        assertThat(response.getAccepted()).isEqualTo(0);
        assertThat(response.getDeduped()).isEqualTo(0);
        assertThat(response.getUpdated()).isEqualTo(1);
        assertThat(response.getRejected()).isEqualTo(0);
        
        verify(eventRepository, times(1)).findAllById(List.of("event-002"));
        verify(eventRepository, times(1)).saveAll(argThat(events -> {
            List<Event> eventList = (List<Event>) events;
            return eventList.size() == 1 &&
                   eventList.get(0).getDurationMs().equals(2000L) &&
                   eventList.get(0).getDefectCount().equals(10);
        }));
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: Different payload with older receivedTime should be ignored")
    void testDifferentPayloadOlderTimeIgnored() {
        // Arrange
        EventRequest oldRequest = createValidEventRequest("event-003", baseTime, "machine-1", 2000L, 10);
        
        // Create existing event with different payload but newer receivedTime
        Instant newerTime = baseTime.plus(Duration.ofMinutes(5));
        Event existingEvent = Event.builder()
                .eventId("event-003")
                .eventTime(baseTime)
                .receivedTime(newerTime)
                .machineId("machine-1")
                .durationMs(1000L)
                .defectCount(5)
                .payloadHash("different-hash")
                .build();
        
        when(eventRepository.findAllById(List.of("event-003"))).thenReturn(List.of(existingEvent));

        // Act
        BatchIngestionResponse response = eventService.processBatch(List.of(oldRequest));

        // Assert
        assertThat(response.getAccepted()).isEqualTo(0);
        assertThat(response.getDeduped()).isEqualTo(1);
        assertThat(response.getUpdated()).isEqualTo(0);
        assertThat(response.getRejected()).isEqualTo(0);
        
        verify(eventRepository, times(1)).findAllById(List.of("event-003"));
        verify(eventRepository, never()).saveAll(anyList());
    }

    @Test
    @Order(4)
    @DisplayName("Test 4a: Negative duration should be rejected")
    void testNegativeDurationRejected() {
        // Arrange
        EventRequest request = createValidEventRequest("event-004", baseTime, "machine-1", -100L, 5);
        
        when(eventRepository.findAllById(anyList())).thenReturn(List.of());

        // Act
        BatchIngestionResponse response = eventService.processBatch(List.of(request));

        // Assert
        assertThat(response.getAccepted()).isEqualTo(0);
        assertThat(response.getDeduped()).isEqualTo(0);
        assertThat(response.getUpdated()).isEqualTo(0);
        assertThat(response.getRejected()).isEqualTo(1);
        assertThat(response.getRejections()).hasSize(1);
        
        RejectionDetail rejection = response.getRejections().get(0);
        assertThat(rejection.getEventId()).isEqualTo("event-004");
        assertThat(rejection.getReason()).isEqualTo("INVALID_DURATION");
        
        verify(eventRepository, never()).saveAll(anyList());
    }

    @Test
    @Order(5)
    @DisplayName("Test 4b: Duration exceeding 6 hours should be rejected")
    void testDurationExceeding6HoursRejected() {
        // Arrange
        long invalidDuration = MAX_DURATION_MS + 1; // Just over 6 hours
        EventRequest request = createValidEventRequest("event-005", baseTime, "machine-1", invalidDuration, 5);
        
        when(eventRepository.findAllById(anyList())).thenReturn(List.of());

        // Act
        BatchIngestionResponse response = eventService.processBatch(List.of(request));

        // Assert
        assertThat(response.getAccepted()).isEqualTo(0);
        assertThat(response.getRejected()).isEqualTo(1);
        assertThat(response.getRejections()).hasSize(1);
        
        RejectionDetail rejection = response.getRejections().get(0);
        assertThat(rejection.getEventId()).isEqualTo("event-005");
        assertThat(rejection.getReason()).isEqualTo("INVALID_DURATION");
        
        verify(eventRepository, never()).saveAll(anyList());
    }

    @Test
    @Order(6)
    @DisplayName("Test 5: Future eventTime beyond 15 minutes should be rejected")
    void testFutureEventTimeRejected() {
        // Arrange - Event time 20 minutes in the future
        Instant futureTime = baseTime.plus(Duration.ofMinutes(20));
        EventRequest request = createValidEventRequest("event-006", futureTime, "machine-1", 1000L, 5);
        
        when(eventRepository.findAllById(anyList())).thenReturn(List.of());

        // Act
        BatchIngestionResponse response = eventService.processBatch(List.of(request));

        // Assert
        assertThat(response.getAccepted()).isEqualTo(0);
        assertThat(response.getRejected()).isEqualTo(1);
        assertThat(response.getRejections()).hasSize(1);
        
        RejectionDetail rejection = response.getRejections().get(0);
        assertThat(rejection.getEventId()).isEqualTo("event-006");
        assertThat(rejection.getReason()).isEqualTo("FUTURE_EVENT_TIME");
        
        verify(eventRepository, never()).saveAll(anyList());
    }

    @Test
    @Order(7)
    @DisplayName("Test 6: defectCount = -1 should be stored (logic tested elsewhere)")
    void testDefectCountNegativeOneStored() {
        // Arrange
        EventRequest request = createValidEventRequest("event-007", baseTime, "machine-1", 1000L, -1);
        
        when(eventRepository.findAllById(List.of("event-007"))).thenReturn(List.of());
        when(eventRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        BatchIngestionResponse response = eventService.processBatch(List.of(request));

        // Assert
        assertThat(response.getAccepted()).isEqualTo(1);
        assertThat(response.getDeduped()).isEqualTo(0);
        assertThat(response.getUpdated()).isEqualTo(0);
        assertThat(response.getRejected()).isEqualTo(0);
        
        verify(eventRepository, times(1)).saveAll(argThat(events -> {
            List<Event> eventList = (List<Event>) events;
            return eventList.size() == 1 &&
                   eventList.get(0).getEventId().equals("event-007") &&
                   eventList.get(0).getDefectCount().equals(-1);
        }));
    }

    @Test
    @Order(8)
    @DisplayName("Test 7: Valid event should be accepted")
    void testValidEventAccepted() {
        // Arrange
        EventRequest request = createValidEventRequest("event-008", baseTime, "machine-1", 1000L, 5);
        
        when(eventRepository.findAllById(List.of("event-008"))).thenReturn(List.of());
        when(eventRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        BatchIngestionResponse response = eventService.processBatch(List.of(request));

        // Assert
        assertThat(response.getAccepted()).isEqualTo(1);
        assertThat(response.getDeduped()).isEqualTo(0);
        assertThat(response.getUpdated()).isEqualTo(0);
        assertThat(response.getRejected()).isEqualTo(0);
        assertThat(response.getRejections()).isEmpty();
        
        verify(eventRepository, times(1)).saveAll(anyList());
    }

    @Test
    @Order(9)
    @DisplayName("Test batch with mixed scenarios")
    void testMixedBatchProcessing() {
        // Arrange
        List<EventRequest> requests = Arrays.asList(
            createValidEventRequest("event-101", baseTime, "machine-1", 1000L, 5),           // New - should accept
            createValidEventRequest("event-102", baseTime.plus(Duration.ofMinutes(20)), "machine-1", 1000L, 5), // Future - should reject
            createValidEventRequest("event-103", baseTime, "machine-1", -500L, 5),           // Negative duration - should reject
            createValidEventRequest("event-104", baseTime, "machine-1", 2000L, 10)           // New - should accept
        );
        
        when(eventRepository.findAllById(anyList())).thenReturn(List.of());
        when(eventRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        BatchIngestionResponse response = eventService.processBatch(requests);

        // Assert
        assertThat(response.getAccepted()).isEqualTo(2);
        assertThat(response.getRejected()).isEqualTo(2);
        assertThat(response.getRejections()).hasSize(2);
        
        verify(eventRepository, times(1)).saveAll(argThat(events -> {
            List<Event> eventList = (List<Event>) events;
            return eventList.size() == 2;
        }));
    }

    @Test
    @Order(10)
    @DisplayName("Test missing required fields")
    void testMissingRequiredFields() {
        // Arrange - Missing eventId
        EventRequest noEventId = createValidEventRequest(null, baseTime, "machine-1", 1000L, 5);
        
        // Missing eventTime
        EventRequest noEventTime = EventRequest.builder()
                .eventId("event-201")
                .eventTime(null)
                .machineId("machine-1")
                .durationMs(1000L)
                .defectCount(5)
                .build();
        
        // Missing machineId
        EventRequest noMachineId = createValidEventRequest("event-202", baseTime, null, 1000L, 5);
        
        // Missing duration
        EventRequest noDuration = EventRequest.builder()
                .eventId("event-203")
                .eventTime(baseTime)
                .machineId("machine-1")
                .durationMs(null)
                .defectCount(5)
                .build();
        
        // Missing defectCount
        EventRequest noDefectCount = EventRequest.builder()
                .eventId("event-204")
                .eventTime(baseTime)
                .machineId("machine-1")
                .durationMs(1000L)
                .defectCount(null)
                .build();
        
        List<EventRequest> requests = Arrays.asList(noEventId, noEventTime, noMachineId, noDuration, noDefectCount);

        // Act
        BatchIngestionResponse response = eventService.processBatch(requests);

        // Assert
        assertThat(response.getRejected()).isEqualTo(5);
        assertThat(response.getRejections()).hasSize(5);
        
        verify(eventRepository, never()).saveAll(anyList());
    }

    @Test
    @Order(11)
    @DisplayName("Test duration boundary values")
    void testDurationBoundaryValues() {
        // Arrange
        EventRequest zeroDuration = createValidEventRequest("event-301", baseTime, "machine-1", 0L, 5);
        EventRequest maxValidDuration = createValidEventRequest("event-302", baseTime, "machine-1", MAX_DURATION_MS, 5);
        
        when(eventRepository.findAllById(Arrays.asList("event-301", "event-302"))).thenReturn(List.of());
        when(eventRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        BatchIngestionResponse response = eventService.processBatch(Arrays.asList(zeroDuration, maxValidDuration));

        // Assert
        assertThat(response.getAccepted()).isEqualTo(2);
        assertThat(response.getRejected()).isEqualTo(0);
        
        verify(eventRepository, times(1)).saveAll(argThat(events -> {
            List<Event> eventList = (List<Event>) events;
            return eventList.size() == 2;
        }));
    }

    @Test
    @Order(12)
    @DisplayName("Test eventTime boundary - exactly 15 minutes in future should be accepted")
    void testEventTimeBoundary() {
        // Arrange - Exactly 15 minutes in the future (should be accepted)
        Instant exactly15Min = baseTime.plus(Duration.ofMinutes(15));
        EventRequest request = createValidEventRequest("event-401", exactly15Min, "machine-1", 1000L, 5);
        
        when(eventRepository.findAllById(List.of("event-401"))).thenReturn(List.of());
        when(eventRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        BatchIngestionResponse response = eventService.processBatch(List.of(request));

        // Assert
        assertThat(response.getAccepted()).isEqualTo(1);
        assertThat(response.getRejected()).isEqualTo(0);
        
        verify(eventRepository, times(1)).saveAll(anyList());
    }

    // Helper methods
    private EventRequest createValidEventRequest(String eventId, Instant eventTime, String machineId, Long duration, Integer defectCount) {
        return EventRequest.builder()
                .eventId(eventId)
                .eventTime(eventTime)
                .machineId(machineId)
                .lineId("line-1")
                .factoryId("factory-1")
                .durationMs(duration)
                .defectCount(defectCount)
                .build();
    }

    private Event createEventFromRequest(EventRequest request, Instant receivedTime) {
        return Event.builder()
                .eventId(request.getEventId())
                .eventTime(request.getEventTime())
                .receivedTime(receivedTime)
                .machineId(request.getMachineId())
                .lineId(request.getLineId())
                .factoryId(request.getFactoryId())
                .durationMs(request.getDurationMs())
                .defectCount(request.getDefectCount())
                .payloadHash(calculateExpectedHash(request))
                .build();
    }

    private String calculateExpectedHash(EventRequest request) {
        // This mimics the hash calculation in EventService
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
}
