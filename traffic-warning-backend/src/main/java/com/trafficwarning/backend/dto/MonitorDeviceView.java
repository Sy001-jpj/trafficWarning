package com.trafficwarning.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

public record MonitorDeviceView(
        String id,
        String name,
        String location,
        String status,
        String videoPath,
        Integer motorCount,
        Integer nonMotorCount,
        Double avgSpeed,
        String congestionLevel,
        List<String> events,
        LocalDateTime lastUpdate,
        String snapshotUrl
) {
}
