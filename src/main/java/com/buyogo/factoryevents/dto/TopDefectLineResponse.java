package com.buyogo.factoryevents.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopDefectLineResponse {
    private String lineId;
    private long totalDefects;
    private long eventCount;
    private double defectsPercent;
}
