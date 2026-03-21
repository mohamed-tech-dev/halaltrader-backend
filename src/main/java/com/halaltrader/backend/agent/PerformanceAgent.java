package com.halaltrader.backend.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.halaltrader.backend.client.AnthropicClient;
import com.halaltrader.backend.config.AnthropicProperties;
import com.halaltrader.backend.entity.TradeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PerformanceAgent {

    private static final Logger log = LoggerFactory.getLogger(PerformanceAgent.class);
    private static final int MAX_TOKENS = 300;
    private static final String SYSTEM_PROMPT = """
            You are a trading performance analyst. Your sole task is to calculate trade metrics.
            Return ONLY a JSON object — no markdown:
            {"pnl": <number>, "pnl_pct": <number>, "comment": "<brief comment>"}
            Rules:
            - For BUY trades: pnl=0, pnl_pct=0 (unrealized).
            - For SELL trades: pnl = (price - avg_buy_price) * quantity, pnl_pct = ((price - avg_buy_price) / avg_buy_price) * 100.
            - For HOLD: pnl=0, pnl_pct=0.
            """;

    private final AnthropicClient anthropicClient;
    private final AnthropicProperties props;
    private final ObjectMapper objectMapper;

    public PerformanceAgent(AnthropicClient anthropicClient, AnthropicProperties props, ObjectMapper objectMapper) {
        this.anthropicClient = anthropicClient;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public BigDecimal compute(TradeAction action, BigDecimal quantity, BigDecimal price, BigDecimal avgBuyPrice) {
        String userMessage = String.format(
                "Action: %s | Quantity: %s | Price: %s | Avg buy price: %s",
                action, quantity, price, avgBuyPrice
        );
        try {
            String model = props.model("performance");
            String response = anthropicClient.call(model, SYSTEM_PROMPT, userMessage, MAX_TOKENS);
            JsonNode node = objectMapper.readTree(response);
            BigDecimal pnl = new BigDecimal(node.path("pnl").asText("0"));
            double pnlPct = node.path("pnl_pct").asDouble(0);
            log.info("[PerformanceAgent] action={} pnl={} pnl_pct={}%", action, pnl, pnlPct);
            return pnl;
        } catch (Exception e) {
            log.error("[PerformanceAgent] failed: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }
}
