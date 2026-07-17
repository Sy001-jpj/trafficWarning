package com.trafficwarning.backend.controller;

import com.trafficwarning.backend.common.ApiResponse;
import com.trafficwarning.backend.dto.AnalysisView;
import com.trafficwarning.backend.repository.AnalysisRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisRepository analysisRepository;

    public AnalysisController(AnalysisRepository analysisRepository) {
        this.analysisRepository = analysisRepository;
    }

    @GetMapping
    public ApiResponse<List<AnalysisView>> listAnalysis() {
        return ApiResponse.success(analysisRepository.findAll());
    }
}
