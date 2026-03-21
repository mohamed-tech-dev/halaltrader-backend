package com.halaltrader.backend.dto;

import java.math.BigDecimal;

public record PositionDto(
        String symbol,
        String name,
        String assetType,
        BigDecimal quantity,
        BigDecimal avgPrice,
        BigDecimal value
) {}
