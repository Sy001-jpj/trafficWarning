package com.trafficwarning.backend.controller;

import com.trafficwarning.backend.common.ApiResponse;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("service", "traffic-warning-backend");
        data.put("status", "UP");
        data.put("database", checkDatabase());
        data.put("time", LocalDateTime.now());
        return ApiResponse.success(data);
    }

    private String checkDatabase() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return Integer.valueOf(1).equals(result) ? "UP" : "DOWN";
    }
}
