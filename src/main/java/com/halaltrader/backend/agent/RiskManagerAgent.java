package com.halaltrader.backend.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.halaltrader.backend.client.AnthropicClient;
import com.halaltrader.backend.config.AnthropicProperties;
import com.halaltrader.backend.dto.MarketAnalysisReport;
import com.halaltrader.backend.dto.RiskReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class RiskManagerAgent {

    private static final Logger log = LoggerFactory.getLogger(RiskManagerAgent.class);
    private static final int MAX_TOKENS = 300;
    private static final String SYSTEM_PROMPT = """
            You are a risk manager for a simulated halal trading portfolio. Your sole task is to evaluate position risk.
            Return ONLY a JSON object — no markdown:
            {"level": "LOW"|"MEDIUM"|"HIGH", "max_quantity": <integer>, "reason": "<1 sentence>"}
            Rules:
            - Never risk more than 10% of portfolio cash in a single trade.
            - If level=HIGH, max_quantity must be 0.
            - max_quantity is the number of shares/units, computed from cash_available * 10% / price.
            """;

    private final AnthropicClient anthropicClient;
    private final AnthropicProperties props;
    private final ObjectMapper objectMapper;

    public RiskManagerAgent(AnthropicClient anthropicClient, AnthropicProperties props, ObjectMapper objectMapper) {
        this.anthropicClient = anthropicClient;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public RiskReport assess(String symbol, BigDecimal cashBalance, BigDecimal currentPositionQty,
                             BigDecimal currentPrice, MarketAnalysisReport analysis) {
        String userMessage = String.format(
                "Symbol: %s | Cash: %.2f | Current position qty: %.4f | Price: %.4f | Trend: %s | Momentum: %s | Analysis: %s",
                symbol, cashBalance, currentPositionQty, currentPrice,
                analysis.trend(), analysis.momentum(), analysis.summary()
        );
        try {
            String model = props.model("risk-manager");
            String response = anthropicClient.call(model, SYSTEM_PROMPT, userMessage, MAX_TOKENS);
            JsonNode node = objectMapper.readTree(response);
            String level = node.path("level").asText("HIGH");
            BigDecimal maxQty = BigDecimal.valueOf(node.path("max_quantity").asLong(0));
            String reason = node.path("reason").asText();
            log.info("[RiskManagerAgent] {} → level={} maxQty={}", symbol, level, maxQty);
            return new RiskReport(level, maxQty, reason);
        } catch (Exception e) {
            log.error("[RiskManagerAgent] failed for {}: {}", symbol, e.getMessage());
            return new RiskReport("HIGH", BigDecimal.ZERO, "Risk assessment error — defaulting to HIGH");
        }
    }
}
