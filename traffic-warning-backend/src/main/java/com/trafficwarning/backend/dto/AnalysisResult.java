package com.trafficwarning.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response DTO from Python AI model service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalysisResult(
        @JsonProperty("device_id") String deviceId,
        @JsonProperty("motor_count") int motorCount,
        @JsonProperty("non_motor_count") int nonMotorCount,
        @JsonProperty("avg_speed") double avgSpeed,
        @JsonProperty("congestion_level") String congestionLevel,
        @JsonProperty("congestion_label") String congestionLabel,
        List<String> events,
        @JsonProperty("snapshot_path") String snapshotPath,
        @JsonProperty("analyzed_at") String analyzedAt
) {
}
