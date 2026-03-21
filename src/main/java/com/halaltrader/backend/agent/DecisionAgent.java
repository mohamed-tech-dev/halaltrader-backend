package com.halaltrader.backend.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.halaltrader.backend.client.AnthropicClient;
import com.halaltrader.backend.config.AnthropicProperties;
import com.halaltrader.backend.dto.*;
import com.halaltrader.backend.entity.TradeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DecisionAgent {

    private static final Logger log = LoggerFactory.getLogger(DecisionAgent.class);
    private static final int MAX_TOKENS = 600;
    private static final String SYSTEM_PROMPT = """
            You are the final decision maker for a halal trading simulation. Your sole task is to synthesize three reports and make a trade decision.
            Return ONLY a JSON object — no markdown:
            {"action": "BUY"|"SELL"|"HOLD", "quantity": <integer>, "reasoning": "<3 sentences max>"}
            Rules:
            - If risk level is HIGH, action MUST be HOLD and quantity MUST be 0.
            - quantity must never exceed max_quantity from the risk report.
            - quantity must be 0 for HOLD.
            - For SELL, quantity is the number of shares to sell (max = current position).
            """;

    private final AnthropicClient anthropicClient;
    private final AnthropicProperties props;
    private final ObjectMapper objectMapper;

    public DecisionAgent(AnthropicClient anthropicClient, AnthropicProperties props, ObjectMapper objectMapper) {
        this.anthropicClient = anthropicClient;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public TradeDecision decide(String symbol, HalalReport halal, MarketAnalysisReport analysis,
                                RiskReport risk, BigDecimal currentPositionQty) {
        String userMessage = String.format(
                "Symbol: %s\nHalal: approved=%s, reason=%s\nMarket: trend=%s, momentum=%s, summary=%s\nRisk: level=%s, max_quantity=%s, reason=%s\nCurrent position: %s shares",
                symbol,
                halal.approved(), halal.reason(),
                analysis.trend(), analysis.momentum(), analysis.summary(),
                risk.level(), risk.maxQuantity(), risk.reason(),
                currentPositionQty
        );
        try {
            String model = props.model("decision");
            String response = anthropicClient.call(model, SYSTEM_PROMPT, userMessage, MAX_TOKENS);
            JsonNode node = objectMapper.readTree(response);
            TradeAction action = TradeAction.valueOf(node.path("action").asText("HOLD").toUpperCase());
            BigDecimal quantity = BigDecimal.valueOf(node.path("quantity").asLong(0));
            String reasoning = node.path("reasoning").asText();
            log.info("[DecisionAgent] {} → action={} qty={}", symbol, action, quantity);
            return new TradeDecision(action, quantity, reasoning);
        } catch (Exception e) {
            log.error("[DecisionAgent] failed for {}: {}", symbol, e.getMessage());
            return new TradeDecision(TradeAction.HOLD, BigDecimal.ZERO, "Decision error — defaulting to HOLD");
        }
    }
}
