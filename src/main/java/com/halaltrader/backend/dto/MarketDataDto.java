package com.halaltrader.backend.dto;

import java.util.List;

public record MarketDataDto(
        String symbol,
        double price,
        double changePct,
        long volume,
        double rsi,
        double macd,
        double macdSignal,
        double ma20,
        double ma50,
        List<String> newsTitles
) {}
