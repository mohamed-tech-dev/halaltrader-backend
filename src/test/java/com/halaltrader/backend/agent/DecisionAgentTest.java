package com.halaltrader.backend.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.halaltrader.backend.client.AnthropicClient;
import com.halaltrader.backend.config.AnthropicProperties;
import com.halaltrader.backend.dto.*;
import com.halaltrader.backend.entity.TradeAction;
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
class DecisionAgentTest {

    @Mock AnthropicClient anthropicClient;
    @Mock AnthropicProperties props;
    @Spy ObjectMapper objectMapper;
    @InjectMocks DecisionAgent agent;

    @Test
    void decide_bullishLowRisk_returnsBuy() {
        when(props.model("decision")).thenReturn("claude-opus-4-6");
        when(anthropicClient.call(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn("{\"action\":\"BUY\",\"quantity\":3,\"reasoning\":\"Strong bullish trend, low risk.\"}");

        HalalReport halal = new HalalReport(true, "ETF islamique certifié");
        MarketAnalysisReport analysis = new MarketAnalysisReport("BULLISH", "STRONG", "RSI 65.");
        RiskReport risk = new RiskReport("LOW", BigDecimal.valueOf(5), "10% cash limit OK.");

        TradeDecision decision = agent.decide("ISWD.L", halal, analysis, risk, BigDecimal.ZERO);

        assertThat(decision.action()).isEqualTo(TradeAction.BUY);
        assertThat(decision.quantity()).isEqualByComparingTo(BigDecimal.valueOf(3));
        assertThat(decision.reasoning()).isNotBlank();
    }

    @Test
    void decide_highRisk_returnsHold() {
        when(props.model("decision")).thenReturn("claude-opus-4-6");
        when(anthropicClient.call(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn("{\"action\":\"HOLD\",\"quantity\":0,\"reasoning\":\"Risk too high.\"}");

        HalalReport halal = new HalalReport(true, "Approved");
        MarketAnalysisReport analysis = new MarketAnalysisReport("BEARISH", "WEAK", "Bearish.");
        RiskReport risk = new RiskReport("HIGH", BigDecimal.ZERO, "Volatility too high.");

        TradeDecision decision = agent.decide("AAPL", halal, analysis, risk, BigDecimal.valueOf(10));

        assertThat(decision.action()).isEqualTo(TradeAction.HOLD);
        assertThat(decision.quantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void decide_apiError_returnsHoldFallback() {
        when(props.model("decision")).thenReturn("claude-opus-4-6");
        when(anthropicClient.call(anyString(), anyString(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("timeout"));

        HalalReport halal = new HalalReport(true, "OK");
        MarketAnalysisReport analysis = new MarketAnalysisReport("NEUTRAL", "MODERATE", "Flat.");
        RiskReport risk = new RiskReport("LOW", BigDecimal.valueOf(3), "OK");

        TradeDecision decision = agent.decide("AAPL", halal, analysis, risk, BigDecimal.ZERO);

        assertThat(decision.action()).isEqualTo(TradeAction.HOLD);
        assertThat(decision.quantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void decide_malformedJson_returnsHoldFallback() {
        when(props.model("decision")).thenReturn("claude-opus-4-6");
        when(anthropicClient.call(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn("not json");

        HalalReport halal = new HalalReport(true, "OK");
        MarketAnalysisReport analysis = new MarketAnalysisReport("BULLISH", "STRONG", "Good.");
        RiskReport risk = new RiskReport("LOW", BigDecimal.valueOf(5), "OK");

        TradeDecision decision = agent.decide("AAPL", halal, analysis, risk, BigDecimal.ZERO);

        assertThat(decision.action()).isEqualTo(TradeAction.HOLD);
        assertThat(decision.quantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
