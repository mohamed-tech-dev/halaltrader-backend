package com.halaltrader.backend.dto;

import com.halaltrader.backend.entity.TradeAction;
import java.math.BigDecimal;

public record TradeDecision(TradeAction action, BigDecimal quantity, String reasoning) {}
