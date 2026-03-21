package com.halaltrader.backend.repository;

import com.halaltrader.backend.entity.Asset;
import com.halaltrader.backend.entity.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TradeRepository extends JpaRepository<Trade, UUID> {
    List<Trade> findTop5ByAssetOrderByExecutedAtDesc(Asset asset);
}
