package com.trafficwarning.backend.dto;

public record DeviceRequest(
        String id,
        Long roadId,
        String name,
        String location,
        String rtsp,
        String videoPath,
        String creator,
        String status
) {
}
