package com.halaltrader.backend.dto;

import java.util.UUID;

public record AssetDto(
        UUID id,
        String symbol,
        String name,
        String assetType,
        String halalScreening,
        String halalReason,
        String sector
) {}
