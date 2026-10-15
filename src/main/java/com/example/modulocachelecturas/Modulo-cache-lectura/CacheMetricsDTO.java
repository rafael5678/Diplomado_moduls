package com.example.modulocachelecturas;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CacheMetricsDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private long totalRequests;
    private long cacheHits;
    private long cacheMisses;
    private double hitRatioPercentage;
    private double dbReadsSavedPercentage; // Target > 85-90%
    private long cacheInvalidations;
    private long mutexLockAcquisitions;
    private long mutexLockWaits;
    private boolean asr1PerformanceTargetMet; // p95 response < 1000ms
}
