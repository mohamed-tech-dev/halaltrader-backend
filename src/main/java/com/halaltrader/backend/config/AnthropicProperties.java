package com.halaltrader.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "anthropic")
public class AnthropicProperties {

    private String apiKey;
    private Map<String, String> models = Map.of();

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public Map<String, String> getModels() { return models; }
    public void setModels(Map<String, String> models) { this.models = models; }

    public String model(String agentKey) {
        return models.getOrDefault(agentKey, "claude-haiku-4-5-20251001");
    }
}
