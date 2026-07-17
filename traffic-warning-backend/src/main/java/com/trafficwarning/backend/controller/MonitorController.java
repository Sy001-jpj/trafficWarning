package com.trafficwarning.backend.controller;

import com.trafficwarning.backend.common.ApiResponse;
import com.trafficwarning.backend.dto.AnalysisView;
import com.trafficwarning.backend.dto.MonitorDeviceView;
import com.trafficwarning.backend.repository.AnalysisRepository;
import com.trafficwarning.backend.service.AnalysisService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    private final AnalysisRepository analysisRepository;
    private final AnalysisService analysisService;

    public MonitorController(AnalysisRepository analysisRepository, AnalysisService analysisService) {
        this.analysisRepository = analysisRepository;
        this.analysisService = analysisService;
    }

    @GetMapping("/devices")
    public ApiResponse<List<MonitorDeviceView>> listMonitorDevices() {
        return ApiResponse.success(analysisRepository.findMonitorDevices());
    }

    @GetMapping("/devices/{deviceId}/latest")
    public ApiResponse<AnalysisView> latestByDevice(@PathVariable String deviceId) {
        return analysisRepository.findLatestByDeviceId(deviceId)
                .stream()
                .findFirst()
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.fail(404, "未找到该设备的分析结果"));
    }

    /**
     * Get historical analysis records for a device.
     * @param minutes time window in minutes (default 60)
     */
    @GetMapping("/devices/{deviceId}/history")
    public ApiResponse<List<AnalysisView>> deviceHistory(
            @PathVariable String deviceId,
            @RequestParam(defaultValue = "60") int minutes
    ) {
        int window = Math.min(minutes, 1440); // max 24 hours
        return ApiResponse.success(analysisRepository.findHistory(deviceId, window));
    }

    /**
     * Manually trigger analysis for a single device.
     */
    @PostMapping("/devices/{deviceId}/analyze")
    public ApiResponse<MonitorDeviceView> triggerAnalyze(@PathVariable String deviceId) {
        try {
            MonitorDeviceView result = analysisService.analyzeDevice(deviceId);
            return ApiResponse.success(result);
        } catch (RuntimeException e) {
            return ApiResponse.fail(500, "分析失败: " + e.getMessage());
        }
    }

    @GetMapping("/realtime")
    public ApiResponse<Map<String, Object>> realtime() {
        List<MonitorDeviceView> devices = analysisRepository.findMonitorDevices();
        return ApiResponse.success(Map.of(
                "devices", devices,
                "total", devices.size()
        ));
    }
}
