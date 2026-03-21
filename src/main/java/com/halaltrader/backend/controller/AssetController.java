package com.halaltrader.backend.controller;

import com.halaltrader.backend.dto.AssetDto;
import com.halaltrader.backend.service.AssetQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetQueryService assetQueryService;

    @GetMapping
    public List<AssetDto> list() {
        return assetQueryService.list();
    }
}
