package com.halaltrader.backend.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class AnthropicClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicClient.class);
    private static final String MESSAGES_PATH = "/v1/messages";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public AnthropicClient(@Qualifier("anthropicWebClient") WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    public String call(String model, String systemPrompt, String userMessage, int maxTokens) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("system", systemPrompt);

        ArrayNode messages = body.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", userMessage);

        try {
            String response = webClient.post()
                    .uri(MESSAGES_PATH)
                    .header("content-type", "application/json")
                    .bodyValue(body.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            return root.path("content").get(0).path("text").asText();
        } catch (Exception e) {
            log.error("[AnthropicClient] call failed for model={}: {}", model, e.getMessage());
            throw new RuntimeException("Anthropic API call failed", e);
        }
    }
}
