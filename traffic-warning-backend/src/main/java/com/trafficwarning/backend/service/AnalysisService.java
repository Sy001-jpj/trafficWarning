package com.trafficwarning.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trafficwarning.backend.dto.AnalysisResult;
import com.trafficwarning.backend.dto.DeviceView;
import com.trafficwarning.backend.dto.EventView;
import com.trafficwarning.backend.dto.MonitorDeviceView;
import com.trafficwarning.backend.repository.AnalysisRepository;
import com.trafficwarning.backend.repository.DeviceRepository;
import com.trafficwarning.backend.repository.EventRepository;
import com.trafficwarning.backend.websocket.MonitorWebSocketHandler;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Core service that orchestrates the analysis pipeline:
 * device list → Python AI → save result → generate events → WebSocket push.
 */
@Service
@EnableScheduling
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;
    private final DeviceRepository deviceRepository;
    private final AnalysisRepository analysisRepository;
    private final EventRepository eventRepository;
    private final MonitorWebSocketHandler webSocketHandler;

    @Value("${traffic-warning.ai-service-url:http://localhost:8000}")
    private String aiServiceUrl;

    public AnalysisService(
            ObjectMapper objectMapper,
            DeviceRepository deviceRepository,
            AnalysisRepository analysisRepository,
            EventRepository eventRepository,
            MonitorWebSocketHandler webSocketHandler
    ) {
        this.objectMapper = objectMapper;
        this.deviceRepository = deviceRepository;
        this.analysisRepository = analysisRepository;
        this.eventRepository = eventRepository;
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * Scheduled task: analyze all online devices every 60 seconds.
     */
    @Scheduled(fixedDelay = 15_000)
    public void scheduledAnalysis() {
        log.info("Starting scheduled analysis round...");
        List<DeviceView> devices = deviceRepository.findAll();
        int analyzedCount = 0;
        int skippedOffline = 0;
        int skippedNoVideo = 0;
        for (DeviceView device : devices) {
            if (!"在线".equals(device.status())) {
                skippedOffline++;
                continue;
            }
            if (device.videoPath() == null || device.videoPath().isBlank()) {
                log.debug("Skipping device {}: no video file configured", device.id());
                skippedNoVideo++;
                continue;
            }
            try {
                analyzeDevice(device.id());
                analyzedCount++;
            } catch (Exception e) {
                log.warn("Analysis failed for device {}: {}", device.id(), e.getMessage());
            }
        }
        log.info("Scheduled analysis complete: {} analyzed, {} skipped ({} offline, {} no video)",
                analyzedCount, skippedOffline + skippedNoVideo, skippedOffline, skippedNoVideo);
    }

    /**
     * Run analysis for a single device and return the updated MonitorDeviceView.
     */
    public MonitorDeviceView analyzeDevice(String deviceId) {
        DeviceView device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new RuntimeException("Device not found: " + deviceId));

        // 1. Call Python AI service — always use real video frame analysis
        AnalysisResult result = callAiVideoService(deviceId, device.videoPath());

        // 2. Save analysis to database
        String eventsJson = serializeEvents(result.events());
        analysisRepository.save(
                deviceId,
                device.roadId(),
                result.motorCount(),
                result.nonMotorCount(),
                result.avgSpeed(),
                result.congestionLevel(),
                eventsJson,
                result.snapshotPath()
        );

        // 3. Auto-generate traffic events if needed
        autoGenerateEvents(device, result);

        // 4. Update device status to online if it was offline
        if (!"在线".equals(device.status())) {
            deviceRepository.updateStatus(deviceId, "在线");
        }

        // 5. Build updated MonitorDeviceView and broadcast
        MonitorDeviceView updated = new MonitorDeviceView(
                device.id(),
                device.name(),
                device.location(),
                "在线",
                device.videoPath(),
                result.motorCount(),
                result.nonMotorCount(),
                result.avgSpeed(),
                result.congestionLevel(),
                result.events(),
                LocalDateTime.now(),
                result.snapshotPath()
        );

        webSocketHandler.broadcast(updated);
        return updated;
    }

    /**
     * Call Python AI model service /model/analyze-video for video-based devices.
     * Extracts a frame from the video file and runs YOLO detection on it.
     */
    private AnalysisResult callAiVideoService(String deviceId, String videoPath) {
        String url = aiServiceUrl + "/model/analyze-video";
        Map<String, Object> body = new HashMap<>();
        body.put("device_id", deviceId);
        body.put("image_url", videoPath);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            AnalysisResult result = restTemplate.postForObject(url, request, AnalysisResult.class);
            if (result == null) {
                throw new RuntimeException("Empty response from AI video service");
            }
            log.info("Video analysis complete for device {} (video: {})", deviceId, videoPath);
            return result;
        } catch (Exception e) {
            log.error("Failed to call AI video service at {}: {}", url, e.getMessage());
            throw new RuntimeException("AI video service call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Generate traffic events based on analysis results, with deduplication.
     */
    private void autoGenerateEvents(DeviceView device, AnalysisResult result) {
        if (result.events() == null || result.events().isEmpty()) {
            return;
        }

        for (String eventType : result.events()) {
            // Skip non-actionable events
            if ("未检测到明显异常行为".equals(eventType)) {
                continue;
            }

            // Dedup: same device + same event type within 5 minutes
            if (eventRepository.existsRecentDuplicate(device.id(), eventType, 5)) {
                log.debug("Skipping duplicate event: device={}, type={}", device.id(), eventType);
                continue;
            }

            try {
                EventView event = eventRepository.create(
                        device.id(),
                        device.name(),
                        device.roadId(),
                        device.location(),
                        eventType,
                        LocalDateTime.now(),
                        result.snapshotPath(),
                        "未处理",
                        "AI分析",
                        result.congestionLevel(),
                        result.motorCount() + result.nonMotorCount(),
                        result.avgSpeed(),
                        "AI自动检测生成: " + eventType + ", 拥堵等级 " + result.congestionLevel()
                );
                log.info("Auto-generated event #{}: {} at {}", event.id(), eventType, device.name());
            } catch (Exception e) {
                log.error("Failed to create event for device {}: {}", device.id(), e.getMessage());
            }
        }
    }

    private String serializeEvents(List<String> events) {
        try {
            return objectMapper.writeValueAsString(events);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
