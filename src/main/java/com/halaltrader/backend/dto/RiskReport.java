package com.halaltrader.backend.dto;

import java.math.BigDecimal;

public record RiskReport(String level, BigDecimal maxQuantity, String reason) {}
