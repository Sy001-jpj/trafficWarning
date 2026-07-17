package com.trafficwarning.backend.dto;

import java.time.LocalDateTime;

public record DeviceView(
        String id,
        Long roadId,
        String name,
        String location,
        String rtsp,
        String videoPath,
        String creator,
        String status,
        LocalDateTime createdAt
) {
}
