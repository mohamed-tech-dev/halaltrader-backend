package com.halaltrader.backend.controller;

import com.halaltrader.backend.config.AnthropicProperties;
import com.halaltrader.backend.dto.PortfolioSummaryDto;
import com.halaltrader.backend.dto.PositionDto;
import com.halaltrader.backend.service.PortfolioQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PortfolioController.class)
class PortfolioControllerTest {

    @Autowired MockMvc mvc;
    @MockBean PortfolioQueryService portfolioQueryService;
    @MockBean AnthropicProperties anthropicProperties; // prevents WebClientConfig from failing

    @Test
    void getSummary_returns200WithPortfolioData() throws Exception {
        var dto = new PortfolioSummaryDto(
                UUID.randomUUID(), "Main Portfolio",
                new BigDecimal("48000.0000"), new BigDecimal("102450.0000"),
                new BigDecimal("2450.0000"), new BigDecimal("2.4500"), 2);
        when(portfolioQueryService.getSummary()).thenReturn(dto);

        mvc.perform(get("/api/portfolio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Main Portfolio"))
                .andExpect(jsonPath("$.positionCount").value(2));
    }

    @Test
    void getPositions_returnsPositionList() throws Exception {
        var position = new PositionDto("AAPL", "Apple Inc.", "STOCK",
                new BigDecimal("5.00000000"), new BigDecimal("175.0000"),
                new BigDecimal("875.0000"));
        when(portfolioQueryService.getPositions()).thenReturn(List.of(position));

        mvc.perform(get("/api/portfolio/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("AAPL"));
    }

    @Test
    void getPositions_returnsEmptyList() throws Exception {
        when(portfolioQueryService.getPositions()).thenReturn(List.of());

        mvc.perform(get("/api/portfolio/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
