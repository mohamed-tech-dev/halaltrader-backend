package com.halaltrader.backend.controller;

import com.halaltrader.backend.dto.TradeDetailDto;
import com.halaltrader.backend.dto.TradeDto;
import com.halaltrader.backend.service.TradeQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
public class TradeController {

    private final TradeQueryService tradeQueryService;

    @GetMapping
    public Page<TradeDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return tradeQueryService.list(page, size);
    }

    @GetMapping("/{id}")
    public TradeDetailDto getById(@PathVariable UUID id) {
        return tradeQueryService.getById(id);
    }
}
