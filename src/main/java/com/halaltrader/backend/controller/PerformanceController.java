package com.halaltrader.backend.controller;

import com.halaltrader.backend.dto.PerformanceDto;
import com.halaltrader.backend.service.PerformanceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/performance")
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceQueryService performanceQueryService;

    @GetMapping
    public PerformanceDto getPerformance() {
        return performanceQueryService.getPerformance();
    }
}
