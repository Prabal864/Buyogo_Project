package com.buyogo.factoryevents.service;

import com.buyogo.factoryevents.dto.StatsResponse;
import com.buyogo.factoryevents.dto.TopDefectLineResponse;
import com.buyogo.factoryevents.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatsService {
    
    private static final double HEALTHY_THRESHOLD = 2.0;
    
    private final EventRepository eventRepository;
    
    public StatsResponse getMachineStats(String machineId, Instant start, Instant end) {
        log.debug("Getting stats for machine {} from {} to {}", machineId, start, end);
        
        long eventsCount = eventRepository.countByMachineIdAndEventTimeBetween(machineId, start, end);
        long defectsCount = eventRepository.sumDefectsByMachineIdAndEventTimeBetween(machineId, start, end);
        
        // Calculate window duration in hours
        double windowHours = Duration.between(start, end).getSeconds() / 3600.0;
        
        // Calculate average defect rate (defects per hour)
        double avgDefectRate = windowHours > 0 ? defectsCount / windowHours : 0.0;
        
        // Determine status
        String status = avgDefectRate < HEALTHY_THRESHOLD ? "Healthy" : "Warning";
        
        return StatsResponse.builder()
            .machineId(machineId)
            .start(start)
            .end(end)
            .eventsCount(eventsCount)
            .defectsCount(defectsCount)
            .avgDefectRate(Math.round(avgDefectRate * 10.0) / 10.0) // Round to 1 decimal
            .status(status)
            .build();
    }
    
    public List<TopDefectLineResponse> getTopDefectLines(String factoryId, Instant from, Instant to, int limit) {
        log.debug("Getting top {} defect lines for factory {} from {} to {}", limit, factoryId, from, to);
        
        List<Object[]> results = eventRepository.findTopDefectLines(factoryId, from, to);
        
        return results.stream()
            .limit(limit)
            .map(row -> {
                String lineId = (String) row[0];
                Long totalDefects = (Long) row[1];
                Long eventCount = (Long) row[2];
                double defectsPercent = eventCount > 0 ? (totalDefects * 100.0) / eventCount : 0.0;
                
                return TopDefectLineResponse.builder()
                    .lineId(lineId)
                    .totalDefects(totalDefects)
                    .eventCount(eventCount)
                    .defectsPercent(Math.round(defectsPercent * 100.0) / 100.0) // Round to 2 decimals
                    .build();
            })
            .collect(Collectors.toList());
    }
}
