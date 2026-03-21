package com.halaltrader.backend.agent;

import com.halaltrader.backend.client.AnthropicClient;
import com.halaltrader.backend.config.AnthropicProperties;
import com.halaltrader.backend.dto.MarketAnalysisReport;
import com.halaltrader.backend.dto.MarketDataDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketAnalystAgentTest {

    @Mock AnthropicClient anthropicClient;
    @Mock AnthropicProperties props;
    @Spy ObjectMapper objectMapper;
    @InjectMocks MarketAnalystAgent agent;

    private MarketDataDto sampleData() {
        return new MarketDataDto("AAPL", 175.5, 1.2, 50000000, 65.0, 1.5, 0.8, 172.0, 168.0, List.of("Apple strong"));
    }

    @Test
    void analyze_bullishSignals_returnsBullishTrend() {
        when(props.model("market-analyst")).thenReturn("claude-sonnet-4-6");
        when(anthropicClient.call(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn("{\"trend\":\"BULLISH\",\"momentum\":\"STRONG\",\"summary\":\"RSI 65, MACD crossover positive.\"}");

        MarketAnalysisReport report = agent.analyze(sampleData());

        assertThat(report.trend()).isEqualTo("BULLISH");
        assertThat(report.momentum()).isEqualTo("STRONG");
        assertThat(report.summary()).isNotBlank();
    }

    @Test
    void analyze_apiError_returnsNeutralFallback() {
        when(props.model("market-analyst")).thenReturn("claude-sonnet-4-6");
        when(anthropicClient.call(anyString(), anyString(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("timeout"));

        MarketAnalysisReport report = agent.analyze(sampleData());

        assertThat(report.trend()).isEqualTo("NEUTRAL");
        assertThat(report.momentum()).isEqualTo("WEAK");
    }

    @Test
    void analyze_malformedJson_returnsNeutralFallback() {
        when(props.model("market-analyst")).thenReturn("claude-sonnet-4-6");
        when(anthropicClient.call(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn("not json");

        MarketAnalysisReport report = agent.analyze(sampleData());

        assertThat(report.trend()).isEqualTo("NEUTRAL");
    }
}
