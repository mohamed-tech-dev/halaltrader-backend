package com.halaltrader.backend.repository;

import com.halaltrader.backend.entity.Asset;
import com.halaltrader.backend.entity.Portfolio;
import com.halaltrader.backend.entity.PortfolioPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortfolioPositionRepository extends JpaRepository<PortfolioPosition, UUID> {
    Optional<PortfolioPosition> findByPortfolioAndAsset(Portfolio portfolio, Asset asset);
}
