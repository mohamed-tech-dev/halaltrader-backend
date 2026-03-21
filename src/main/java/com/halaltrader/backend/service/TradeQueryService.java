package com.halaltrader.backend.service;

import com.halaltrader.backend.dto.TradeDetailDto;
import com.halaltrader.backend.dto.TradeDto;
import com.halaltrader.backend.entity.Trade;
import com.halaltrader.backend.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TradeQueryService {

    private final TradeRepository tradeRepository;
    private final PortfolioQueryService portfolioQueryService;

    public Page<TradeDto> list(int page, int size) {
        var portfolio = portfolioQueryService.getPortfolio();
        int clampedSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, clampedSize, Sort.by("executedAt").descending());
        return tradeRepository.findByPortfolio(portfolio, pageable)
                .map(this::toDto);
    }

    public TradeDetailDto getById(UUID id) {
        Trade t = tradeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trade not found"));
        var portfolio = portfolioQueryService.getPortfolio();
        if (!t.getPortfolio().getId().equals(portfolio.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trade not found");
        }
        return new TradeDetailDto(
                t.getId(), t.getAsset().getSymbol(), t.getAction().name(),
                t.getQuantity(), t.getPrice(), t.getTotalAmount(),
                t.getSimulatedPnl(), t.getExecutedAt(),
                t.getAiReasoning(), t.getTechnicalData());
    }

    private TradeDto toDto(Trade t) {
        return new TradeDto(
                t.getId(), t.getAsset().getSymbol(), t.getAction().name(),
                t.getQuantity(), t.getPrice(), t.getTotalAmount(),
                t.getSimulatedPnl(), t.getExecutedAt());
    }
}
