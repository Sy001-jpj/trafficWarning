package com.trafficwarning.backend.controller;

import com.trafficwarning.backend.common.ApiResponse;
import com.trafficwarning.backend.dto.EventAnalysisStatsView;
import com.trafficwarning.backend.dto.EventAnalysisTypeRequest;
import com.trafficwarning.backend.dto.EventRequest;
import com.trafficwarning.backend.dto.EventStatusRequest;
import com.trafficwarning.backend.dto.EventView;
import com.trafficwarning.backend.repository.EventRepository;
import com.trafficwarning.backend.util.DateTimeParser;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private static final String DEFAULT_STATUS = "\u672a\u5904\u7406";
    private static final String DEFAULT_ANALYSIS_TYPE = "AI分析";
    private static final String MANUAL_ANALYSIS_TYPE = "人工分析";

    private final EventRepository eventRepository;

    public EventController(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @GetMapping
    public ApiResponse<List<EventView>> listEvents() {
        return ApiResponse.success(eventRepository.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<EventView> getEvent(@PathVariable Long id) {
        return eventRepository.findById(id)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.fail(404, "\u4ea4\u901a\u4e8b\u4ef6\u4e0d\u5b58\u5728"));
    }

    @GetMapping("/analysis-stats")
    public ApiResponse<EventAnalysisStatsView> getAnalysisStats() {
        return ApiResponse.success(eventRepository.getAnalysisStats());
    }

    @PostMapping
    public ApiResponse<EventView> createEvent(@RequestBody EventRequest request) {
        String cameraName = normalize(request.cameraName());
        String roadName = normalize(request.roadName());
        String eventType = normalize(request.eventType());
        if (cameraName.isBlank() || roadName.isBlank() || eventType.isBlank()) {
            return ApiResponse.fail(400, "\u76d1\u63a7\u70b9\u3001\u9053\u8def\u540d\u79f0\u3001\u4e8b\u4ef6\u7c7b\u578b\u5747\u4e3a\u5fc5\u586b\u9879");
        }

        EventView event = eventRepository.create(
                defaultNull(request.deviceId()),
                cameraName,
                request.roadId(),
                roadName,
                eventType,
                DateTimeParser.parseOrNow(request.time()),
                defaultNull(request.snapshotUrl()),
                defaultIfBlank(request.status(), DEFAULT_STATUS),
                normalizeAnalysisType(request.analysisType()),
                defaultNull(request.congestionLevel()),
                request.vehicles() == null ? 0 : request.vehicles(),
                request.avgSpeed(),
                defaultNull(request.description())
        );
        return ApiResponse.success(event);
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<EventView> updateStatus(@PathVariable Long id, @RequestBody EventStatusRequest request) {
        String status = defaultIfBlank(request.status(), DEFAULT_STATUS);
        return eventRepository.updateStatus(id, status)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.fail(404, "\u4ea4\u901a\u4e8b\u4ef6\u4e0d\u5b58\u5728"));
    }

    @PatchMapping("/{id}/analysis-type")
    public ApiResponse<EventView> updateAnalysisType(@PathVariable Long id, @RequestBody EventAnalysisTypeRequest request) {
        String analysisType = normalizeAnalysisType(request.analysisType());
        return eventRepository.updateAnalysisType(id, analysisType)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.fail(404, "\u4ea4\u901a\u4e8b\u4ef6\u4e0d\u5b58\u5728"));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> deleteEvent(@PathVariable Long id) {
        boolean deleted = eventRepository.deleteById(id);
        return deleted
                ? ApiResponse.success(true)
                : ApiResponse.fail(404, "\u4ea4\u901a\u4e8b\u4ef6\u4e0d\u5b58\u5728");
    }

    private String defaultIfBlank(String value, String fallback) {
        String normalized = normalize(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private String defaultNull(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeAnalysisType(String value) {
        String normalized = normalize(value);
        return MANUAL_ANALYSIS_TYPE.equals(normalized) ? MANUAL_ANALYSIS_TYPE : DEFAULT_ANALYSIS_TYPE;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
