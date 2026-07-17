package com.trafficwarning.backend.dto;

import java.util.List;

public record TrafficNetworkView(
        String source,
        String updatedAt,
        List<TrafficRoadView> roads
) {
}
