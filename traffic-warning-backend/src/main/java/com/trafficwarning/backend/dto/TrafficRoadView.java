package com.trafficwarning.backend.dto;

import java.util.List;

public record TrafficRoadView(
        String name,
        String level,
        Integer value,
        String tone,
        List<TrafficPointView> points
) {
}
