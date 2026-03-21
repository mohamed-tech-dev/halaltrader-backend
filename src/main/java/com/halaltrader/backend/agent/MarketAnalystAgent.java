package com.halaltrader.backend.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.halaltrader.backend.client.AnthropicClient;
import com.halaltrader.backend.config.AnthropicProperties;
import com.halaltrader.backend.dto.MarketAnalysisReport;
import com.halaltrader.backend.dto.MarketDataDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MarketAnalystAgent {

    private static final Logger log = LoggerFactory.getLogger(MarketAnalystAgent.class);
    private static final int MAX_TOKENS = 300;
    private static final String SYSTEM_PROMPT = """
            You are a quantitative market analyst. Your sole task is to produce a technical analysis from market indicators.
            Return ONLY a JSON object — no markdown:
            {"trend": "BULLISH"|"BEARISH"|"NEUTRAL", "momentum": "STRONG"|"MODERATE"|"WEAK", "summary": "<2 sentences max>"}
            Base your analysis strictly on the provided price, RSI, MACD, moving averages, and recent news.
            """;

    private final AnthropicClient anthropicClient;
    private final AnthropicProperties props;
    private final ObjectMapper objectMapper;

    public MarketAnalystAgent(AnthropicClient anthropicClient, AnthropicProperties props, ObjectMapper objectMapper) {
        this.anthropicClient = anthropicClient;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public MarketAnalysisReport analyze(MarketDataDto data) {
        String userMessage = String.format(
                "Symbol: %s | Price: %.4f | Change: %.2f%% | RSI: %.2f | MACD: %.4f | Signal: %.4f | MA20: %.4f | MA50: %.4f | News: %s",
                data.symbol(), data.price(), data.changePct(), data.rsi(),
                data.macd(), data.macdSignal(), data.ma20(), data.ma50(),
                String.join("; ", data.newsTitles())
        );
        try {
            String model = props.model("market-analyst");
            String response = anthropicClient.call(model, SYSTEM_PROMPT, userMessage, MAX_TOKENS);
            JsonNode node = objectMapper.readTree(response);
            String trend = node.path("trend").asText("NEUTRAL");
            String momentum = node.path("momentum").asText("MODERATE");
            String summary = node.path("summary").asText();
            log.info("[MarketAnalystAgent] {} → trend={} momentum={}", data.symbol(), trend, momentum);
            return new MarketAnalysisReport(trend, momentum, summary);
        } catch (Exception e) {
            log.error("[MarketAnalystAgent] failed for {}: {}", data.symbol(), e.getMessage());
            return new MarketAnalysisReport("NEUTRAL", "WEAK", "Analysis unavailable");
        }
    }
}
