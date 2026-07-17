package com.trafficwarning.backend.controller;

import com.trafficwarning.backend.common.ApiResponse;
import com.trafficwarning.backend.dto.TrafficNetworkView;
import com.trafficwarning.backend.service.AmapTrafficService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/congestion")
public class CongestionController {

    private final AmapTrafficService amapTrafficService;

    public CongestionController(AmapTrafficService amapTrafficService) {
        this.amapTrafficService = amapTrafficService;
    }

    @GetMapping("/road-network")
    public ApiResponse<TrafficNetworkView> getRoadNetwork() {
        return ApiResponse.success(amapTrafficService.getRoadNetwork());
    }
}
