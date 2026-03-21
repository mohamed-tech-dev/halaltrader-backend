package com.halaltrader.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean("anthropicWebClient")
    public WebClient anthropicWebClient(AnthropicProperties props) {
        return WebClient.builder()
                .baseUrl("https://api.anthropic.com")
                .defaultHeader("x-api-key", props.getApiKey())
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("content-type", "application/json")
                .build();
    }

    @Bean("marketDataWebClient")
    public WebClient marketDataWebClient(
            @Value("${market-data.base-url:http://localhost:8081}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}
