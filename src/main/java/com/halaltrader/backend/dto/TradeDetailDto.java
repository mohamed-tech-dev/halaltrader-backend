package com.halaltrader.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TradeDetailDto(
        UUID id,
        String symbol,
        String action,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal totalAmount,
        BigDecimal simulatedPnl,
        LocalDateTime executedAt,
        String aiReasoning,
        String technicalData
) {}
