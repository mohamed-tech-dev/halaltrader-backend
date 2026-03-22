package com.halaltrader.backend.service;

import com.halaltrader.backend.dto.StatsDto;
import com.halaltrader.backend.entity.Asset;
import com.halaltrader.backend.entity.Portfolio;
import com.halaltrader.backend.entity.Trade;
import com.halaltrader.backend.entity.TradeAction;
import com.halaltrader.backend.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsQueryServiceTest {

    @InjectMocks
    private StatsQueryService service;

    @Mock
    private PortfolioQueryService portfolioQueryService;

    @Mock
    private TradeRepository tradeRepository;

    private List<Trade> fixture;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "initialCash", new BigDecimal("100000"));

        Trade t1 = buildTrade("AAPL", "BUY",  new BigDecimal("100"),  LocalDateTime.of(2026, 1, 15, 10, 0));
        Trade t2 = buildTrade("AAPL", "BUY",  new BigDecimal("200"),  LocalDateTime.of(2026, 1, 20, 10, 0));
        Trade t3 = buildTrade("MSFT", "SELL", new BigDecimal("-50"),  LocalDateTime.of(2026, 1, 25, 10, 0));

        fixture = List.of(t1, t2, t3);

        Portfolio portfolio = mock(Portfolio.class);
        when(portfolioQueryService.getPortfolio()).thenReturn(portfolio);
        when(tradeRepository.findByPortfolioOrderByExecutedAtAsc(any())).thenReturn(fixture);
    }

    private Trade buildTrade(String symbol, String action, BigDecimal pnl, LocalDateTime executedAt) {
        Asset asset = mock(Asset.class);
        when(asset.getSymbol()).thenReturn(symbol);

        Trade trade = mock(Trade.class);
        when(trade.getAsset()).thenReturn(asset);
        when(trade.getAction()).thenReturn(TradeAction.valueOf(action));
        when(trade.getSimulatedPnl()).thenReturn(pnl);
        when(trade.getExecutedAt()).thenReturn(executedAt);

        return trade;
    }

    @Test
    void getStats_returnsCorrectMonthlyPerformance() {
        StatsDto result = service.getStats();

        assertThat(result.monthlyPerformance()).hasSize(1);
        assertThat(result.monthlyPerformance().get(0).month()).isEqualTo("2026-01");
        assertThat(result.monthlyPerformance().get(0).pnl().compareTo(new BigDecimal("250"))).isZero();
        assertThat(result.monthlyPerformance().get(0).tradeCount()).isEqualTo(3);
    }

    @Test
    void getStats_returnsCorrectRecordsAndAssets() {
        StatsDto result = service.getStats();

        assertThat(result.records().bestTrade().pnl().compareTo(new BigDecimal("200"))).isZero();
        assertThat(result.records().worstTrade().pnl().compareTo(new BigDecimal("-50"))).isZero();
        assertThat(result.records().maxWinStreak()).isEqualTo(2);
        assertThat(result.records().maxLossStreak()).isEqualTo(1);

        assertThat(result.records().bestDay()).isNotNull();
        assertThat(result.records().bestDay().date()).isEqualTo("2026-01-20");
        assertThat(result.records().bestDay().pnl().compareTo(new BigDecimal("200"))).isZero();

        assertThat(result.perAssetPnl()).hasSize(2);
        assertThat(result.perAssetPnl().get(0).symbol()).isEqualTo("AAPL");
        assertThat(result.perAssetPnl().get(0).totalPnl().compareTo(new BigDecimal("300"))).isZero();
        assertThat(result.perAssetPnl().get(1).symbol()).isEqualTo("MSFT");
        assertThat(result.perAssetPnl().get(1).totalPnl().compareTo(new BigDecimal("-50"))).isZero();
    }
}
