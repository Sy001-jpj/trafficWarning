package com.trafficwarning.backend.dto;

public record EventRequest(
        String deviceId,
        String cameraName,
        Long roadId,
        String roadName,
        String eventType,
        String time,
        String snapshotUrl,
        String status,
        String analysisType,
        String congestionLevel,
        Integer vehicles,
        Double avgSpeed,
        String description
) {
}
