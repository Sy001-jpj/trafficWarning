package com.trafficwarning.backend.dto;

import java.time.LocalDateTime;

public record EventView(
        Long id,
        String deviceId,
        String cameraName,
        Long roadId,
        String roadName,
        String eventType,
        LocalDateTime time,
        String snapshotUrl,
        String status,
        String analysisType,
        String congestionLevel,
        Integer vehicles,
        Double avgSpeed,
        String description
) {
}
