package com.halaltrader.backend.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PortfolioSummaryDto(
        UUID id,
        String name,
        BigDecimal cashBalance,
        BigDecimal totalValue,
        BigDecimal totalPnl,
        BigDecimal totalPnlPct,
        int positionCount
) {}
