package com.halaltrader.backend.controller;

import com.halaltrader.backend.dto.PortfolioSummaryDto;
import com.halaltrader.backend.dto.PositionDto;
import com.halaltrader.backend.service.PortfolioQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioQueryService portfolioQueryService;

    @GetMapping
    public PortfolioSummaryDto getSummary() {
        return portfolioQueryService.getSummary();
    }

    @GetMapping("/positions")
    public List<PositionDto> getPositions() {
        return portfolioQueryService.getPositions();
    }
}
