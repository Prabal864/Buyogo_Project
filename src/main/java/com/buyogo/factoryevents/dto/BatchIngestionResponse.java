package com.buyogo.factoryevents.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchIngestionResponse {
    private int accepted;
    private int deduped;
    private int updated;
    private int rejected;
    private List<RejectionDetail> rejections;
}
