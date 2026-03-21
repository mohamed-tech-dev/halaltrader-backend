package com.halaltrader.backend.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.halaltrader.backend.dto.MarketDataDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

@Component
public class MarketDataClient {

    private static final Logger log = LoggerFactory.getLogger(MarketDataClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public MarketDataClient(@Qualifier("marketDataWebClient") WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    // Convenience constructor for tests
    public MarketDataClient(WebClient webClient) {
        this(webClient, new ObjectMapper());
    }

    public MarketDataDto getMarketData(String symbol) {
        try {
            String priceJson = webClient.get()
                    .uri("/price/{symbol}", symbol)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode price = objectMapper.readTree(priceJson);

            List<String> news = fetchNewsTitles(symbol);

            return new MarketDataDto(
                    price.path("symbol").asText(),
                    price.path("price").asDouble(),
                    price.path("change_pct").asDouble(),
                    price.path("volume").asLong(),
                    price.path("rsi").asDouble(),
                    price.path("macd").asDouble(),
                    price.path("macd_signal").asDouble(),
                    price.path("ma20").asDouble(),
                    price.path("ma50").asDouble(),
                    news
            );
        } catch (Exception e) {
            log.error("[MarketDataClient] failed to fetch data for {}: {}", symbol, e.getMessage());
            throw new RuntimeException("MarketData fetch failed for " + symbol, e);
        }
    }

    private List<String> fetchNewsTitles(String symbol) {
        try {
            String newsJson = webClient.get()
                    .uri("/news/{symbol}", symbol)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode newsArray = objectMapper.readTree(newsJson);
            List<String> titles = new ArrayList<>();
            for (JsonNode item : newsArray) {
                String title = item.path("title").asText();
                if (!title.isBlank()) titles.add(title);
            }
            return titles;
        } catch (Exception e) {
            log.warn("[MarketDataClient] news fetch failed for {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }
}
