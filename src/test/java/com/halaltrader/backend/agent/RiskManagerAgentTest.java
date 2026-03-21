package com.halaltrader.backend.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.halaltrader.backend.client.AnthropicClient;
import com.halaltrader.backend.config.AnthropicProperties;
import com.halaltrader.backend.dto.MarketAnalysisReport;
import com.halaltrader.backend.dto.RiskReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskManagerAgentTest {

    @Mock AnthropicClient anthropicClient;
    @Mock AnthropicProperties props;
    @Spy ObjectMapper objectMapper;
    @InjectMocks RiskManagerAgent agent;

    private MarketAnalysisReport bullishAnalysis() {
        return new MarketAnalysisReport("BULLISH", "STRONG", "Positive signals.");
    }

    @Test
    void assess_lowRisk_returnsPositiveMaxQuantity() {
        when(props.model("risk-manager")).thenReturn("claude-sonnet-4-6");
        when(anthropicClient.call(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn("{\"level\":\"LOW\",\"max_quantity\":5,\"reason\":\"Volatility low.\"}");

        RiskReport report = agent.assess("AAPL", BigDecimal.valueOf(50000), BigDecimal.ZERO,
                BigDecimal.valueOf(175), bullishAnalysis());

        assertThat(report.level()).isEqualTo("LOW");
        assertThat(report.maxQuantity()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(report.reason()).isNotBlank();
    }

    @Test
    void assess_highRisk_returnsZeroMaxQuantity() {
        when(props.model("risk-manager")).thenReturn("claude-sonnet-4-6");
        when(anthropicClient.call(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn("{\"level\":\"HIGH\",\"max_quantity\":0,\"reason\":\"High volatility.\"}");

        RiskReport report = agent.assess("NVDA", BigDecimal.valueOf(10000), BigDecimal.valueOf(5),
                BigDecimal.valueOf(500), new MarketAnalysisReport("BEARISH", "WEAK", "Bad."));

        assertThat(report.level()).isEqualTo("HIGH");
        assertThat(report.maxQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void assess_apiError_returnsHighRiskFallback() {
        when(props.model("risk-manager")).thenReturn("claude-sonnet-4-6");
        when(anthropicClient.call(anyString(), anyString(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("timeout"));

        RiskReport report = agent.assess("AAPL", BigDecimal.valueOf(50000), BigDecimal.ZERO,
                BigDecimal.valueOf(175), bullishAnalysis());

        assertThat(report.level()).isEqualTo("HIGH");
        assertThat(report.maxQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void assess_malformedJson_returnsHighRiskFallback() {
        when(props.model("risk-manager")).thenReturn("claude-sonnet-4-6");
        when(anthropicClient.call(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn("not json");

        RiskReport report = agent.assess("AAPL", BigDecimal.valueOf(50000), BigDecimal.ZERO,
                BigDecimal.valueOf(175), bullishAnalysis());

        assertThat(report.level()).isEqualTo("HIGH");
        assertThat(report.maxQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
