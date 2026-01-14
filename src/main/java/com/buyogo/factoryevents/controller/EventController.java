package com.buyogo.factoryevents.controller;

import com.buyogo.factoryevents.dto.BatchIngestionResponse;
import com.buyogo.factoryevents.dto.EventRequest;
import com.buyogo.factoryevents.service.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
@Slf4j
public class EventController {
    
    private final EventService eventService;
    
    @PostMapping("/batch")
    public ResponseEntity<BatchIngestionResponse> ingestBatch(@RequestBody List<EventRequest> events) {
        log.info("Received batch ingestion request with {} events", events.size());
        BatchIngestionResponse response = eventService.processBatch(events);
        log.info("Batch processing complete: accepted={}, deduped={}, updated={}, rejected={}", 
            response.getAccepted(), response.getDeduped(), response.getUpdated(), response.getRejected());
        return ResponseEntity.ok(response);
    }
}
