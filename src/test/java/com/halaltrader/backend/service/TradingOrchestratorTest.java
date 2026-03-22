package com.halaltrader.backend.service;

import com.halaltrader.backend.agent.*;
import com.halaltrader.backend.client.MarketDataClient;
import com.halaltrader.backend.dto.*;
import com.halaltrader.backend.entity.*;
import com.halaltrader.backend.repository.*;
import com.halaltrader.backend.websocket.TradingEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradingOrchestratorTest {

    @Mock HalalScreenerAgent halalScreenerAgent;
    @Mock MarketAnalystAgent marketAnalystAgent;
    @Mock RiskManagerAgent riskManagerAgent;
    @Mock DecisionAgent decisionAgent;
    @Mock PerformanceAgent performanceAgent;
    @Mock MarketDataClient marketDataClient;
    @Mock AssetRepository assetRepository;
    @Mock PortfolioRepository portfolioRepository;
    @Mock PortfolioPositionRepository positionRepository;
    @Mock TradeExecutionService tradeExecutionService;
    @Mock TradingEventPublisher tradingEventPublisher;
    @InjectMocks TradingOrchestrator orchestrator;

    private Asset buildAsset(String symbol) {
        Asset a = new Asset();
        a.setId(UUID.randomUUID());
        a.setSymbol(symbol);
        a.setName("Test Asset");
        a.setAssetType(AssetType.STOCK);
        a.setHalalScreening(HalalScreening.APPROVED);
        a.setSector("Technology");
        return a;
    }

    private Portfolio buildPortfolio() {
        Portfolio p = new Portfolio();
        p.setId(UUID.randomUUID());
        p.setName("Simulation Portfolio");
        p.setCashBalance(BigDecimal.valueOf(100000));
        return p;
    }

    @Test
    void run_halalRejected_stopsPipelineEarly() {
        Asset asset = buildAsset("BUD");
        Portfolio portfolio = buildPortfolio();

        when(assetRepository.findByHalalScreening(HalalScreening.APPROVED)).thenReturn(List.of(asset));
        when(portfolioRepository.findAll()).thenReturn(List.of(portfolio));
        when(halalScreenerAgent.analyze(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new HalalReport(false, "Alcool interdit"));

        orchestrator.run();

        verifyNoInteractions(marketDataClient, marketAnalystAgent, riskManagerAgent, decisionAgent);
        verify(tradeExecutionService, never()).execute(any(), any(), any(), any(), any(), any());
    }

    @Test
    void run_validPipeline_callsAllAgentsAndExecutes() {
        Asset asset = buildAsset("AAPL");
        Portfolio portfolio = buildPortfolio();
        MarketDataDto marketData = new MarketDataDto("AAPL", 175.0, 1.0, 1000000, 60, 1.0, 0.5, 170, 165, List.of());
        MarketAnalysisReport analysis = new MarketAnalysisReport("BULLISH", "STRONG", "Good.");
        RiskReport risk = new RiskReport("LOW", BigDecimal.valueOf(3), "OK");
        TradeDecision decision = new TradeDecision(TradeAction.BUY, BigDecimal.valueOf(2), "Buy it.");

        when(assetRepository.findByHalalScreening(HalalScreening.APPROVED)).thenReturn(List.of(asset));
        when(portfolioRepository.findAll()).thenReturn(List.of(portfolio));
        when(halalScreenerAgent.analyze(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new HalalReport(true, "Tech sector OK"));
        when(marketDataClient.getMarketData("AAPL")).thenReturn(marketData);
        when(marketAnalystAgent.analyze(marketData)).thenReturn(analysis);
        when(riskManagerAgent.assess(anyString(), any(), any(), any(), any())).thenReturn(risk);
        when(decisionAgent.decide(anyString(), any(), any(), any(), any())).thenReturn(decision);
        when(performanceAgent.compute(any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(positionRepository.findByPortfolioAndAsset(portfolio, asset)).thenReturn(Optional.empty());

        orchestrator.run();

        verify(tradeExecutionService).execute(eq(portfolio), eq(asset), eq(decision), any(), any(), anyString());
    }

    @Test
    void run_noPortfolio_abortsGracefully() {
        when(portfolioRepository.findAll()).thenReturn(List.of());

        orchestrator.run();

        verifyNoInteractions(assetRepository, halalScreenerAgent, marketDataClient);
    }

    @Test
    void run_assetProcessingException_skipsAssetContinuesLoop() {
        Asset asset1 = buildAsset("AAPL");
        Asset asset2 = buildAsset("MSFT");
        Portfolio portfolio = buildPortfolio();

        when(assetRepository.findByHalalScreening(HalalScreening.APPROVED)).thenReturn(List.of(asset1, asset2));
        when(portfolioRepository.findAll()).thenReturn(List.of(portfolio));
        when(halalScreenerAgent.analyze(eq("AAPL"), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("unexpected"));
        when(halalScreenerAgent.analyze(eq("MSFT"), anyString(), anyString(), anyString()))
                .thenReturn(new HalalReport(false, "Skipped"));

        orchestrator.run(); // should not throw

        verify(halalScreenerAgent, times(2)).analyze(anyString(), anyString(), anyString(), anyString());
    }
}
