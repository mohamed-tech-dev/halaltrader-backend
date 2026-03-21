package com.halaltrader.backend.repository;

import com.halaltrader.backend.entity.Asset;
import com.halaltrader.backend.entity.HalalScreening;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AssetRepository extends JpaRepository<Asset, UUID> {
    List<Asset> findByHalalScreening(HalalScreening status);
}
