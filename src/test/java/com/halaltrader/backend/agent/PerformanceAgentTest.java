package com.halaltrader.backend.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.halaltrader.backend.client.AnthropicClient;
import com.halaltrader.backend.config.AnthropicProperties;
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
class PerformanceAgentTest {

    @Mock AnthropicClient anthropicClient;
    @Mock AnthropicProperties props;
    @Spy ObjectMapper objectMapper;
    @InjectMocks PerformanceAgent agent;

    @Test
    void compute_sellWithProfit_returnsPositivePnl() {
        when(props.model("performance")).thenReturn("claude-haiku-4-5-20251001");
        when(anthropicClient.call(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn("{\"pnl\":250.00,\"pnl_pct\":16.67,\"comment\":\"Profitable exit.\"}");

        BigDecimal pnl = agent.compute(TradeAction.SELL, BigDecimal.valueOf(5),
                BigDecimal.valueOf(175), BigDecimal.valueOf(150));

        assertThat(pnl).isEqualByComparingTo(BigDecimal.valueOf(250.00));
    }

    @Test
    void compute_buyTrade_returnsZeroPnl() {
        when(props.model("performance")).thenReturn("claude-haiku-4-5-20251001");
        when(anthropicClient.call(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn("{\"pnl\":0,\"pnl_pct\":0,\"comment\":\"BUY trade, PnL not realized.\"}");

        BigDecimal pnl = agent.compute(TradeAction.BUY, BigDecimal.valueOf(3),
                BigDecimal.valueOf(175), BigDecimal.ZERO);

        assertThat(pnl).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void compute_apiError_returnsZero() {
        when(props.model("performance")).thenReturn("claude-haiku-4-5-20251001");
        when(anthropicClient.call(anyString(), anyString(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("timeout"));

        BigDecimal pnl = agent.compute(TradeAction.SELL, BigDecimal.valueOf(3),
                BigDecimal.valueOf(175), BigDecimal.valueOf(150));

        assertThat(pnl).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
