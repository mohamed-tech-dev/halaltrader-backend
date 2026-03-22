package com.halaltrader.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record StatsDto(
        List<MonthlyEntry> monthlyPerformance,
        Records records,
        List<AssetPnlEntry> perAssetPnl
) {
    public record MonthlyEntry(
            String month,       // "2026-01" format
            BigDecimal pnl,
            BigDecimal pctChange,
            int tradeCount
    ) {}

    public record Records(
            TradeRecord bestTrade,
            TradeRecord worstTrade,
            int maxWinStreak,
            int maxLossStreak,
            DayRecord bestDay
    ) {}

    public record TradeRecord(
            String symbol,
            String action,
            BigDecimal pnl,
            LocalDateTime executedAt
    ) {}

    public record DayRecord(
            String date,
            BigDecimal pnl
    ) {}

    public record AssetPnlEntry(
            String symbol,
            BigDecimal totalPnl
    ) {}
}
