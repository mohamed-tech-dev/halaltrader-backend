package com.halaltrader.backend.service;

import com.halaltrader.backend.dto.AssetDto;
import com.halaltrader.backend.repository.AssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AssetQueryService {

    private final AssetRepository assetRepository;

    public List<AssetDto> list() {
        return assetRepository.findAll().stream()
                .map(a -> new AssetDto(
                        a.getId(), a.getSymbol(), a.getName(),
                        a.getAssetType().name(), a.getHalalScreening().name(),
                        a.getHalalReason(), a.getSector()))
                .toList();
    }
}
