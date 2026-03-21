package com.halaltrader.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PerformanceDto(
        List<DailyPnlEntry> dailyPnl,
        BigDecimal winRate,
        int totalTrades,
        AssetPnlEntry bestAsset,
        AssetPnlEntry worstAsset,
        BigDecimal halalApprovalRate,
        LocalDateTime lastCycleAt,
        int approvedAssets,
        int totalAssets
) {
    public record DailyPnlEntry(String date, BigDecimal cumulativePnl) {}
    public record AssetPnlEntry(String symbol, BigDecimal totalPnl) {}
}
