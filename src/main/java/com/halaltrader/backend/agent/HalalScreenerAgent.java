package com.halaltrader.backend.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.halaltrader.backend.client.AnthropicClient;
import com.halaltrader.backend.config.AnthropicProperties;
import com.halaltrader.backend.dto.HalalReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class HalalScreenerAgent {

    private static final Logger log = LoggerFactory.getLogger(HalalScreenerAgent.class);
    private static final int MAX_TOKENS = 600;
    private static final String SYSTEM_PROMPT = """
            You are a halal finance compliance expert. Your sole task is to verify whether a financial asset complies with Islamic finance principles (Shariah law).
            Analyze the provided asset and return ONLY a JSON object — no markdown, no explanation outside JSON:
            {"approved": true|false, "reason": "<concise justification, max 100 chars>"}
            Rules: approved=true only if the asset sector/type contains no alcohol, gambling, pork, interest-based finance, weapons, or tobacco.
            """;

    private final AnthropicClient anthropicClient;
    private final AnthropicProperties props;
    private final ObjectMapper objectMapper;

    public HalalScreenerAgent(AnthropicClient anthropicClient, AnthropicProperties props, ObjectMapper objectMapper) {
        this.anthropicClient = anthropicClient;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public HalalReport analyze(String symbol, String name, String assetType, String sector) {
        String userMessage = String.format(
                "Asset: %s | Name: %s | Type: %s | Sector: %s",
                symbol, name, assetType, sector
        );
        try {
            String model = props.model("halal-screener");
            String response = anthropicClient.call(model, SYSTEM_PROMPT, userMessage, MAX_TOKENS);
            JsonNode node = objectMapper.readTree(response);
            boolean approved = node.path("approved").asBoolean();
            String reason = node.path("reason").asText();
            log.info("[HalalScreenerAgent] {} → approved={} reason=\"{}\"", symbol, approved, reason);
            return new HalalReport(approved, reason);
        } catch (Exception e) {
            log.error("[HalalScreenerAgent] failed for {}: {}", symbol, e.getMessage());
            return new HalalReport(false, "Screening error — defaulting to rejected");
        }
    }
}
