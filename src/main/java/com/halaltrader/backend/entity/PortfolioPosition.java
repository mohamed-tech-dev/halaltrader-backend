package com.halaltrader.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"portfolio", "asset"})
@Entity
@Table(name = "portfolio_positions")
public class PortfolioPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity;

    @Column(name = "average_buy_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal averageBuyPrice;
}
