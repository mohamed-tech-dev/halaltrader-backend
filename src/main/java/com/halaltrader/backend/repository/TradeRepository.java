package com.halaltrader.backend.repository;

import com.halaltrader.backend.entity.Asset;
import com.halaltrader.backend.entity.Portfolio;
import com.halaltrader.backend.entity.Trade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TradeRepository extends JpaRepository<Trade, UUID> {
    List<Trade> findTop5ByAssetOrderByExecutedAtDesc(Asset asset);
    Page<Trade> findByPortfolio(Portfolio portfolio, Pageable pageable);
    List<Trade> findByPortfolioOrderByExecutedAtAsc(Portfolio portfolio);
}
