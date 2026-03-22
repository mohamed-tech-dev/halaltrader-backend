package com.halaltrader.backend.service;

import com.halaltrader.backend.dto.StatsDto;
import com.halaltrader.backend.entity.Trade;
import com.halaltrader.backend.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsQueryService {

    @Value("${trading.simulation.initial-cash:100000}")
    private BigDecimal initialCash;

    private final PortfolioQueryService portfolioQueryService;
    private final TradeRepository tradeRepository;

    @Transactional(readOnly = true)
    public StatsDto getStats() {
        var portfolio = portfolioQueryService.getPortfolio();
        List<Trade> trades = tradeRepository.findByPortfolioOrderByExecutedAtAsc(portfolio);

        List<Trade> pnlTrades = trades.stream()
                .filter(t -> t.getSimulatedPnl() != null && t.getExecutedAt() != null)
                .toList();

        if (pnlTrades.isEmpty()) {
            return new StatsDto(
                    Collections.emptyList(),
                    new StatsDto.Records(null, null, 0, 0, null),
                    Collections.emptyList());
        }

        // ── Monthly performance ──────────────────────────────────────────────
        Map<YearMonth, List<Trade>> byMonth = pnlTrades.stream()
                .collect(Collectors.groupingBy(
                        t -> YearMonth.from(t.getExecutedAt()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<StatsDto.MonthlyEntry> monthlyPerf = new ArrayList<>();
        BigDecimal cumPnl = BigDecimal.ZERO;
        for (var entry : byMonth.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            BigDecimal monthPnl = entry.getValue().stream()
                    .map(Trade::getSimulatedPnl)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal base = initialCash.add(cumPnl);
            BigDecimal pctChange = base.compareTo(BigDecimal.ZERO) <= 0
                    ? BigDecimal.ZERO
                    : monthPnl.divide(base, 4, RoundingMode.HALF_UP)
                               .multiply(new BigDecimal("100"))
                               .setScale(2, RoundingMode.HALF_UP);
            monthlyPerf.add(new StatsDto.MonthlyEntry(
                    entry.getKey().toString(), monthPnl, pctChange, entry.getValue().size()));
            cumPnl = cumPnl.add(monthPnl);
        }

        // ── Records ─────────────────────────────────────────────────────────
        Trade best = pnlTrades.stream()
                .max(Comparator.comparing(Trade::getSimulatedPnl)).orElse(null);
        Trade worst = pnlTrades.stream()
                .min(Comparator.comparing(Trade::getSimulatedPnl)).orElse(null);

        int maxWin = 0, curWin = 0, maxLoss = 0, curLoss = 0;
        for (Trade t : pnlTrades) {
            if (t.getSimulatedPnl().compareTo(BigDecimal.ZERO) > 0) {
                curWin++; curLoss = 0;
                maxWin = Math.max(maxWin, curWin);
            } else {
                curLoss++; curWin = 0;
                maxLoss = Math.max(maxLoss, curLoss);
            }
        }

        Map<LocalDate, BigDecimal> byDay = new LinkedHashMap<>();
        pnlTrades.forEach(t -> byDay.merge(
                t.getExecutedAt().toLocalDate(), t.getSimulatedPnl(), BigDecimal::add));
        StatsDto.DayRecord bestDay = byDay.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> new StatsDto.DayRecord(e.getKey().toString(), e.getValue()))
                .orElse(null);

        StatsDto.TradeRecord bestRecord = best == null ? null
                : new StatsDto.TradeRecord(best.getAsset().getSymbol(),
                        best.getAction().name(), best.getSimulatedPnl(), best.getExecutedAt());
        StatsDto.TradeRecord worstRecord = worst == null ? null
                : new StatsDto.TradeRecord(worst.getAsset().getSymbol(),
                        worst.getAction().name(), worst.getSimulatedPnl(), worst.getExecutedAt());

        // ── Per-asset P&L ────────────────────────────────────────────────────
        Map<String, BigDecimal> pnlByAsset = new LinkedHashMap<>();
        pnlTrades.forEach(t -> pnlByAsset.merge(
                t.getAsset().getSymbol(), t.getSimulatedPnl(), BigDecimal::add));
        List<StatsDto.AssetPnlEntry> perAssetPnl = pnlByAsset.entrySet().stream()
                .map(e -> new StatsDto.AssetPnlEntry(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(StatsDto.AssetPnlEntry::totalPnl).reversed())
                .toList();

        return new StatsDto(
                monthlyPerf,
                new StatsDto.Records(bestRecord, worstRecord, maxWin, maxLoss, bestDay),
                perAssetPnl);
    }
}
