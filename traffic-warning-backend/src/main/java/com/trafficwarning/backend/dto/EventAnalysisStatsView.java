package com.trafficwarning.backend.dto;

public record EventAnalysisStatsView(
        Long aiCount,
        Long manualCount,
        Long total
) {
}
