package com.trafficwarning.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AnalysisView(
        Long id,
        String deviceId,
        Long roadId,
        Integer motorCount,
        Integer nonMotorCount,
        Double avgSpeed,
        String congestionLevel,
        List<String> events,
        LocalDateTime analysisTime
) {
}
