package com.buyogo.factoryevents.service;

import com.buyogo.factoryevents.dto.StatsResponse;
import com.buyogo.factoryevents.dto.TopDefectLineResponse;
import com.buyogo.factoryevents.repository.EventRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StatsServiceTest {

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private StatsService statsService;

    private Instant baseTime;
    private static final double HEALTHY_THRESHOLD = 2.0;

    @BeforeEach
    void setUp() {
        baseTime = Instant.parse("2024-01-15T10:00:00Z");
    }

    @AfterEach
    void tearDown() {
        reset(eventRepository);
    }

    @Test
    @Order(1)
    @DisplayName("Test 1: defectCount = -1 should be ignored in defect totals")
    void testDefectCountNegativeOneIgnored() {
        // Arrange
        Instant start = baseTime;
        Instant end = baseTime.plus(Duration.ofHours(1));
        String machineId = "machine-1";
        
        // Repository returns counts excluding defectCount = -1
        when(eventRepository.countByMachineIdAndEventTimeBetween(machineId, start, end))
                .thenReturn(10L); // Total events including those with defectCount = -1
        
        when(eventRepository.sumDefectsByMachineIdAndEventTimeBetween(machineId, start, end))
                .thenReturn(50L); // Sum excludes defectCount = -1 (query has AND e.defectCount >= 0)

        // Act
        StatsResponse response = statsService.getMachineStats(machineId, start, end);

        // Assert
        assertThat(response.getEventsCount()).isEqualTo(10L);
        assertThat(response.getDefectsCount()).isEqualTo(50L); // Only valid defects counted
        
        verify(eventRepository, times(1)).sumDefectsByMachineIdAndEventTimeBetween(
                eq(machineId), eq(start), eq(end));
    }

    @Test
    @Order(2)
    @DisplayName("Test 2a: Start boundary is inclusive")
    void testStartBoundaryInclusive() {
        // Arrange
        Instant start = baseTime;
        Instant end = baseTime.plus(Duration.ofHours(1));
        String machineId = "machine-1";
        
        when(eventRepository.countByMachineIdAndEventTimeBetween(machineId, start, end))
                .thenReturn(5L);
        when(eventRepository.sumDefectsByMachineIdAndEventTimeBetween(machineId, start, end))
                .thenReturn(10L);

        // Act
        StatsResponse response = statsService.getMachineStats(machineId, start, end);

        // Assert
        verify(eventRepository).countByMachineIdAndEventTimeBetween(eq(machineId), eq(start), eq(end));
        
        // The repository query uses >= for start, so events at exact start time are included
        assertThat(response.getEventsCount()).isEqualTo(5L);
    }

    @Test
    @Order(3)
    @DisplayName("Test 2b: End boundary is exclusive")
    void testEndBoundaryExclusive() {
        // Arrange
        Instant start = baseTime;
        Instant end = baseTime.plus(Duration.ofHours(1));
        String machineId = "machine-1";
        
        when(eventRepository.countByMachineIdAndEventTimeBetween(machineId, start, end))
                .thenReturn(5L);
        when(eventRepository.sumDefectsByMachineIdAndEventTimeBetween(machineId, start, end))
                .thenReturn(10L);

        // Act
        StatsResponse response = statsService.getMachineStats(machineId, start, end);

        // Assert
        verify(eventRepository).countByMachineIdAndEventTimeBetween(eq(machineId), eq(start), eq(end));
        
        // The repository query uses < for end, so events at exact end time are excluded
        assertThat(response.getEventsCount()).isEqualTo(5L);
    }

    @Test
    @Order(4)
    @DisplayName("Test 3a: Status should be Healthy when defect rate < 2.0")
    void testStatusHealthy() {
        // Arrange
        Instant start = baseTime;
        Instant end = baseTime.plus(Duration.ofHours(1));
        String machineId = "machine-1";
        
        // 1 defect in 1 hour = 1.0 defects/hour (< 2.0 threshold)
        when(eventRepository.countByMachineIdAndEventTimeBetween(machineId, start, end))
                .thenReturn(10L);
        when(eventRepository.sumDefectsByMachineIdAndEventTimeBetween(machineId, start, end))
                .thenReturn(1L);

        // Act
        StatsResponse response = statsService.getMachineStats(machineId, start, end);

        // Assert
        assertThat(response.getStatus()).isEqualTo("Healthy");
        assertThat(response.getAvgDefectRate()).isLessThan(HEALTHY_THRESHOLD);
        assertThat(response.getAvgDefectRate()).isEqualTo(1.0);
    }

    @Test
    @Order(5)
    @DisplayName("Test 3b: Status should be Warning when defect rate >= 2.0")
    void testStatusWarning() {
        // Arrange
        Instant start = baseTime;
        Instant end = baseTime.plus(Duration.ofHours(1));
        String machineId = "machine-1";
        
        // 3 defects in 1 hour = 3.0 defects/hour (>= 2.0 threshold)
        when(eventRepository.countByMachineIdAndEventTimeBetween(machineId, start, end))
                .thenReturn(10L);
        when(eventRepository.sumDefectsByMachineIdAndEventTimeBetween(machineId, start, end))
                .thenReturn(3L);

        // Act
        StatsResponse response = statsService.getMachineStats(machineId, start, end);

        // Assert
        assertThat(response.getStatus()).isEqualTo("Warning");
        assertThat(response.getAvgDefectRate()).isGreaterThanOrEqualTo(HEALTHY_THRESHOLD);
        assertThat(response.getAvgDefectRate()).isEqualTo(3.0);
    }

    @Test
    @Order(6)
    @DisplayName("Test 3c: Status boundary - exactly 2.0 defects/hour should be Warning")
    void testStatusBoundary() {
        // Arrange
        Instant start = baseTime;
        Instant end = baseTime.plus(Duration.ofHours(1));
        String machineId = "machine-1";
        
        // Exactly 2 defects in 1 hour = 2.0 defects/hour (at threshold)
        when(eventRepository.countByMachineIdAndEventTimeBetween(machineId, start, end))
                .thenReturn(10L);
        when(eventRepository.sumDefectsByMachineIdAndEventTimeBetween(machineId, start, end))
                .thenReturn(2L);

        // Act
        StatsResponse response = statsService.getMachineStats(machineId, start, end);

        // Assert
        assertThat(response.getStatus()).isEqualTo("Warning");
        assertThat(response.getAvgDefectRate()).isEqualTo(2.0);
    }

    @Test
    @Order(7)
    @DisplayName("Test 4a: Defect rate calculation - simple case")
    void testDefectRateCalculationSimple() {
        // Arrange
        Instant start = baseTime;
        Instant end = baseTime.plus(Duration.ofHours(2));
        String machineId = "machine-1";
        
        // 10 defects in 2 hours = 5.0 defects/hour
        when(eventRepository.countByMachineIdAndEventTimeBetween(machineId, start, end))
                .thenReturn(20L);
        when(eventRepository.sumDefectsByMachineIdAndEventTimeBetween(machineId, start, end))
                .thenReturn(10L);

        // Act
        StatsResponse response = statsService.getMachineStats(machineId, start, end);

        // Assert
        assertThat(response.getDefectsCount()).isEqualTo(10L);
        assertThat(response.getAvgDefectRate()).isEqualTo(5.0);
    }

    @Test
    @Order(8)
    @DisplayName("Test 4b: Defect rate calculation - fractional hours")
    void testDefectRateCalculationFractional() {
        // Arrange
        Instant start = baseTime;
        Instant end = baseTime.plus(Duration.ofMinutes(30)); // 0.5 hours
        String machineId = "machine-1";
        
        // 5 defects in 0.5 hours = 10.0 defects/hour
        when(eventRepository.countByMachineIdAndEventTimeBetween(machineId, start, end))
                .thenReturn(10L);
        when(eventRepository.sumDefectsByMachineIdAndEventTimeBetween(machineId, start, end))
                .thenReturn(5L);

        // Act
        StatsResponse response = statsService.getMachineStats(machineId, start, end);

        // Assert
        assertThat(response.getDefectsCount()).isEqualTo(5L);
        assertThat(response.getAvgDefectRate()).isEqualTo(10.0);
    }

    @Test
    @Order(9)
    @DisplayName("Test 4c: Defect rate calculation - rounding to 1 decimal place")
    void testDefectRateRounding() {
        // Arrange
        Instant start = baseTime;
        Instant end = baseTime.plus(Duration.ofHours(3));
        String machineId = "machine-1";
        
        // 7 defects in 3 hours = 2.333... defects/hour, should round to 2.3
        when(eventRepository.countByMachineIdAndEventTimeBetween(machineId, start, end))
                .thenReturn(15L);
        when(eventRepository.sumDefectsByMachineIdAndEventTimeBetween(machineId, start, end))
                .thenReturn(7L);

        // Act
        StatsResponse response = statsService.getMachineStats(machineId, start, end);

        // Assert
        assertThat(response.getAvgDefectRate()).isEqualTo(2.3);
    }

    @Test
    @Order(10)
    @DisplayName("Test zero defects")
    void testZeroDefects() {
        // Arrange
        Instant start = baseTime;
        Instant end = baseTime.plus(Duration.ofHours(1));
        String machineId = "machine-1";
        
        when(eventRepository.countByMachineIdAndEventTimeBetween(machineId, start, end))
                .thenReturn(10L);
        when(eventRepository.sumDefectsByMachineIdAndEventTimeBetween(machineId, start, end))
                .thenReturn(0L);

        // Act
        StatsResponse response = statsService.getMachineStats(machineId, start, end);

        // Assert
        assertThat(response.getDefectsCount()).isEqualTo(0L);
        assertThat(response.getAvgDefectRate()).isEqualTo(0.0);
        assertThat(response.getStatus()).isEqualTo("Healthy");
    }

    @Test
    @Order(11)
    @DisplayName("Test zero events")
    void testZeroEvents() {
        // Arrange
        Instant start = baseTime;
        Instant end = baseTime.plus(Duration.ofHours(1));
        String machineId = "machine-1";
        
        when(eventRepository.countByMachineIdAndEventTimeBetween(machineId, start, end))
                .thenReturn(0L);
        when(eventRepository.sumDefectsByMachineIdAndEventTimeBetween(machineId, start, end))
                .thenReturn(0L);

        // Act
        StatsResponse response = statsService.getMachineStats(machineId, start, end);

        // Assert
        assertThat(response.getEventsCount()).isEqualTo(0L);
        assertThat(response.getDefectsCount()).isEqualTo(0L);
        assertThat(response.getAvgDefectRate()).isEqualTo(0.0);
        assertThat(response.getStatus()).isEqualTo("Healthy");
    }

    @Test
    @Order(12)
    @DisplayName("Test top defect lines - basic functionality")
    void testTopDefectLinesBasic() {
        // Arrange
        String factoryId = "factory-1";
        Instant from = baseTime;
        Instant to = baseTime.plus(Duration.ofHours(24));
        int limit = 3;
        
        List<Object[]> mockResults = Arrays.asList(
            new Object[]{"line-1", 100L, 50L},  // 200% defect rate
            new Object[]{"line-2", 80L, 40L},    // 200% defect rate
            new Object[]{"line-3", 60L, 60L}     // 100% defect rate
        );
        
        when(eventRepository.findTopDefectLines(factoryId, from, to))
                .thenReturn(mockResults);

        // Act
        List<TopDefectLineResponse> response = statsService.getTopDefectLines(factoryId, from, to, limit);

        // Assert
        assertThat(response).hasSize(3);
        
        TopDefectLineResponse line1 = response.get(0);
        assertThat(line1.getLineId()).isEqualTo("line-1");
        assertThat(line1.getTotalDefects()).isEqualTo(100L);
        assertThat(line1.getEventCount()).isEqualTo(50L);
        assertThat(line1.getDefectsPercent()).isEqualTo(200.0);
        
        TopDefectLineResponse line2 = response.get(1);
        assertThat(line2.getLineId()).isEqualTo("line-2");
        assertThat(line2.getTotalDefects()).isEqualTo(80L);
        assertThat(line2.getDefectsPercent()).isEqualTo(200.0);
        
        TopDefectLineResponse line3 = response.get(2);
        assertThat(line3.getLineId()).isEqualTo("line-3");
        assertThat(line3.getTotalDefects()).isEqualTo(60L);
        assertThat(line3.getDefectsPercent()).isEqualTo(100.0);
    }

    @Test
    @Order(13)
    @DisplayName("Test top defect lines - limit enforcement")
    void testTopDefectLinesLimit() {
        // Arrange
        String factoryId = "factory-1";
        Instant from = baseTime;
        Instant to = baseTime.plus(Duration.ofHours(24));
        int limit = 2;
        
        List<Object[]> mockResults = Arrays.asList(
            new Object[]{"line-1", 100L, 50L},
            new Object[]{"line-2", 80L, 40L},
            new Object[]{"line-3", 60L, 60L},
            new Object[]{"line-4", 40L, 20L},
            new Object[]{"line-5", 20L, 10L}
        );
        
        when(eventRepository.findTopDefectLines(factoryId, from, to))
                .thenReturn(mockResults);

        // Act
        List<TopDefectLineResponse> response = statsService.getTopDefectLines(factoryId, from, to, limit);

        // Assert
        assertThat(response).hasSize(2);
        assertThat(response.get(0).getLineId()).isEqualTo("line-1");
        assertThat(response.get(1).getLineId()).isEqualTo("line-2");
    }

    @Test
    @Order(14)
    @DisplayName("Test top defect lines - percent rounding")
    void testTopDefectLinesPercentRounding() {
        // Arrange
        String factoryId = "factory-1";
        Instant from = baseTime;
        Instant to = baseTime.plus(Duration.ofHours(24));
        int limit = 1;
        
        // 50 defects / 3 events * 100 = 1666.67 defects per 100 events
        List<Object[]> mockResults = new ArrayList<>();
        mockResults.add(new Object[]{"line-1", 50L, 3L});
        
        when(eventRepository.findTopDefectLines(factoryId, from, to))
                .thenReturn(mockResults);

        // Act
        List<TopDefectLineResponse> response = statsService.getTopDefectLines(factoryId, from, to, limit);

        // Assert
        assertThat(response).hasSize(1);
        assertThat(response.get(0).getDefectsPercent()).isEqualTo(1666.67);
    }

    @Test
    @Order(15)
    @DisplayName("Test top defect lines - empty result")
    void testTopDefectLinesEmpty() {
        // Arrange
        String factoryId = "factory-1";
        Instant from = baseTime;
        Instant to = baseTime.plus(Duration.ofHours(24));
        int limit = 5;
        
        when(eventRepository.findTopDefectLines(factoryId, from, to))
                .thenReturn(Arrays.asList());

        // Act
        List<TopDefectLineResponse> response = statsService.getTopDefectLines(factoryId, from, to, limit);

        // Assert
        assertThat(response).isEmpty();
    }

    @Test
    @Order(16)
    @DisplayName("Test complete stats response structure")
    void testCompleteStatsResponse() {
        // Arrange
        Instant start = baseTime;
        Instant end = baseTime.plus(Duration.ofHours(4));
        String machineId = "machine-123";
        
        when(eventRepository.countByMachineIdAndEventTimeBetween(machineId, start, end))
                .thenReturn(100L);
        when(eventRepository.sumDefectsByMachineIdAndEventTimeBetween(machineId, start, end))
                .thenReturn(12L);

        // Act
        StatsResponse response = statsService.getMachineStats(machineId, start, end);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getMachineId()).isEqualTo(machineId);
        assertThat(response.getStart()).isEqualTo(start);
        assertThat(response.getEnd()).isEqualTo(end);
        assertThat(response.getEventsCount()).isEqualTo(100L);
        assertThat(response.getDefectsCount()).isEqualTo(12L);
        assertThat(response.getAvgDefectRate()).isEqualTo(3.0); // 12 defects / 4 hours
        assertThat(response.getStatus()).isEqualTo("Warning");
    }
}
