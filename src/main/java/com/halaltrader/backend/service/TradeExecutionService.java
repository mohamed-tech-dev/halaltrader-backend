package com.halaltrader.backend.service;

import com.halaltrader.backend.dto.TradeDecision;
import com.halaltrader.backend.entity.*;
import com.halaltrader.backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class TradeExecutionService {

    private static final Logger log = LoggerFactory.getLogger(TradeExecutionService.class);

    private final PortfolioRepository portfolioRepository;
    private final PortfolioPositionRepository positionRepository;
    private final TradeRepository tradeRepository;

    public TradeExecutionService(PortfolioRepository portfolioRepository,
                                 PortfolioPositionRepository positionRepository,
                                 TradeRepository tradeRepository) {
        this.portfolioRepository = portfolioRepository;
        this.positionRepository = positionRepository;
        this.tradeRepository = tradeRepository;
    }

    @Transactional
    public void execute(Portfolio portfolio, Asset asset, TradeDecision decision,
                        BigDecimal price, BigDecimal simulatedPnl, String auditJson) {
        if (decision.action() == TradeAction.HOLD || decision.quantity().compareTo(BigDecimal.ZERO) == 0) {
            log.info("[TradeExecutionService] HOLD for {} — no action taken", asset.getSymbol());
            return;
        }

        BigDecimal totalAmount = price.multiply(decision.quantity());

        if (decision.action() == TradeAction.BUY) {
            executeBuy(portfolio, asset, decision, price, totalAmount, simulatedPnl, auditJson);
        } else if (decision.action() == TradeAction.SELL) {
            executeSell(portfolio, asset, decision, price, totalAmount, simulatedPnl, auditJson);
        }
    }

    private void executeBuy(Portfolio portfolio, Asset asset, TradeDecision decision,
                            BigDecimal price, BigDecimal totalAmount, BigDecimal pnl, String audit) {
        portfolio.setCashBalance(portfolio.getCashBalance().subtract(totalAmount));
        portfolioRepository.save(portfolio);

        Optional<PortfolioPosition> existing = positionRepository.findByPortfolioAndAsset(portfolio, asset);
        if (existing.isPresent()) {
            PortfolioPosition pos = existing.get();
            BigDecimal newQty = pos.getQuantity().add(decision.quantity());
            BigDecimal newAvg = pos.getAverageBuyPrice()
                    .multiply(pos.getQuantity())
                    .add(price.multiply(decision.quantity()))
                    .divide(newQty, 4, RoundingMode.HALF_UP);
            pos.setQuantity(newQty);
            pos.setAverageBuyPrice(newAvg);
            positionRepository.save(pos);
        } else {
            PortfolioPosition pos = new PortfolioPosition();
            pos.setPortfolio(portfolio);
            pos.setAsset(asset);
            pos.setQuantity(decision.quantity());
            pos.setAverageBuyPrice(price);
            positionRepository.save(pos);
        }

        saveTrade(portfolio, asset, decision, price, totalAmount, pnl, audit);
        log.info("[TradeExecutionService] BUY {} qty={} price={} total={}", asset.getSymbol(), decision.quantity(), price, totalAmount);
    }

    private void executeSell(Portfolio portfolio, Asset asset, TradeDecision decision,
                             BigDecimal price, BigDecimal totalAmount, BigDecimal pnl, String audit) {
        portfolio.setCashBalance(portfolio.getCashBalance().add(totalAmount));
        portfolioRepository.save(portfolio);

        positionRepository.findByPortfolioAndAsset(portfolio, asset).ifPresent(pos -> {
            BigDecimal newQty = pos.getQuantity().subtract(decision.quantity());
            if (newQty.compareTo(BigDecimal.ZERO) <= 0) {
                positionRepository.delete(pos);
            } else {
                pos.setQuantity(newQty);
                positionRepository.save(pos);
            }
        });

        saveTrade(portfolio, asset, decision, price, totalAmount, pnl, audit);
        log.info("[TradeExecutionService] SELL {} qty={} price={} pnl={}", asset.getSymbol(), decision.quantity(), price, pnl);
    }

    private void saveTrade(Portfolio portfolio, Asset asset, TradeDecision decision,
                           BigDecimal price, BigDecimal totalAmount, BigDecimal pnl, String audit) {
        Trade trade = new Trade();
        trade.setPortfolio(portfolio);
        trade.setAsset(asset);
        trade.setAction(decision.action());
        trade.setQuantity(decision.quantity());
        trade.setPrice(price);
        trade.setTotalAmount(totalAmount);
        trade.setAiReasoning(decision.reasoning());
        trade.setTechnicalData(audit);
        trade.setSimulatedPnl(pnl);
        trade.setExecutedAt(LocalDateTime.now());
        tradeRepository.save(trade);
    }
}
