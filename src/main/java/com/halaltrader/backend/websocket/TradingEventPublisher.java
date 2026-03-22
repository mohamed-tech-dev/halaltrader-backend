package com.halaltrader.backend.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TradingEventPublisher {

    private final SimpMessagingTemplate messaging;

    public void publishTradeExecuted(String symbol, String action,
                                     BigDecimal quantity, BigDecimal price,
                                     String agentSummary) {
        messaging.convertAndSend("/topic/trading-events",
                new TradingEventDto("TRADE_EXECUTED", symbol, action,
                        quantity, price, agentSummary, null, Instant.now()));
    }

    public void publishCycleComplete(int totalDecisions) {
        messaging.convertAndSend("/topic/trading-events",
                new TradingEventDto("CYCLE_COMPLETE", null, null,
                        null, null, null, totalDecisions, Instant.now()));
    }
}
