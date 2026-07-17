package com.trafficwarning.backend.controller;

import com.trafficwarning.backend.common.ApiResponse;
import com.trafficwarning.backend.dto.DeviceRequest;
import com.trafficwarning.backend.dto.DeviceStatusRequest;
import com.trafficwarning.backend.dto.DeviceView;
import com.trafficwarning.backend.repository.DeviceRepository;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private static final String DEFAULT_CREATOR = "\u7cfb\u7edf\u7ba1\u7406\u5458";
    private static final String DEFAULT_STATUS = "\u79bb\u7ebf";

    private final DeviceRepository deviceRepository;

    public DeviceController(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @GetMapping
    public ApiResponse<List<DeviceView>> listDevices() {
        return ApiResponse.success(deviceRepository.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<DeviceView> getDevice(@PathVariable String id) {
        return deviceRepository.findById(id)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.fail(404, "\u8bbe\u5907\u4e0d\u5b58\u5728"));
    }

    @PostMapping
    public ApiResponse<DeviceView> createDevice(@RequestBody DeviceRequest request) {
        String id = normalize(request.id());
        String name = normalize(request.name());
        String location = normalize(request.location());
        String rtsp = normalize(request.rtsp());
        if (id.isBlank() || name.isBlank() || location.isBlank()) {
            return ApiResponse.fail(400, "\u8bbe\u5907\u7f16\u53f7\u3001\u8bbe\u5907\u540d\u79f0\u3001\u5b89\u88c5\u4f4d\u7f6e\u5747\u4e3a\u5fc5\u586b\u9879");
        }
        if (deviceRepository.existsById(id)) {
            return ApiResponse.fail(409, "\u8be5\u8bbe\u5907\u7f16\u53f7\u5df2\u5b58\u5728");
        }

        DeviceView device = deviceRepository.create(
                id,
                request.roadId(),
                name,
                location,
                rtsp,
                normalize(request.videoPath()),
                defaultIfBlank(request.creator(), DEFAULT_CREATOR),
                defaultIfBlank(request.status(), DEFAULT_STATUS)
        );
        return ApiResponse.success(device);
    }

    @PutMapping("/{id}")
    public ApiResponse<DeviceView> updateDevice(@PathVariable String id, @RequestBody DeviceRequest request) {
        String name = normalize(request.name());
        String location = normalize(request.location());
        String rtsp = normalize(request.rtsp());
        if (name.isBlank() || location.isBlank()) {
            return ApiResponse.fail(400, "\u8bbe\u5907\u540d\u79f0\u3001\u5b89\u88c5\u4f4d\u7f6e\u5747\u4e3a\u5fc5\u586b\u9879");
        }

        return deviceRepository.update(
                id,
                request.roadId(),
                name,
                location,
                rtsp,
                normalize(request.videoPath()),
                defaultIfBlank(request.creator(), DEFAULT_CREATOR),
                defaultIfBlank(request.status(), DEFAULT_STATUS)
        ).map(ApiResponse::success).orElseGet(() -> ApiResponse.fail(404, "\u8bbe\u5907\u4e0d\u5b58\u5728"));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<DeviceView> updateStatus(@PathVariable String id, @RequestBody DeviceStatusRequest request) {
        String status = defaultIfBlank(request.status(), DEFAULT_STATUS);
        return deviceRepository.updateStatus(id, status)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.fail(404, "\u8bbe\u5907\u4e0d\u5b58\u5728"));
    }

    @PostMapping("/check-status")
    public ApiResponse<List<DeviceView>> checkStatus() {
        return ApiResponse.success(deviceRepository.checkStatus());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> deleteDevice(@PathVariable String id) {
        boolean deleted = deviceRepository.deleteById(id);
        return deleted
                ? ApiResponse.success(true)
                : ApiResponse.fail(404, "\u8bbe\u5907\u4e0d\u5b58\u5728");
    }

    private String defaultIfBlank(String value, String fallback) {
        String normalized = normalize(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
