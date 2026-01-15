package com.buyogo.factoryevents.integration;

import com.buyogo.factoryevents.dto.BatchIngestionResponse;
import com.buyogo.factoryevents.dto.EventRequest;
import com.buyogo.factoryevents.entity.Event;
import com.buyogo.factoryevents.repository.EventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Slf4j
class PerformanceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRepository eventRepository;

    private Instant baseTime;

    @BeforeEach
    void setUp() {
        baseTime = Instant.now();
        eventRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        eventRepository.deleteAll();
    }

    @Test
    @Order(1)
    @DisplayName("Performance Test: Ingest 1000 events in single batch - should complete < 1 second")
    void testIngest1000EventsPerformance() throws Exception {
        // Arrange
        int eventCount = 1000;
        List<EventRequest> events = new ArrayList<>();
        
        for (int i = 0; i < eventCount; i++) {
            events.add(createValidEvent(
                "perf-event-" + i, 
                baseTime.minusSeconds(i), 
                "machine-" + (i % 10),  // 10 different machines
                1000L + (i % 100),      // Varying durations
                i % 20                   // Varying defect counts
            ));
        }

        String requestBody = objectMapper.writeValueAsString(events);
        
        log.info("Starting performance test: Ingesting {} events", eventCount);
        long startTime = System.currentTimeMillis();

        // Act
        MvcResult result = mockMvc.perform(post("/events/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        String responseBody = result.getResponse().getContentAsString();
        BatchIngestionResponse response = objectMapper.readValue(responseBody, BatchIngestionResponse.class);

        // Assert
        log.info("Performance test completed in {} ms", duration);
        log.info("Response - Accepted: {}, Deduped: {}, Updated: {}, Rejected: {}", 
                response.getAccepted(), response.getDeduped(), response.getUpdated(), response.getRejected());
        
        assertThat(response.getAccepted()).isEqualTo(eventCount);
        assertThat(response.getRejected()).isEqualTo(0);
        assertThat(duration).as("Ingestion of 1000 events should complete in less than 1 second")
                .isLessThan(1000);

        // Verify database
        List<Event> savedEvents = eventRepository.findAll();
        assertThat(savedEvents).hasSize(eventCount);
        
        log.info("Successfully verified {} events in database", savedEvents.size());
    }

    @Test
    @Order(2)
    @DisplayName("Performance Test: Ingest 1000 events with duplicates - measure deduplication performance")
    void testIngest1000EventsWithDuplicatesPerformance() throws Exception {
        // Arrange - First batch of 500 events
        int halfCount = 500;
        List<EventRequest> firstBatch = new ArrayList<>();
        
        for (int i = 0; i < halfCount; i++) {
            firstBatch.add(createValidEvent(
                "dup-event-" + i, 
                baseTime.minusSeconds(i), 
                "machine-1",
                1000L, 
                5
            ));
        }

        String firstRequestBody = objectMapper.writeValueAsString(firstBatch);

        // Send first batch
        log.info("Sending first batch of {} events", halfCount);
        mockMvc.perform(post("/events/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(firstRequestBody))
                .andExpect(status().isOk());

        // Arrange - Second batch with 500 duplicates + 500 new events
        List<EventRequest> secondBatch = new ArrayList<>();
        
        // Add duplicates (same eventIds as first batch)
        for (int i = 0; i < halfCount; i++) {
            secondBatch.add(createValidEvent(
                "dup-event-" + i,  // Same eventIds
                baseTime.minusSeconds(i), 
                "machine-1",
                1000L, 
                5
            ));
        }
        
        // Add new events
        for (int i = halfCount; i < halfCount * 2; i++) {
            secondBatch.add(createValidEvent(
                "dup-event-" + i,  // New eventIds
                baseTime.minusSeconds(i), 
                "machine-1",
                1000L, 
                5
            ));
        }

        String secondRequestBody = objectMapper.writeValueAsString(secondBatch);
        
        log.info("Starting deduplication performance test: Ingesting {} events (500 duplicates + 500 new)", 
                secondBatch.size());
        long startTime = System.currentTimeMillis();

        // Act
        MvcResult result = mockMvc.perform(post("/events/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(secondRequestBody))
                .andExpect(status().isOk())
                .andReturn();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        String responseBody = result.getResponse().getContentAsString();
        BatchIngestionResponse response = objectMapper.readValue(responseBody, BatchIngestionResponse.class);

        // Assert
        log.info("Deduplication performance test completed in {} ms", duration);
        log.info("Response - Accepted: {}, Deduped: {}, Updated: {}, Rejected: {}", 
                response.getAccepted(), response.getDeduped(), response.getUpdated(), response.getRejected());
        
        assertThat(response.getAccepted()).isEqualTo(halfCount);
        assertThat(response.getDeduped()).isEqualTo(halfCount);
        assertThat(duration).as("Deduplication of 1000 events should complete in less than 1 second")
                .isLessThan(1000);

        // Verify database has correct count
        List<Event> savedEvents = eventRepository.findAll();
        assertThat(savedEvents).hasSize(halfCount * 2); // 500 from first batch + 500 new from second batch
        
        log.info("Successfully verified {} unique events in database after deduplication", savedEvents.size());
    }

    @Test
    @Order(3)
    @DisplayName("Performance Test: Ingest 2000 events - extended performance test")
    void testIngest2000EventsPerformance() throws Exception {
        // Arrange
        int eventCount = 2000;
        List<EventRequest> events = new ArrayList<>();
        
        for (int i = 0; i < eventCount; i++) {
            events.add(createValidEvent(
                "large-perf-event-" + i, 
                baseTime.minusSeconds(i), 
                "machine-" + (i % 20),  // 20 different machines
                1000L + (i % 200),      // Varying durations
                i % 30                   // Varying defect counts
            ));
        }

        String requestBody = objectMapper.writeValueAsString(events);
        
        log.info("Starting extended performance test: Ingesting {} events", eventCount);
        long startTime = System.currentTimeMillis();

        // Act
        MvcResult result = mockMvc.perform(post("/events/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        String responseBody = result.getResponse().getContentAsString();
        BatchIngestionResponse response = objectMapper.readValue(responseBody, BatchIngestionResponse.class);

        // Assert
        log.info("Extended performance test completed in {} ms", duration);
        log.info("Throughput: {} events/second", (eventCount * 1000.0) / duration);
        log.info("Response - Accepted: {}, Deduped: {}, Updated: {}, Rejected: {}", 
                response.getAccepted(), response.getDeduped(), response.getUpdated(), response.getRejected());
        
        assertThat(response.getAccepted()).isEqualTo(eventCount);
        assertThat(response.getRejected()).isEqualTo(0);
        
        // For 2000 events, allow up to 2 seconds (still very fast)
        assertThat(duration).as("Ingestion of 2000 events should complete in less than 2 seconds")
                .isLessThan(2000);

        // Verify database
        List<Event> savedEvents = eventRepository.findAll();
        assertThat(savedEvents).hasSize(eventCount);
        
        log.info("Successfully verified {} events in database", savedEvents.size());
    }

    @Test
    @Order(4)
    @DisplayName("Performance Test: Ingest 1000 events with mixed scenarios")
    void testIngest1000EventsMixedScenarios() throws Exception {
        // Arrange - Create a realistic mix
        int validCount = 850;
        int invalidCount = 150;
        int totalCount = validCount + invalidCount;
        List<EventRequest> events = new ArrayList<>();
        
        // Valid events (850)
        for (int i = 0; i < validCount; i++) {
            events.add(createValidEvent(
                "mixed-event-" + i, 
                baseTime.minusSeconds(i * 10), 
                "machine-" + (i % 15),
                1000L + i, 
                i % 25
            ));
        }
        
        // Invalid events (150) - mix of different validation failures
        for (int i = validCount; i < totalCount; i++) {
            if (i % 3 == 0) {
                // Negative duration
                events.add(createValidEvent("invalid-event-" + i, baseTime, "machine-1", -100L, 5));
            } else if (i % 3 == 1) {
                // Future time (> 15 minutes)
                events.add(createValidEvent("invalid-event-" + i, 
                        baseTime.plus(Duration.ofMinutes(20)), "machine-1", 1000L, 5));
            } else {
                // Excessive duration (> 6 hours)
                events.add(createValidEvent("invalid-event-" + i, baseTime, "machine-1", 
                        21_600_001L, 5));
            }
        }

        String requestBody = objectMapper.writeValueAsString(events);
        
        log.info("Starting mixed scenarios performance test: Ingesting {} events ({} valid, {} invalid)", 
                totalCount, validCount, invalidCount);
        long startTime = System.currentTimeMillis();

        // Act
        MvcResult result = mockMvc.perform(post("/events/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        String responseBody = result.getResponse().getContentAsString();
        BatchIngestionResponse response = objectMapper.readValue(responseBody, BatchIngestionResponse.class);

        // Assert
        log.info("Mixed scenarios performance test completed in {} ms", duration);
        log.info("Response - Accepted: {}, Deduped: {}, Updated: {}, Rejected: {}", 
                response.getAccepted(), response.getDeduped(), response.getUpdated(), response.getRejected());
        
        assertThat(response.getAccepted()).isEqualTo(validCount);
        assertThat(response.getRejected()).isEqualTo(invalidCount);
        assertThat(duration).as("Ingestion of 1000 mixed events should complete in less than 1 second")
                .isLessThan(1000);

        // Verify database
        List<Event> savedEvents = eventRepository.findAll();
        assertThat(savedEvents).hasSize(validCount);
        
        log.info("Successfully processed mixed batch: {} valid events stored, {} invalid events rejected", 
                savedEvents.size(), invalidCount);
    }

    @Test
    @Order(5)
    @DisplayName("Performance Test: Measure update performance with 500 updates")
    void testUpdatePerformance() throws Exception {
        // Arrange - Create initial events
        int eventCount = 500;
        List<EventRequest> initialBatch = new ArrayList<>();
        
        for (int i = 0; i < eventCount; i++) {
            initialBatch.add(createValidEvent(
                "update-event-" + i, 
                baseTime.minusSeconds(i * 10), 
                "machine-1",
                1000L, 
                5
            ));
        }

        String initialRequestBody = objectMapper.writeValueAsString(initialBatch);

        // Send initial batch
        log.info("Sending initial batch of {} events", eventCount);
        mockMvc.perform(post("/events/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(initialRequestBody))
                .andExpect(status().isOk());

        // Small delay to ensure different receivedTime
        Thread.sleep(100);

        // Arrange - Create update batch with different payloads
        List<EventRequest> updateBatch = new ArrayList<>();
        
        for (int i = 0; i < eventCount; i++) {
            updateBatch.add(createValidEvent(
                "update-event-" + i,  // Same eventIds
                baseTime.minusSeconds(i * 5),  // Different eventTime
                "machine-2",           // Different machineId
                2000L,                 // Different duration
                10                     // Different defectCount
            ));
        }

        String updateRequestBody = objectMapper.writeValueAsString(updateBatch);
        
        log.info("Starting update performance test: Updating {} events", eventCount);
        long startTime = System.currentTimeMillis();

        // Act
        MvcResult result = mockMvc.perform(post("/events/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequestBody))
                .andExpect(status().isOk())
                .andReturn();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        String responseBody = result.getResponse().getContentAsString();
        BatchIngestionResponse response = objectMapper.readValue(responseBody, BatchIngestionResponse.class);

        // Assert
        log.info("Update performance test completed in {} ms", duration);
        log.info("Response - Accepted: {}, Deduped: {}, Updated: {}, Rejected: {}", 
                response.getAccepted(), response.getDeduped(), response.getUpdated(), response.getRejected());
        
        assertThat(response.getUpdated()).isEqualTo(eventCount);
        assertThat(response.getAccepted()).isEqualTo(0);
        assertThat(duration).as("Updating 500 events should complete in less than 1 second")
                .isLessThan(1000);

        // Verify database - count should remain the same
        List<Event> savedEvents = eventRepository.findAll();
        assertThat(savedEvents).hasSize(eventCount);
        
        // Verify updates were applied
        Event firstEvent = eventRepository.findByEventId("update-event-0").orElseThrow();
        assertThat(firstEvent.getMachineId()).isEqualTo("machine-2");
        assertThat(firstEvent.getDurationMs()).isEqualTo(2000L);
        assertThat(firstEvent.getDefectCount()).isEqualTo(10);
        
        log.info("Successfully verified {} events were updated correctly", eventCount);
    }

    // Helper method
    private EventRequest createValidEvent(String eventId, Instant eventTime, String machineId, 
                                         Long durationMs, Integer defectCount) {
        return EventRequest.builder()
                .eventId(eventId)
                .eventTime(eventTime)
                .machineId(machineId)
                .lineId("line-1")
                .factoryId("factory-1")
                .durationMs(durationMs)
                .defectCount(defectCount)
                .build();
    }
}
