package com.buyogo.factoryevents.controller;

import com.buyogo.factoryevents.dto.StatsResponse;
import com.buyogo.factoryevents.dto.TopDefectLineResponse;
import com.buyogo.factoryevents.service.StatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
@Slf4j
public class StatsController {
    
    private final StatsService statsService;
    
    @GetMapping
    public ResponseEntity<StatsResponse> getStats(
            @RequestParam String machineId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
        
        log.info("Getting stats for machine {} from {} to {}", machineId, start, end);
        StatsResponse stats = statsService.getMachineStats(machineId, start, end);
        return ResponseEntity.ok(stats);
    }
    
    @GetMapping("/top-defect-lines")
    public ResponseEntity<List<TopDefectLineResponse>> getTopDefectLines(
            @RequestParam String factoryId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "10") int limit) {
        
        log.info("Getting top {} defect lines for factory {} from {} to {}", limit, factoryId, from, to);
        List<TopDefectLineResponse> topLines = statsService.getTopDefectLines(factoryId, from, to, limit);
        return ResponseEntity.ok(topLines);
    }
}
