package com.halaltrader.backend.agent;

import com.halaltrader.backend.client.AnthropicClient;
import com.halaltrader.backend.config.AnthropicProperties;
import com.halaltrader.backend.dto.HalalReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.Spy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HalalScreenerAgentTest {

    @Mock AnthropicClient anthropicClient;
    @Mock AnthropicProperties props;
    @Spy ObjectMapper objectMapper;
    @InjectMocks HalalScreenerAgent agent;

    @Test
    void analyze_approvedAsset_returnsApprovedTrue() {
        when(props.model("halal-screener")).thenReturn("claude-opus-4-6");
        when(anthropicClient.call(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn("{\"approved\":true,\"reason\":\"ETF islamique certifié\"}");

        HalalReport report = agent.analyze("ISWD.L", "iShares MSCI World Islamic ETF", "ETF", "Islamic");

        assertThat(report.approved()).isTrue();
        assertThat(report.reason()).isNotBlank();
    }

    @Test
    void analyze_rejectedAsset_returnsApprovedFalse() {
        when(props.model("halal-screener")).thenReturn("claude-opus-4-6");
        when(anthropicClient.call(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn("{\"approved\":false,\"reason\":\"Secteur alcool interdit\"}");

        HalalReport report = agent.analyze("BUD", "Anheuser-Busch", "STOCK", "Alcohol");

        assertThat(report.approved()).isFalse();
    }
}
