package com.halaltrader.backend.controller;

import com.halaltrader.backend.config.AnthropicProperties;
import com.halaltrader.backend.dto.AssetDto;
import com.halaltrader.backend.service.AssetQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AssetController.class)
class AssetControllerTest {

    @Autowired MockMvc mvc;
    @MockBean AssetQueryService assetQueryService;
    @MockBean AnthropicProperties anthropicProperties;

    @Test
    void list_returns200WithAssets() throws Exception {
        var dto = new AssetDto(UUID.randomUUID(), "AAPL", "Apple Inc.",
                "STOCK", "APPROVED", "Technology sector", "Technology");
        when(assetQueryService.list()).thenReturn(List.of(dto));

        mvc.perform(get("/api/assets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].halalScreening").value("APPROVED"));
    }

    @Test
    void list_returnsEmptyWhenNoAssets() throws Exception {
        when(assetQueryService.list()).thenReturn(List.of());

        mvc.perform(get("/api/assets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
