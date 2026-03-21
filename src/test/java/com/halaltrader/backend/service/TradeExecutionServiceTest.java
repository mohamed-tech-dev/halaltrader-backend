package com.halaltrader.backend.service;

import com.halaltrader.backend.dto.TradeDecision;
import com.halaltrader.backend.entity.*;
import com.halaltrader.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeExecutionServiceTest {

    @Mock PortfolioRepository portfolioRepository;
    @Mock PortfolioPositionRepository positionRepository;
    @Mock TradeRepository tradeRepository;
    @InjectMocks TradeExecutionService service;

    private Portfolio buildPortfolio() {
        Portfolio p = new Portfolio();
        p.setId(UUID.randomUUID());
        p.setName("Test");
        p.setCashBalance(BigDecimal.valueOf(10000));
        return p;
    }

    private Asset buildAsset(String symbol) {
        Asset a = new Asset();
        a.setId(UUID.randomUUID());
        a.setSymbol(symbol);
        return a;
    }

    @Test
    void execute_buyTrade_deductsFromCashAndSavesTrade() {
        Portfolio portfolio = buildPortfolio();
        Asset asset = buildAsset("AAPL");

        when(positionRepository.findByPortfolioAndAsset(portfolio, asset)).thenReturn(Optional.empty());
        when(tradeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TradeDecision decision = new TradeDecision(TradeAction.BUY, BigDecimal.valueOf(2), "Bullish.");

        service.execute(portfolio, asset, decision, BigDecimal.valueOf(175), BigDecimal.ZERO, "{}");

        ArgumentCaptor<Portfolio> portfolioCaptor = ArgumentCaptor.forClass(Portfolio.class);
        verify(portfolioRepository).save(portfolioCaptor.capture());
        assertThat(portfolioCaptor.getValue().getCashBalance())
                .isEqualByComparingTo(BigDecimal.valueOf(9650)); // 10000 - 2*175

        ArgumentCaptor<Trade> tradeCaptor = ArgumentCaptor.forClass(Trade.class);
        verify(tradeRepository).save(tradeCaptor.capture());
        assertThat(tradeCaptor.getValue().getAction()).isEqualTo(TradeAction.BUY);
        assertThat(tradeCaptor.getValue().getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(2));
        assertThat(tradeCaptor.getValue().getTechnicalData()).isEqualTo("{}");
    }

    @Test
    void execute_buyTrade_existingPosition_updatesAvgPrice() {
        Portfolio portfolio = buildPortfolio();
        Asset asset = buildAsset("AAPL");

        PortfolioPosition existing = new PortfolioPosition();
        existing.setPortfolio(portfolio);
        existing.setAsset(asset);
        existing.setQuantity(BigDecimal.valueOf(2));
        existing.setAverageBuyPrice(BigDecimal.valueOf(150));

        when(positionRepository.findByPortfolioAndAsset(portfolio, asset)).thenReturn(Optional.of(existing));
        when(tradeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TradeDecision decision = new TradeDecision(TradeAction.BUY, BigDecimal.valueOf(2), "Buy more.");

        service.execute(portfolio, asset, decision, BigDecimal.valueOf(200), BigDecimal.ZERO, "{}");

        ArgumentCaptor<PortfolioPosition> posCaptor = ArgumentCaptor.forClass(PortfolioPosition.class);
        verify(positionRepository).save(posCaptor.capture());
        // avg = (2*150 + 2*200) / 4 = 700/4 = 175
        assertThat(posCaptor.getValue().getAverageBuyPrice()).isEqualByComparingTo(BigDecimal.valueOf(175));
        assertThat(posCaptor.getValue().getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(4));
    }

    @Test
    void execute_sellTrade_addsCashAndReducesPosition() {
        Portfolio portfolio = buildPortfolio();
        Asset asset = buildAsset("AAPL");

        PortfolioPosition existing = new PortfolioPosition();
        existing.setPortfolio(portfolio);
        existing.setAsset(asset);
        existing.setQuantity(BigDecimal.valueOf(5));
        existing.setAverageBuyPrice(BigDecimal.valueOf(150));

        when(positionRepository.findByPortfolioAndAsset(portfolio, asset)).thenReturn(Optional.of(existing));
        when(tradeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TradeDecision decision = new TradeDecision(TradeAction.SELL, BigDecimal.valueOf(3), "Take profit.");
        BigDecimal pnl = BigDecimal.valueOf(75); // (175-150)*3

        service.execute(portfolio, asset, decision, BigDecimal.valueOf(175), pnl, "{}");

        ArgumentCaptor<Portfolio> portfolioCaptor = ArgumentCaptor.forClass(Portfolio.class);
        verify(portfolioRepository).save(portfolioCaptor.capture());
        // 10000 + 3*175 = 10525
        assertThat(portfolioCaptor.getValue().getCashBalance()).isEqualByComparingTo(BigDecimal.valueOf(10525));

        ArgumentCaptor<PortfolioPosition> posCaptor = ArgumentCaptor.forClass(PortfolioPosition.class);
        verify(positionRepository).save(posCaptor.capture());
        assertThat(posCaptor.getValue().getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(2)); // 5-3
    }

    @Test
    void execute_holdTrade_doesNothing() {
        Portfolio portfolio = buildPortfolio();
        Asset asset = buildAsset("AAPL");

        TradeDecision decision = new TradeDecision(TradeAction.HOLD, BigDecimal.ZERO, "Waiting.");

        service.execute(portfolio, asset, decision, BigDecimal.valueOf(175), BigDecimal.ZERO, "{}");

        verifyNoInteractions(portfolioRepository, positionRepository, tradeRepository);
    }

    @Test
    void execute_sellAllShares_deletesPosition() {
        Portfolio portfolio = buildPortfolio();
        Asset asset = buildAsset("AAPL");

        PortfolioPosition existing = new PortfolioPosition();
        existing.setPortfolio(portfolio);
        existing.setAsset(asset);
        existing.setQuantity(BigDecimal.valueOf(3));
        existing.setAverageBuyPrice(BigDecimal.valueOf(150));

        when(positionRepository.findByPortfolioAndAsset(portfolio, asset)).thenReturn(Optional.of(existing));
        when(tradeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TradeDecision decision = new TradeDecision(TradeAction.SELL, BigDecimal.valueOf(3), "Exit all.");

        service.execute(portfolio, asset, decision, BigDecimal.valueOf(175), BigDecimal.valueOf(75), "{}");

        verify(positionRepository).delete(existing);
        verify(positionRepository, never()).save(any());
    }
}
