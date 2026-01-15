package com.buyogo.factoryevents.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Event {
    @Id
    @Column(name = "event_id")
    private String eventId;
    
    @Column(name = "event_time", nullable = false)
    private Instant eventTime;
    
    @Column(name = "received_time", nullable = false)
    private Instant receivedTime;
    
    @Column(name = "machine_id", nullable = false)
    private String machineId;
    
    @Column(name = "line_id")
    private String lineId;
    
    @Column(name = "factory_id")
    private String factoryId;
    
    @Column(name = "duration_ms", nullable = false)
    private Long durationMs;
    
    @Column(name = "defect_count", nullable = false)
    private Integer defectCount;
    
    @Column(name = "payload_hash", nullable = false)
    private String payloadHash;
    
    @Column(name = "created_at")
    private Instant createdAt;
    
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
