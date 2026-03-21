package com.halaltrader.backend.service;

import com.halaltrader.backend.dto.PortfolioSummaryDto;
import com.halaltrader.backend.dto.PositionDto;
import com.halaltrader.backend.entity.Portfolio;
import com.halaltrader.backend.entity.PortfolioPosition;
import com.halaltrader.backend.repository.PortfolioPositionRepository;
import com.halaltrader.backend.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PortfolioQueryService {

    @Value("${trading.simulation.initial-cash:100000}")
    private BigDecimal initialCash;

    private final PortfolioRepository portfolioRepository;
    private final PortfolioPositionRepository positionRepository;

    public Portfolio getPortfolio() {
        return portfolioRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No portfolio found"));
    }

    public PortfolioSummaryDto getSummary() {
        Portfolio p = getPortfolio();
        List<PortfolioPosition> positions = positionRepository.findByPortfolio(p);
        BigDecimal positionsValue = positions.stream()
                .map(pos -> pos.getQuantity().multiply(pos.getAverageBuyPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalValue = p.getCashBalance().add(positionsValue);
        BigDecimal totalPnl = totalValue.subtract(initialCash);
        BigDecimal totalPnlPct = initialCash.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : totalPnl.divide(initialCash, 4, RoundingMode.HALF_UP)
                           .multiply(new BigDecimal("100"));

        return new PortfolioSummaryDto(
                p.getId(), p.getName(), p.getCashBalance(),
                totalValue, totalPnl, totalPnlPct, positions.size());
    }

    public List<PositionDto> getPositions() {
        Portfolio p = getPortfolio();
        return positionRepository.findByPortfolio(p).stream()
                .map(pos -> new PositionDto(
                        pos.getAsset().getSymbol(),
                        pos.getAsset().getName(),
                        pos.getAsset().getAssetType().name(),
                        pos.getQuantity(),
                        pos.getAverageBuyPrice(),
                        pos.getQuantity().multiply(pos.getAverageBuyPrice())))
                .toList();
    }
}
