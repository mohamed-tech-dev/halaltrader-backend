package com.halaltrader.backend.controller;

import com.halaltrader.backend.config.AnthropicProperties;
import com.halaltrader.backend.dto.StatsDto;
import com.halaltrader.backend.service.StatsQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StatsController.class)
class StatsControllerTest {

    @Autowired MockMvc mvc;
    @MockBean StatsQueryService statsQueryService;
    @MockBean AnthropicProperties anthropicProperties;

    @Test
    void getStats_returns200WithStructure() throws Exception {
        var dto = new StatsDto(
                List.of(),
                new StatsDto.Records(null, null, 0, 0, null),
                List.of()
        );
        when(statsQueryService.getStats()).thenReturn(dto);

        mvc.perform(get("/api/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyPerformance").isArray());
    }
}
