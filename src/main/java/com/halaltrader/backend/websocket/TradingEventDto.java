package com.halaltrader.backend.websocket;

import java.math.BigDecimal;
import java.time.Instant;

public record TradingEventDto(
        String type,
        String symbol,
        String action,
        BigDecimal quantity,
        BigDecimal price,
        String agentSummary,
        Integer totalDecisions,
        Instant timestamp
) {}
