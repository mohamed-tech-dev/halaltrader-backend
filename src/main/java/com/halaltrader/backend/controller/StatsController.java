package com.halaltrader.backend.controller;

import com.halaltrader.backend.dto.StatsDto;
import com.halaltrader.backend.service.StatsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsQueryService statsQueryService;

    @GetMapping
    public StatsDto getStats() {
        return statsQueryService.getStats();
    }
}
