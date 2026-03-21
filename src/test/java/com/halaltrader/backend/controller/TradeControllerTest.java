package com.halaltrader.backend.controller;

import com.halaltrader.backend.config.AnthropicProperties;
import com.halaltrader.backend.dto.TradeDetailDto;
import com.halaltrader.backend.dto.TradeDto;
import com.halaltrader.backend.service.TradeQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TradeController.class)
class TradeControllerTest {

    @Autowired MockMvc mvc;
    @MockBean TradeQueryService tradeQueryService;
    @MockBean AnthropicProperties anthropicProperties;

    private final UUID tradeId = UUID.randomUUID();

    @Test
    void list_returns200WithPaginatedTrades() throws Exception {
        var dto = new TradeDto(tradeId, "AAPL", "BUY",
                new BigDecimal("5"), new BigDecimal("175"),
                new BigDecimal("875"), new BigDecimal("50"), LocalDateTime.now());
        when(tradeQueryService.list(anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(dto)));

        mvc.perform(get("/api/trades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.content[0].action").value("BUY"));
    }

    @Test
    void getById_returns200WithDetail() throws Exception {
        var dto = new TradeDetailDto(tradeId, "AAPL", "BUY",
                new BigDecimal("5"), new BigDecimal("175"),
                new BigDecimal("875"), new BigDecimal("50"),
                LocalDateTime.now(), "{\"decision\":\"BUY\"}", "{\"rsi\":45}");
        when(tradeQueryService.getById(tradeId)).thenReturn(dto);

        mvc.perform(get("/api/trades/{id}", tradeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiReasoning").isNotEmpty());
    }

    @Test
    void getById_returns404WhenNotFound() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(tradeQueryService.getById(unknown))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        mvc.perform(get("/api/trades/{id}", unknown))
                .andExpect(status().isNotFound());
    }
}
