package com.halaltrader.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.halaltrader.backend.agent.*;
import com.halaltrader.backend.client.MarketDataClient;
import com.halaltrader.backend.dto.*;
import com.halaltrader.backend.entity.*;
import com.halaltrader.backend.repository.*;
import com.halaltrader.backend.websocket.TradingEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TradingOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(TradingOrchestrator.class);

    private final HalalScreenerAgent halalScreenerAgent;
    private final MarketAnalystAgent marketAnalystAgent;
    private final RiskManagerAgent riskManagerAgent;
    private final DecisionAgent decisionAgent;
    private final PerformanceAgent performanceAgent;
    private final MarketDataClient marketDataClient;
    private final AssetRepository assetRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioPositionRepository positionRepository;
    private final TradeExecutionService tradeExecutionService;
    private final TradingEventPublisher tradingEventPublisher;
    private final ObjectMapper objectMapper;

    public TradingOrchestrator(HalalScreenerAgent halalScreenerAgent,
                               MarketAnalystAgent marketAnalystAgent,
                               RiskManagerAgent riskManagerAgent,
                               DecisionAgent decisionAgent,
                               PerformanceAgent performanceAgent,
                               MarketDataClient marketDataClient,
                               AssetRepository assetRepository,
                               PortfolioRepository portfolioRepository,
                               PortfolioPositionRepository positionRepository,
                               TradeExecutionService tradeExecutionService,
                               TradingEventPublisher tradingEventPublisher,
                               ObjectMapper objectMapper) {
        this.halalScreenerAgent = halalScreenerAgent;
        this.marketAnalystAgent = marketAnalystAgent;
        this.riskManagerAgent = riskManagerAgent;
        this.decisionAgent = decisionAgent;
        this.performanceAgent = performanceAgent;
        this.marketDataClient = marketDataClient;
        this.assetRepository = assetRepository;
        this.portfolioRepository = portfolioRepository;
        this.positionRepository = positionRepository;
        this.tradeExecutionService = tradeExecutionService;
        this.tradingEventPublisher = tradingEventPublisher;
        this.objectMapper = objectMapper;
    }

    public void run() {
        List<Portfolio> portfolios = portfolioRepository.findAll();

        if (portfolios.isEmpty()) {
            log.warn("[TradingOrchestrator] No portfolio found — aborting");
            return;
        }
        Portfolio portfolio = portfolios.get(0);

        List<Asset> assets = assetRepository.findByHalalScreening(HalalScreening.APPROVED);
        log.info("[TradingOrchestrator] Starting cycle — {} assets, cash={}", assets.size(), portfolio.getCashBalance());

        int executed = 0;
        for (Asset asset : assets) {
            try {
                if (processAsset(portfolio, asset)) {
                    executed++;
                }
            } catch (Exception e) {
                log.error("[TradingOrchestrator] Unexpected error for {} — skipping: {}", asset.getSymbol(), e.getMessage());
            }
        }
        tradingEventPublisher.publishCycleComplete(executed);
        log.info("[TradingOrchestrator] Cycle complete");
    }

    private boolean processAsset(Portfolio portfolio, Asset asset) {
        String symbol = asset.getSymbol();

        HalalReport halal = halalScreenerAgent.analyze(
                symbol, asset.getName(), asset.getAssetType().name(), asset.getSector());
        if (!halal.approved()) {
            log.info("[TradingOrchestrator] {} rejected by HalalScreener — stopping pipeline", symbol);
            return false;
        }

        MarketDataDto marketData = marketDataClient.getMarketData(symbol);
        MarketAnalysisReport analysis = marketAnalystAgent.analyze(marketData);

        Optional<PortfolioPosition> position = positionRepository.findByPortfolioAndAsset(portfolio, asset);
        BigDecimal currentQty = position.map(PortfolioPosition::getQuantity).orElse(BigDecimal.ZERO);
        BigDecimal avgBuyPrice = position.map(PortfolioPosition::getAverageBuyPrice).orElse(BigDecimal.ZERO);

        RiskReport risk = riskManagerAgent.assess(
                symbol, portfolio.getCashBalance(), currentQty, BigDecimal.valueOf(marketData.price()), analysis);

        TradeDecision decision = decisionAgent.decide(symbol, halal, analysis, risk, currentQty);

        BigDecimal pnl = performanceAgent.compute(
                decision.action(), decision.quantity(), BigDecimal.valueOf(marketData.price()), avgBuyPrice);

        String auditJson = buildAuditJson(halal, analysis, risk, decision);

        tradeExecutionService.execute(portfolio, asset, decision,
                BigDecimal.valueOf(marketData.price()), pnl, auditJson);

        if (decision.action() != TradeAction.HOLD) {
            tradingEventPublisher.publishTradeExecuted(
                    symbol, decision.action().name(), decision.quantity(),
                    BigDecimal.valueOf(marketData.price()), decision.reasoning());
            return true;
        }
        return false;
    }

    private String buildAuditJson(HalalReport halal, MarketAnalysisReport analysis,
                                  RiskReport risk, TradeDecision decision) {
        try {
            Map<String, Object> audit = new LinkedHashMap<>();
            audit.put("halal", Map.of("approved", halal.approved(), "reason", halal.reason()));
            audit.put("analysis", Map.of("trend", analysis.trend(), "momentum", analysis.momentum(), "summary", analysis.summary()));
            audit.put("risk", Map.of("level", risk.level(), "maxQuantity", risk.maxQuantity(), "reason", risk.reason()));
            audit.put("decision", Map.of("action", decision.action().name(), "quantity", decision.quantity(), "reasoning", decision.reasoning()));
            return objectMapper.writeValueAsString(audit);
        } catch (Exception e) {
            return "{}";
        }
    }
}
