package com.halaltrader.backend.service;

import com.halaltrader.backend.dto.PerformanceDto;
import com.halaltrader.backend.dto.PerformanceDto.AssetPnlEntry;
import com.halaltrader.backend.dto.PerformanceDto.DailyPnlEntry;
import com.halaltrader.backend.entity.HalalScreening;
import com.halaltrader.backend.entity.Trade;
import com.halaltrader.backend.repository.AssetRepository;
import com.halaltrader.backend.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PerformanceQueryService {

    private final PortfolioQueryService portfolioQueryService;
    private final TradeRepository tradeRepository;
    private final AssetRepository assetRepository;

    public PerformanceDto getPerformance() {
        var portfolio = portfolioQueryService.getPortfolio();
        List<Trade> allTrades = tradeRepository.findByPortfolioOrderByExecutedAtAsc(portfolio);

        // Cumulative P&L curve by day
        Map<LocalDate, BigDecimal> dailyMap = new LinkedHashMap<>();
        for (Trade t : allTrades) {
            if (t.getSimulatedPnl() != null && t.getExecutedAt() != null) {
                LocalDate date = t.getExecutedAt().toLocalDate();
                dailyMap.merge(date, t.getSimulatedPnl(), BigDecimal::add);
            }
        }
        List<DailyPnlEntry> dailyPnl = new ArrayList<>();
        BigDecimal cumulative = BigDecimal.ZERO;
        for (var entry : dailyMap.entrySet()) {
            cumulative = cumulative.add(entry.getValue());
            dailyPnl.add(new DailyPnlEntry(entry.getKey().toString(), cumulative));
        }

        // Win rate
        List<Trade> pnlTrades = allTrades.stream()
                .filter(t -> t.getSimulatedPnl() != null)
                .toList();
        long winCount = pnlTrades.stream()
                .filter(t -> t.getSimulatedPnl().compareTo(BigDecimal.ZERO) > 0)
                .count();
        BigDecimal winRate = pnlTrades.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(winCount * 100.0 / pnlTrades.size())
                            .setScale(1, RoundingMode.HALF_UP);

        // Best / worst asset
        Map<String, BigDecimal> pnlByAsset = new HashMap<>();
        for (Trade t : pnlTrades) {
            pnlByAsset.merge(t.getAsset().getSymbol(), t.getSimulatedPnl(), BigDecimal::add);
        }
        AssetPnlEntry bestAsset = pnlByAsset.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> new AssetPnlEntry(e.getKey(), e.getValue()))
                .orElse(null);
        AssetPnlEntry worstAsset = pnlByAsset.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(e -> new AssetPnlEntry(e.getKey(), e.getValue()))
                .orElse(null);

        // Halal approval rate
        var allAssets = assetRepository.findAll();
        long approvedCount = allAssets.stream()
                .filter(a -> a.getHalalScreening() == HalalScreening.APPROVED)
                .count();
        BigDecimal halalRate = allAssets.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(approvedCount * 100.0 / allAssets.size())
                            .setScale(1, RoundingMode.HALF_UP);

        // Last cycle
        LocalDateTime lastCycleAt = allTrades.stream()
                .map(Trade::getExecutedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return new PerformanceDto(
                dailyPnl, winRate, allTrades.size(),
                bestAsset, worstAsset, halalRate, lastCycleAt,
                (int) approvedCount, allAssets.size());
    }
}
