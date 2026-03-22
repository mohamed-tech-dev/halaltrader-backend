# Phase 4 — Progression & Statistiques Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add two new dashboard pages — `/progression` (portfolio value chart with period filter and per-asset P&L) and `/statistiques` (monthly performance grid + AI records panel) — backed by a new `GET /api/stats` endpoint.

**Architecture:** New `StatsQueryService` aggregates trade data into monthly buckets, streaks, and per-asset totals; `StatsController` exposes it as `StatsDto`. Frontend consumes both the existing `/api/performance` and the new `/api/stats` via React Query hooks, filtering data client-side for the period selector.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Lombok, Mockito, MockMvc / React 19, TypeScript, Recharts, @tanstack/react-query, Tailwind CSS

**Repos:**
- Backend: `C:\Users\alkao\OneDrive\Bureau\perso\halaltrader-backend`
- Frontend: `C:\Users\alkao\OneDrive\Bureau\perso\halaltrader-frontend`

---

## File Map

### Backend (new files)
- `src/main/java/com/halaltrader/backend/dto/StatsDto.java` — DTO record with 5 inner records
- `src/main/java/com/halaltrader/backend/service/StatsQueryService.java` — aggregation logic
- `src/main/java/com/halaltrader/backend/controller/StatsController.java` — `GET /api/stats`
- `src/test/java/com/halaltrader/backend/service/StatsQueryServiceTest.java` — unit tests (Mockito)
- `src/test/java/com/halaltrader/backend/controller/StatsControllerTest.java` — controller slice test

### Frontend (new files)
- `src/api/stats.ts` — `useStats()` hook
- `src/features/progression/index.tsx` — page container, period state, data wiring
- `src/features/progression/PortfolioChart.tsx` — Recharts AreaChart
- `src/features/progression/PeriodSelector.tsx` — 4 filter buttons
- `src/features/progression/KpiSidebar.tsx` — 4 stacked KPI cards
- `src/features/progression/AssetPnlRow.tsx` — per-asset P&L cards
- `src/features/statistiques/index.tsx` — page container
- `src/features/statistiques/MonthlyGrid.tsx` — monthly tiles + bar chart
- `src/features/statistiques/RecordsPanel.tsx` — AI records rows

### Frontend (modified)
- `src/types/api.ts` — add `StatsDto`, `MonthlyEntry`, `Records`, `TradeRecord`, `DayRecord` types
- `src/api/mock-data.ts` — add `mockStats` export
- `src/api/client.ts` — add `/stats` branch in `mockFetch`
- `src/components/AppLayout.tsx` — add 2 nav items
- `src/router/index.tsx` — add 2 routes

---

## Task 1: StatsDto

**Files:**
- Create: `src/main/java/com/halaltrader/backend/dto/StatsDto.java`

- [ ] **Step 1: Create StatsDto.java**

```java
package com.halaltrader.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record StatsDto(
        List<MonthlyEntry> monthlyPerformance,
        Records records,
        List<AssetPnlEntry> perAssetPnl
) {
    public record MonthlyEntry(
            String month,       // "2026-01" format
            BigDecimal pnl,
            BigDecimal pctChange,
            int tradeCount
    ) {}

    public record Records(
            TradeRecord bestTrade,
            TradeRecord worstTrade,
            int maxWinStreak,
            int maxLossStreak,
            DayRecord bestDay
    ) {}

    public record TradeRecord(
            String symbol,
            String action,
            BigDecimal pnl,
            LocalDateTime executedAt
    ) {}

    public record DayRecord(
            String date,
            BigDecimal pnl
    ) {}

    public record AssetPnlEntry(
            String symbol,
            BigDecimal totalPnl
    ) {}
}
```

- [ ] **Step 2: Compile check**

```bash
cd C:\Users\alkao\OneDrive\Bureau\perso\halaltrader-backend
mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/halaltrader/backend/dto/StatsDto.java
git commit -m "feat: add StatsDto record with nested records"
```

---

## Task 2: StatsQueryService

**Files:**
- Create: `src/main/java/com/halaltrader/backend/service/StatsQueryService.java`

- [ ] **Step 1: Create StatsQueryService.java**

```java
package com.halaltrader.backend.service;

import com.halaltrader.backend.dto.StatsDto;
import com.halaltrader.backend.entity.Trade;
import com.halaltrader.backend.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsQueryService {

    @Value("${trading.simulation.initial-cash:100000}")
    private BigDecimal initialCash;

    private final PortfolioQueryService portfolioQueryService;
    private final TradeRepository tradeRepository;

    @Transactional(readOnly = true)
    public StatsDto getStats() {
        var portfolio = portfolioQueryService.getPortfolio();
        List<Trade> trades = tradeRepository.findByPortfolioOrderByExecutedAtAsc(portfolio);

        List<Trade> pnlTrades = trades.stream()
                .filter(t -> t.getSimulatedPnl() != null && t.getExecutedAt() != null)
                .toList();

        if (pnlTrades.isEmpty()) {
            return new StatsDto(
                    Collections.emptyList(),
                    new StatsDto.Records(null, null, 0, 0, null),
                    Collections.emptyList());
        }

        // ── Monthly performance ──────────────────────────────────────────────
        Map<YearMonth, List<Trade>> byMonth = pnlTrades.stream()
                .collect(Collectors.groupingBy(
                        t -> YearMonth.from(t.getExecutedAt()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<StatsDto.MonthlyEntry> monthlyPerf = new ArrayList<>();
        BigDecimal cumPnl = BigDecimal.ZERO;
        for (var entry : byMonth.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            BigDecimal monthPnl = entry.getValue().stream()
                    .map(Trade::getSimulatedPnl)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal base = initialCash.add(cumPnl);
            BigDecimal pctChange = base.compareTo(BigDecimal.ZERO) <= 0
                    ? BigDecimal.ZERO
                    : monthPnl.divide(base, 4, RoundingMode.HALF_UP)
                               .multiply(new BigDecimal("100"))
                               .setScale(2, RoundingMode.HALF_UP);
            monthlyPerf.add(new StatsDto.MonthlyEntry(
                    entry.getKey().toString(), monthPnl, pctChange, entry.getValue().size()));
            cumPnl = cumPnl.add(monthPnl);
        }

        // ── Records ─────────────────────────────────────────────────────────
        Trade best = pnlTrades.stream()
                .max(Comparator.comparing(Trade::getSimulatedPnl)).orElse(null);
        Trade worst = pnlTrades.stream()
                .min(Comparator.comparing(Trade::getSimulatedPnl)).orElse(null);

        int maxWin = 0, curWin = 0, maxLoss = 0, curLoss = 0;
        for (Trade t : pnlTrades) {
            if (t.getSimulatedPnl().compareTo(BigDecimal.ZERO) > 0) {
                curWin++; curLoss = 0;
                maxWin = Math.max(maxWin, curWin);
            } else {
                curLoss++; curWin = 0;
                maxLoss = Math.max(maxLoss, curLoss);
            }
        }

        Map<LocalDate, BigDecimal> byDay = new LinkedHashMap<>();
        pnlTrades.forEach(t -> byDay.merge(
                t.getExecutedAt().toLocalDate(), t.getSimulatedPnl(), BigDecimal::add));
        StatsDto.DayRecord bestDay = byDay.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> new StatsDto.DayRecord(e.getKey().toString(), e.getValue()))
                .orElse(null);

        StatsDto.TradeRecord bestRecord = best == null ? null
                : new StatsDto.TradeRecord(best.getAsset().getSymbol(),
                        best.getAction().name(), best.getSimulatedPnl(), best.getExecutedAt());
        StatsDto.TradeRecord worstRecord = worst == null ? null
                : new StatsDto.TradeRecord(worst.getAsset().getSymbol(),
                        worst.getAction().name(), worst.getSimulatedPnl(), worst.getExecutedAt());

        // ── Per-asset P&L ────────────────────────────────────────────────────
        Map<String, BigDecimal> pnlByAsset = new LinkedHashMap<>();
        pnlTrades.forEach(t -> pnlByAsset.merge(
                t.getAsset().getSymbol(), t.getSimulatedPnl(), BigDecimal::add));
        List<StatsDto.AssetPnlEntry> perAssetPnl = pnlByAsset.entrySet().stream()
                .map(e -> new StatsDto.AssetPnlEntry(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(StatsDto.AssetPnlEntry::totalPnl).reversed())
                .toList();

        return new StatsDto(
                monthlyPerf,
                new StatsDto.Records(bestRecord, worstRecord, maxWin, maxLoss, bestDay),
                perAssetPnl);
    }
}
```

- [ ] **Step 2: Compile check**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/halaltrader/backend/service/StatsQueryService.java
git commit -m "feat: add StatsQueryService with monthly, records and per-asset aggregation"
```

---

## Task 3: StatsQueryServiceTest

**Files:**
- Create: `src/test/java/com/halaltrader/backend/service/StatsQueryServiceTest.java`

- [ ] **Step 1: Create StatsQueryServiceTest.java**

```java
package com.halaltrader.backend.service;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatsQueryServiceTest {

    @Mock PortfolioQueryService portfolioQueryService;
    @Mock TradeRepository tradeRepository;
    @InjectMocks StatsQueryService statsQueryService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(statsQueryService, "initialCash", new BigDecimal("100000"));
    }

    private Trade buildTrade(String symbol, TradeAction action, BigDecimal pnl, LocalDateTime at) {
        Asset asset = mock(Asset.class);
        when(asset.getSymbol()).thenReturn(symbol);
        Trade t = new Trade();
        t.setAsset(asset);
        t.setAction(action);
        t.setSimulatedPnl(pnl);
        t.setExecutedAt(at);
        t.setQuantity(BigDecimal.ONE);
        t.setPrice(new BigDecimal("100"));
        t.setTotalAmount(new BigDecimal("100"));
        return t;
    }

    @Test
    void getStats_returnsCorrectAggregates() {
        Portfolio portfolio = mock(Portfolio.class);
        when(portfolioQueryService.getPortfolio()).thenReturn(portfolio);

        // 2 winners then 1 loser, all in January 2026
        Trade t1 = buildTrade("AAPL", TradeAction.BUY,  new BigDecimal("100"),
                LocalDateTime.of(2026, 1, 10, 10, 0));
        Trade t2 = buildTrade("AAPL", TradeAction.BUY,  new BigDecimal("200"),
                LocalDateTime.of(2026, 1, 15, 10, 0));
        Trade t3 = buildTrade("MSFT", TradeAction.SELL, new BigDecimal("-50"),
                LocalDateTime.of(2026, 1, 20, 10, 0));
        when(tradeRepository.findByPortfolioOrderByExecutedAtAsc(portfolio))
                .thenReturn(List.of(t1, t2, t3));

        var result = statsQueryService.getStats();

        // Monthly — 1 month, pnl = 250, tradeCount = 3
        assertEquals(1, result.monthlyPerformance().size());
        assertEquals(new BigDecimal("250"), result.monthlyPerformance().get(0).pnl());
        assertEquals(3, result.monthlyPerformance().get(0).tradeCount());

        // Records
        assertEquals(new BigDecimal("200"), result.records().bestTrade().pnl());
        assertEquals(new BigDecimal("-50"), result.records().worstTrade().pnl());
        assertEquals(2, result.records().maxWinStreak());
        assertEquals(1, result.records().maxLossStreak());

        // Per-asset — sorted descending by totalPnl
        assertEquals(2, result.perAssetPnl().size());
        assertEquals("AAPL", result.perAssetPnl().get(0).symbol());
        assertEquals(new BigDecimal("300"), result.perAssetPnl().get(0).totalPnl());
        assertEquals("MSFT", result.perAssetPnl().get(1).symbol());
        assertEquals(new BigDecimal("-50"), result.perAssetPnl().get(1).totalPnl());
    }

    @Test
    void getStats_emptyWhenNoTrades() {
        Portfolio portfolio = mock(Portfolio.class);
        when(portfolioQueryService.getPortfolio()).thenReturn(portfolio);
        when(tradeRepository.findByPortfolioOrderByExecutedAtAsc(portfolio))
                .thenReturn(List.of());

        var result = statsQueryService.getStats();

        assertTrue(result.monthlyPerformance().isEmpty());
        assertTrue(result.perAssetPnl().isEmpty());
        assertNull(result.records().bestTrade());
        assertNull(result.records().worstTrade());
        assertEquals(0, result.records().maxWinStreak());
    }
}
```

- [ ] **Step 2: Run the tests**

```bash
mvn test -Dtest=StatsQueryServiceTest
```
Expected: 2 tests pass, BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/halaltrader/backend/service/StatsQueryServiceTest.java
git commit -m "test: add StatsQueryServiceTest — monthly, records, per-asset"
```

---

## Task 4: StatsController + StatsControllerTest

**Files:**
- Create: `src/main/java/com/halaltrader/backend/controller/StatsController.java`
- Create: `src/test/java/com/halaltrader/backend/controller/StatsControllerTest.java`

- [ ] **Step 1: Create StatsController.java**

```java
package com.halaltrader.backend.controller;

import com.halaltrader.backend.dto.StatsDto;
import com.halaltrader.backend.service.StatsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsQueryService statsQueryService;

    @GetMapping
    public StatsDto getStats() {
        return statsQueryService.getStats();
    }
}
```

- [ ] **Step 2: Create StatsControllerTest.java**

```java
package com.halaltrader.backend.controller;

import com.halaltrader.backend.config.AnthropicProperties;
import com.halaltrader.backend.dto.StatsDto;
import com.halaltrader.backend.service.StatsQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StatsController.class)
class StatsControllerTest {

    @Autowired MockMvc mvc;
    @MockBean StatsQueryService statsQueryService;
    @MockBean AnthropicProperties anthropicProperties;

    @Test
    void getStats_returns200WithStructure() throws Exception {
        var dto = new StatsDto(
                Collections.emptyList(),
                new StatsDto.Records(null, null, 0, 0, null),
                Collections.emptyList());
        when(statsQueryService.getStats()).thenReturn(dto);

        mvc.perform(get("/api/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyPerformance").isArray())
                .andExpect(jsonPath("$.records").exists())
                .andExpect(jsonPath("$.perAssetPnl").isArray());
    }
}
```

- [ ] **Step 3: Run all backend tests**

```bash
mvn test
```
Expected: All tests pass (including new StatsControllerTest and StatsQueryServiceTest), BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/halaltrader/backend/controller/StatsController.java
git add src/test/java/com/halaltrader/backend/controller/StatsControllerTest.java
git commit -m "feat: add StatsController GET /api/stats with WebMvc test"
```

---

## Task 5: Frontend — Types, Mock Data, useStats() Hook

All commands run from: `C:\Users\alkao\OneDrive\Bureau\perso\halaltrader-frontend`

**Files:**
- Modify: `src/types/api.ts`
- Modify: `src/api/mock-data.ts`
- Modify: `src/api/client.ts`
- Create: `src/api/stats.ts`

- [ ] **Step 1: Add StatsDto types to src/types/api.ts**

Append after the `PagedResponse` interface (end of file):

```typescript
export interface MonthlyEntry {
  month: string       // "2026-01"
  pnl: number
  pctChange: number
  tradeCount: number
}

export interface TradeRecord {
  symbol: string
  action: string
  pnl: number
  executedAt: string
}

export interface DayRecord {
  date: string
  pnl: number
}

export interface Records {
  bestTrade: TradeRecord | null
  worstTrade: TradeRecord | null
  maxWinStreak: number
  maxLossStreak: number
  bestDay: DayRecord | null
}

export interface StatsDto {
  monthlyPerformance: MonthlyEntry[]
  records: Records
  perAssetPnl: AssetPnlEntry[]   // reuses existing AssetPnlEntry type
}
```

- [ ] **Step 2: Add mockStats to src/api/mock-data.ts**

Add the `StatsDto` import in the import block at the top (add `StatsDto` to the existing import list):

```typescript
import type {
  PortfolioSummary,
  Position,
  Trade,
  TradeDetail,
  Asset,
  Performance,
  PagedResponse,
  StatsDto,
} from '../types/api'
```

Then append at the end of the file:

```typescript
// ── Stats ─────────────────────────────────────────────────────────────────────

export const mockStats: StatsDto = {
  monthlyPerformance: [
    { month: '2026-01', pnl: 180,  pctChange: 0.18, tradeCount: 2 },
    { month: '2026-02', pnl: -120, pctChange: -0.12, tradeCount: 1 },
    { month: '2026-03', pnl: 805,  pctChange: 0.81, tradeCount: 7 },
  ],
  records: {
    bestTrade:  { symbol: 'META', action: 'SELL', pnl: 430, executedAt: '2026-03-14T09:30:00' },
    worstTrade: { symbol: 'NVDA', action: 'BUY',  pnl: -200, executedAt: '2026-03-12T15:10:00' },
    maxWinStreak: 4,
    maxLossStreak: 1,
    bestDay: { date: '2026-03-14', pnl: 430 },
  },
  perAssetPnl: [
    { symbol: 'META',  totalPnl: 430 },
    { symbol: 'AAPL',  totalPnl: 340 },
    { symbol: 'MSFT',  totalPnl: 400 },
    { symbol: 'AMZN',  totalPnl:  80 },
    { symbol: 'NVDA',  totalPnl: -200 },
  ],
}
```

- [ ] **Step 3: Add /stats handler to src/api/client.ts**

Add `mockStats` to the import at the top:

```typescript
import {
  mockPortfolio,
  mockPositions,
  mockTradeDetails,
  mockTradesPage,
  mockAssets,
  mockPerformance,
  mockStats,
} from './mock-data'
```

Add the `/stats` branch in `mockFetch` after the `/performance` line:

```typescript
if (path === '/stats')           return mockStats as T
```

- [ ] **Step 4: Create src/api/stats.ts**

```typescript
import { useQuery } from '@tanstack/react-query'
import { apiFetch } from './client'
import type { StatsDto } from '../types/api'

export function useStats() {
  return useQuery({
    queryKey: ['stats'],
    queryFn: () => apiFetch<StatsDto>('/stats'),
    staleTime: 30_000,
  })
}
```

- [ ] **Step 5: TypeScript check**

```bash
cd C:\Users\alkao\OneDrive\Bureau\perso\halaltrader-frontend
npm run build
```
Expected: no TypeScript errors, BUILD SUCCESS

- [ ] **Step 6: Commit (frontend repo)**

```bash
git add src/types/api.ts src/api/mock-data.ts src/api/client.ts src/api/stats.ts
git commit -m "feat: add StatsDto types, mockStats, useStats hook"
```

---

## Task 6: Progression Page

**Files (all in `src/features/progression/`):**
- Create: `index.tsx`
- Create: `PortfolioChart.tsx`
- Create: `PeriodSelector.tsx`
- Create: `KpiSidebar.tsx`
- Create: `AssetPnlRow.tsx`

- [ ] **Step 1: Create PeriodSelector.tsx**

```typescript
type Period = '7J' | '1M' | '3M' | 'Tout'

interface Props {
  period: Period
  onChange: (p: Period) => void
}

const PERIODS: Period[] = ['7J', '1M', '3M', 'Tout']

export type { Period }

export default function PeriodSelector({ period, onChange }: Props) {
  return (
    <div className="flex gap-1">
      {PERIODS.map(p => (
        <button
          key={p}
          onClick={() => onChange(p)}
          className={`px-3 py-1 text-xs rounded border transition-colors ${
            p === period
              ? 'bg-[#00d4aa22] border-[#00d4aa44] text-[#00d4aa]'
              : 'border-[#1e2d3d] text-[#3d5a6e] hover:text-[#e6edf3]'
          }`}
        >
          {p}
        </button>
      ))}
    </div>
  )
}
```

- [ ] **Step 2: Create PortfolioChart.tsx**

```typescript
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer,
} from 'recharts'

interface Props {
  data: { date: string; portfolioValue: number }[]
}

export default function PortfolioChart({ data }: Props) {
  const lastValue = data.at(-1)?.portfolioValue ?? 100_000
  const color = lastValue > 100_000 ? '#00d4aa' : '#ff4444'
  const gradientId = 'portfolioGradient'

  if (data.length === 0) {
    return (
      <div className="h-56 flex items-center justify-center text-[#3d5a6e] text-sm bg-[#161b22] rounded-lg border border-[#1e2d3d]">
        Aucune donnée pour cette période
      </div>
    )
  }

  return (
    <div className="bg-[#161b22] rounded-lg border border-[#1e2d3d] p-4">
      <ResponsiveContainer width="100%" height={220}>
        <AreaChart data={data} margin={{ top: 5, right: 15, bottom: 5, left: 15 }}>
          <defs>
            <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor={color} stopOpacity={0.15} />
              <stop offset="95%" stopColor={color} stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid strokeDasharray="3 3" stroke="#1e2d3d" vertical={false} />
          <XAxis dataKey="date" tick={{ fontSize: 10, fill: '#3d5a6e' }} tickLine={false} axisLine={false} />
          <YAxis
            tick={{ fontSize: 10, fill: '#3d5a6e' }}
            tickLine={false}
            axisLine={false}
            tickFormatter={(v: number) => `€${v.toLocaleString('fr-FR')}`}
          />
          <Tooltip
            contentStyle={{ background: '#161b22', border: '1px solid #1e2d3d', borderRadius: 6, fontSize: 12 }}
            labelStyle={{ color: '#e6edf3' }}
            formatter={(v: number) => [
              `€${v.toLocaleString('fr-FR', { minimumFractionDigits: 2 })}`,
              'Portfolio',
            ]}
          />
          <Area
            type="monotone"
            dataKey="portfolioValue"
            stroke={color}
            strokeWidth={2}
            fill={`url(#${gradientId})`}
            dot={false}
            activeDot={{ r: 4, fill: color }}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  )
}
```

- [ ] **Step 3: Create KpiSidebar.tsx**

```typescript
interface Props {
  totalValue: number
  gainAbs: number
  gainPct: number
  winRate: number
  totalTrades: number
}

function KpiCard({ label, value, color }: { label: string; value: string; color?: string }) {
  return (
    <div className="bg-[#161b22] border border-[#1e2d3d] rounded-lg p-4 flex-1">
      <div className="text-xs text-[#3d5a6e] uppercase tracking-wide mb-1">{label}</div>
      <div className={`text-lg font-bold ${color ?? 'text-[#e6edf3]'}`}>{value}</div>
    </div>
  )
}

export default function KpiSidebar({ totalValue, gainAbs, gainPct, winRate, totalTrades }: Props) {
  const fmt = (n: number) =>
    n.toLocaleString('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  const gainColor = gainAbs >= 0 ? 'text-[#00d4aa]' : 'text-[#ff4444]'
  const sign = gainAbs >= 0 ? '+' : ''

  return (
    <div className="flex flex-col gap-3 h-full">
      <KpiCard label="Capital total" value={`€${fmt(totalValue)}`} />
      <KpiCard
        label="Gain net"
        value={`${sign}€${fmt(gainAbs)} (${sign}${gainPct.toFixed(2)}%)`}
        color={gainColor}
      />
      <KpiCard label="Win rate" value={`${winRate.toFixed(1)}%`} />
      <KpiCard label="Nb trades" value={String(totalTrades)} />
    </div>
  )
}
```

- [ ] **Step 4: Create AssetPnlRow.tsx**

```typescript
import type { AssetPnlEntry } from '../../types/api'

interface Props {
  perAssetPnl: AssetPnlEntry[]
}

export default function AssetPnlRow({ perAssetPnl }: Props) {
  if (perAssetPnl.length === 0) return null

  return (
    <div className="grid grid-cols-5 gap-3">
      {perAssetPnl.map(e => {
        const isPos = e.totalPnl >= 0
        return (
          <div
            key={e.symbol}
            className={`rounded-lg border p-3 text-center ${
              isPos ? 'bg-[#00d4aa0d] border-[#00d4aa33]' : 'bg-[#ff44440d] border-[#ff444433]'
            }`}
          >
            <div className="text-xs text-[#3d5a6e] mb-1">{e.symbol}</div>
            <div className={`text-sm font-bold ${isPos ? 'text-[#00d4aa]' : 'text-[#ff4444]'}`}>
              {isPos ? '+' : ''}€
              {Math.abs(e.totalPnl).toLocaleString('fr-FR', {
                minimumFractionDigits: 0,
                maximumFractionDigits: 0,
              })}
            </div>
          </div>
        )
      })}
    </div>
  )
}
```

- [ ] **Step 5: Create progression/index.tsx**

```typescript
import { useState, useMemo } from 'react'
import { usePerformance } from '../../api/performance'
import { useStats } from '../../api/stats'
import type { DailyPnlEntry } from '../../types/api'
import PortfolioChart from './PortfolioChart'
import PeriodSelector from './PeriodSelector'
import type { Period } from './PeriodSelector'
import KpiSidebar from './KpiSidebar'
import AssetPnlRow from './AssetPnlRow'

function filterByPeriod(dailyPnl: DailyPnlEntry[], period: Period): DailyPnlEntry[] {
  if (period === 'Tout') return dailyPnl
  const days = period === '7J' ? 7 : period === '1M' ? 30 : 90
  const cutoff = new Date()
  cutoff.setDate(cutoff.getDate() - days)
  return dailyPnl.filter(e => new Date(e.date) >= cutoff)
}

export default function Progression() {
  const [period, setPeriod] = useState<Period>('Tout')
  const { data: perf, isLoading } = usePerformance()
  const { data: stats } = useStats()

  const chartData = useMemo(() => {
    const filtered = filterByPeriod(perf?.dailyPnl ?? [], period)
    return filtered.map(e => ({ date: e.date, portfolioValue: 100_000 + e.cumulativePnl }))
  }, [perf?.dailyPnl, period])

  if (isLoading) {
    return <div className="animate-pulse h-96 bg-slate-200 dark:bg-[#161b22] rounded-lg" />
  }

  const lastPnl = perf?.dailyPnl.at(-1)?.cumulativePnl ?? 0
  const totalValue = 100_000 + lastPnl
  const gainPct = (lastPnl / 100_000) * 100

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-slate-900 dark:text-[#e6edf3]">
          Progression du Portfolio
        </h1>
        <PeriodSelector period={period} onChange={setPeriod} />
      </div>

      <div className="grid grid-cols-3 gap-4">
        <div className="col-span-2">
          <PortfolioChart data={chartData} />
        </div>
        <KpiSidebar
          totalValue={totalValue}
          gainAbs={lastPnl}
          gainPct={gainPct}
          winRate={perf?.winRate ?? 0}
          totalTrades={perf?.totalTrades ?? 0}
        />
      </div>

      <AssetPnlRow perAssetPnl={stats?.perAssetPnl ?? []} />
    </div>
  )
}
```

- [ ] **Step 6: TypeScript check**

```bash
npm run build
```
Expected: no TypeScript errors

- [ ] **Step 7: Commit**

```bash
git add src/features/progression/
git commit -m "feat: add Progression page with PortfolioChart, PeriodSelector, KpiSidebar, AssetPnlRow"
```

---

## Task 7: Statistiques Page

**Files (all in `src/features/statistiques/`):**
- Create: `index.tsx`
- Create: `MonthlyGrid.tsx`
- Create: `RecordsPanel.tsx`

- [ ] **Step 1: Create MonthlyGrid.tsx**

```typescript
import {
  BarChart, Bar, Cell, XAxis, YAxis, Tooltip, ResponsiveContainer,
} from 'recharts'
import type { MonthlyEntry } from '../../types/api'

interface Props {
  monthlyPerformance: MonthlyEntry[]
}

function tileClass(pnl: number): string {
  if (pnl > 0) return 'bg-teal-900/40 border-teal-700 text-[#00d4aa]'
  if (pnl < 0) return 'bg-red-900/30 border-red-800 text-[#ff4444]'
  return 'bg-slate-800 border-slate-700 text-[#3d5a6e]'
}

export default function MonthlyGrid({ monthlyPerformance }: Props) {
  return (
    <div className="bg-[#161b22] rounded-lg border border-[#1e2d3d] p-4 space-y-4">
      <h2 className="text-sm font-semibold text-[#e6edf3]">Performance mensuelle</h2>

      {monthlyPerformance.length === 0 ? (
        <p className="text-sm text-[#3d5a6e]">Aucune donnée disponible</p>
      ) : (
        <>
          <div className="grid grid-cols-4 gap-2">
            {monthlyPerformance.map(m => (
              <div key={m.month} className={`rounded border p-2 text-center text-xs ${tileClass(m.pnl)}`}>
                <div className="text-[#3d5a6e]">{m.month}</div>
                <div className="font-bold">
                  {m.pnl >= 0 ? '+' : ''}{m.pctChange.toFixed(1)}%
                </div>
              </div>
            ))}
          </div>

          <ResponsiveContainer width="100%" height={120}>
            <BarChart data={monthlyPerformance} margin={{ top: 5, right: 5, bottom: 5, left: 5 }}>
              <XAxis dataKey="month" tick={{ fontSize: 9, fill: '#3d5a6e' }} tickLine={false} axisLine={false} />
              <YAxis hide />
              <Tooltip
                contentStyle={{ background: '#161b22', border: '1px solid #1e2d3d', borderRadius: 4, fontSize: 11 }}
                labelStyle={{ color: '#e6edf3' }}
                formatter={(v: number) => [`€${v.toFixed(0)}`, 'P&L']}
              />
              <Bar dataKey="pnl">
                {monthlyPerformance.map((m, i) => (
                  <Cell key={i} fill={m.pnl >= 0 ? '#00d4aa66' : '#ff444466'} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </>
      )}
    </div>
  )
}
```

- [ ] **Step 2: Create RecordsPanel.tsx**

```typescript
import type { Records } from '../../types/api'

interface Props {
  records: Records | undefined
}

function RecordRow({
  icon, label, value, color,
}: { icon: string; label: string; value: string; color: string }) {
  return (
    <div className="flex items-center justify-between py-3 border-b border-[#1e2d3d] last:border-0">
      <div className="flex items-center gap-2">
        <span>{icon}</span>
        <span className="text-sm text-[#3d5a6e]">{label}</span>
      </div>
      <span className={`text-sm font-bold ${color}`}>{value}</span>
    </div>
  )
}

function fmtPnl(n: number): string {
  const abs = Math.abs(n).toLocaleString('fr-FR', { minimumFractionDigits: 0, maximumFractionDigits: 0 })
  return `${n >= 0 ? '+' : '-'}€${abs}`
}

export default function RecordsPanel({ records }: Props) {
  return (
    <div className="bg-[#161b22] rounded-lg border border-[#1e2d3d] p-4">
      <h2 className="text-sm font-semibold text-[#e6edf3] mb-2">Records & Séries</h2>
      <RecordRow
        icon="🏆"
        label="Meilleur trade"
        value={records?.bestTrade ? `${records.bestTrade.symbol} ${fmtPnl(records.bestTrade.pnl)}` : '—'}
        color="text-[#00d4aa]"
      />
      <RecordRow
        icon="💀"
        label="Pire trade"
        value={records?.worstTrade ? `${records.worstTrade.symbol} ${fmtPnl(records.worstTrade.pnl)}` : '—'}
        color="text-[#ff4444]"
      />
      <RecordRow
        icon="🔥"
        label="Série de gains max"
        value={records ? `${records.maxWinStreak} de suite` : '—'}
        color="text-[#e6edf3]"
      />
      <RecordRow
        icon="📅"
        label="Meilleur jour"
        value={records?.bestDay ? `${records.bestDay.date} ${fmtPnl(records.bestDay.pnl)}` : '—'}
        color="text-[#00d4aa]"
      />
    </div>
  )
}
```

- [ ] **Step 3: Create statistiques/index.tsx**

```typescript
import { useStats } from '../../api/stats'
import MonthlyGrid from './MonthlyGrid'
import RecordsPanel from './RecordsPanel'

export default function Statistiques() {
  const { data: stats, isLoading } = useStats()

  if (isLoading) {
    return <div className="animate-pulse h-96 bg-slate-200 dark:bg-[#161b22] rounded-lg" />
  }

  return (
    <div className="space-y-6">
      <h1 className="text-xl font-semibold text-slate-900 dark:text-[#e6edf3]">Statistiques IA</h1>
      <div className="grid grid-cols-2 gap-4">
        <MonthlyGrid monthlyPerformance={stats?.monthlyPerformance ?? []} />
        <RecordsPanel records={stats?.records} />
      </div>
    </div>
  )
}
```

- [ ] **Step 4: TypeScript check**

```bash
npm run build
```
Expected: no TypeScript errors

- [ ] **Step 5: Commit**

```bash
git add src/features/statistiques/
git commit -m "feat: add Statistiques page with MonthlyGrid and RecordsPanel"
```

---

## Task 8: Navigation + Router Wiring

**Files:**
- Modify: `src/components/AppLayout.tsx`
- Modify: `src/router/index.tsx`

- [ ] **Step 1: Update AppLayout.tsx**

Add `LineChart` and `PieChart` to the lucide-react import:

```typescript
import {
  LayoutDashboard,
  TrendingUp,
  ArrowLeftRight,
  Brain,
  Star,
  BarChart2,
  Settings,
  LineChart,
  PieChart,
} from 'lucide-react'
```

Add two entries to the `navItems` array, after the `'/performance'` entry:

```typescript
{ to: '/progression',   icon: LineChart,  label: 'Progression',   end: false },
{ to: '/statistiques',  icon: PieChart,   label: 'Statistiques',  end: false },
```

- [ ] **Step 2: Update router/index.tsx**

Add imports:

```typescript
import Progression from '../features/progression'
import Statistiques from '../features/statistiques'
```

Add routes in the `children` array, after the `'/performance'` route:

```typescript
{ path: '/progression',  element: <Progression /> },
{ path: '/statistiques', element: <Statistiques /> },
```

- [ ] **Step 3: Final build**

```bash
npm run build
```
Expected: no errors, dist output created

- [ ] **Step 4: Smoke test with mock mode (optional)**

```bash
VITE_MOCK=true npm run dev
```
Navigate to `http://localhost:5173/progression` and `http://localhost:5173/statistiques` — both pages should render with mock data.

- [ ] **Step 5: Commit**

```bash
git add src/components/AppLayout.tsx src/router/index.tsx
git commit -m "feat: wire Progression and Statistiques pages into nav and router"
```

---

## Final Backend Verification

- [ ] **Run full test suite**

```bash
cd C:\Users\alkao\OneDrive\Bureau\perso\halaltrader-backend
mvn test
```
Expected: all tests pass

- [ ] **Commit tag**

```bash
git tag phase-4-complete
```
