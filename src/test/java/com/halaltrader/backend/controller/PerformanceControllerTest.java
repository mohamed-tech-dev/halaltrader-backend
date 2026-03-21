package com.halaltrader.backend.controller;

import com.halaltrader.backend.config.AnthropicProperties;
import com.halaltrader.backend.dto.PerformanceDto;
import com.halaltrader.backend.service.PerformanceQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PerformanceController.class)
class PerformanceControllerTest {

    @Autowired MockMvc mvc;
    @MockBean PerformanceQueryService performanceQueryService;
    @MockBean AnthropicProperties anthropicProperties;

    @Test
    void getPerformance_returns200WithStats() throws Exception {
        var dto = new PerformanceDto(
                List.of(new PerformanceDto.DailyPnlEntry("2026-03-21", new BigDecimal("50.00"))),
                new BigDecimal("60.0"), 10,
                new PerformanceDto.AssetPnlEntry("AAPL", new BigDecimal("200.00")),
                new PerformanceDto.AssetPnlEntry("GLD", new BigDecimal("-50.00")),
                new BigDecimal("80.0"), LocalDateTime.now(), 4, 5);
        when(performanceQueryService.getPerformance()).thenReturn(dto);

        mvc.perform(get("/api/performance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.winRate").value(60.0))
                .andExpect(jsonPath("$.totalTrades").value(10))
                .andExpect(jsonPath("$.dailyPnl[0].date").value("2026-03-21"))
                .andExpect(jsonPath("$.bestAsset.symbol").value("AAPL"))
                .andExpect(jsonPath("$.halalApprovalRate").value(80.0));
    }

    @Test
    void getPerformance_returnsEmptyWhenNoTrades() throws Exception {
        var dto = new PerformanceDto(
                List.of(), BigDecimal.ZERO, 0,
                null, null, BigDecimal.ZERO, null, 0, 0);
        when(performanceQueryService.getPerformance()).thenReturn(dto);

        mvc.perform(get("/api/performance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTrades").value(0))
                .andExpect(jsonPath("$.dailyPnl").isEmpty());
    }
}
