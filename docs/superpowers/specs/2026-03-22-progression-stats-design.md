# Phase 4 — Pages Progression & Statistiques

## Goal

Ajouter deux nouvelles pages au dashboard HalalTrader :
1. **Progression** — graphique de la valeur du portfolio du début à maintenant, avec sélecteur de période et P&L par actif.
2. **Statistiques** — performance mensuelle (tuiles colorées + bar chart) et records & séries de l'IA.

Les pages existantes (Performance, Vue d'ensemble, etc.) ne sont pas modifiées.

---

## Architecture

```
Backend
  └── GET /api/stats  (nouveau)
        └── StatsController → StatsQueryService
              └── PortfolioQueryService (pour getPortfolio())
              └── TradeRepository (findByPortfolioOrderByExecutedAtAsc)
        └── Retourne : StatsDto

Frontend
  ├── /progression  — ProgressionPage
  │     └── usePerformance() (existant /api/performance) + useStats() pour perAssetPnl
  │     └── Filtre période côté client (7J / 1M / 3M / Tout)
  │     └── Composants : PortfolioChart, PeriodSelector, KpiSidebar, AssetPnlRow
  └── /statistiques  — StatisticsPage
        └── useStats() → /api/stats
        └── Composants : MonthlyGrid, RecordsPanel
```

---

## Backend

### Nouveau endpoint

**`GET /api/stats`** → `StatsDto`

```java
public record StatsDto(
    List<MonthlyEntry> monthlyPerformance,
    Records records,
    List<AssetPnlEntry> perAssetPnl       // P&L total par actif, tous trades confondus
) {
    public record MonthlyEntry(
        String month,          // format "2026-01" (YearMonth.toString())
        BigDecimal pnl,        // P&L net du mois (sum simulatedPnl)
        BigDecimal pctChange,  // % gain/perte ce mois (voir formule ci-dessous)
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
        String symbol,         // trade.getAsset().getSymbol() — accès dans @Transactional
        String action,         // trade.getAction().name()
        BigDecimal pnl,        // trade.getSimulatedPnl()
        LocalDateTime executedAt
    ) {}

    public record DayRecord(
        String date,           // ISO date string, ex: "2026-03-15"
        BigDecimal pnl         // sum(simulatedPnl) ce jour
    ) {}

    public record AssetPnlEntry(
        String symbol,
        BigDecimal totalPnl    // sum(simulatedPnl) pour cet actif, tous trades
    ) {}
}
```

### StatsQueryService

```java
@Service
@RequiredArgsConstructor
public class StatsQueryService {

    @Value("${trading.simulation.initial-cash:100000}")
    private BigDecimal initialCash;

    private final PortfolioQueryService portfolioQueryService;
    private final TradeRepository tradeRepository;

    @Transactional(readOnly = true)   // REQUIS — accès lazy à trade.getAsset().getSymbol()
    public StatsDto getStats() { ... }
}
```

**Monthly performance :**
- Charger `tradeRepository.findByPortfolioOrderByExecutedAtAsc(portfolio)` (méthode existante)
- Filtrer les trades avec `simulatedPnl != null`
- Grouper par `YearMonth.from(trade.getExecutedAt())`
- Pour chaque mois trié : `pnl = sum(simulatedPnl)`, `tradeCount = count`
- `pctChange` : calculer `cumulativePnlAvantCeMois` (somme des pnl des mois précédents), puis `pctChange = pnl / (initialCash + cumulativePnlAvantCeMois) * 100` arrondi à 2 décimales. Si dénominateur ≤ 0 → 0.

**Records :**
- `bestTrade` / `worstTrade` : `max/min(simulatedPnl)` parmi les trades avec pnl non null
- `maxWinStreak` / `maxLossStreak` : parcours séquentiel des trades ordonnés par `executedAt`, compter les séries consécutives positives/négatives
- `bestDay` : grouper par `trade.getExecutedAt().toLocalDate()`, trouver le jour avec le plus grand `sum(simulatedPnl)`, retourner `DayRecord(date.toString(), pnl)`

**Per-asset P&L :**
- Grouper par `trade.getAsset().getSymbol()`, `sum(simulatedPnl)` par actif
- Trier par `totalPnl` décroissant

**Edge cases :**
- Si aucun trade : retourner `StatsDto(emptyList, Records(null, null, 0, 0, null), emptyList)`
- Si `bestDay` n'existe pas (aucun trade avec pnl) : `null`

### Nouveaux fichiers backend

- `src/main/java/com/halaltrader/backend/dto/StatsDto.java`
- `src/main/java/com/halaltrader/backend/service/StatsQueryService.java`
- `src/main/java/com/halaltrader/backend/controller/StatsController.java`
- `src/test/java/com/halaltrader/backend/controller/StatsControllerTest.java`
- `src/test/java/com/halaltrader/backend/service/StatsQueryServiceTest.java`

### StatsController

```java
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

### Tests backend

**StatsControllerTest :**
```java
@WebMvcTest(StatsController.class)
class StatsControllerTest {
    @Autowired MockMvc mvc;
    @MockBean StatsQueryService statsQueryService;
    @MockBean AnthropicProperties anthropicProperties; // REQUIS — pattern existant du projet
    ...
}
```
Cas : `getStats_returns200WithStructure` — mock retourne un `StatsDto` minimal, vérifie status 200 + champ `monthlyPerformance` présent.

**StatsQueryServiceTest :**
- Fixtures : 3 trades ordonnés par date, 2 gagnants (+100, +200) puis 1 perdant (-50). Actifs AAPL (2 trades) et MSFT (1 trade).
- Assertions :
  - `monthlyPerformance` : 1 ou 2 mois selon les dates, pnl correct
  - `bestTrade.pnl = 200`, `worstTrade.pnl = -50`
  - `maxWinStreak = 2`, `maxLossStreak = 1`
  - `perAssetPnl` : AAPL = +300, MSFT = -50

---

## Frontend

### Hook `useStats()`

Fichier : `src/api/stats.ts`

```typescript
export function useStats() {
  return useQuery({
    queryKey: ['stats'],
    queryFn: () => apiFetch<StatsDto>('/stats'),
    staleTime: 30_000,
  })
}
```

Ajouter les types `StatsDto`, `MonthlyEntry`, `Records`, `TradeRecord`, `DayRecord` dans `src/types/api.ts`. **Ne pas** redéclarer `AssetPnlEntry` — ce type existe déjà dans `src/types/api.ts` (Phase 3A, `PerformanceDto`) avec les mêmes champs `{ symbol: string, totalPnl: number }`. Utiliser le type existant pour `StatsDto.perAssetPnl`.

**Mock support :** Ajouter dans `src/api/mock-data.ts` un objet `mockStats: StatsDto` minimal (ex : `monthlyPerformance: []`, `records: { bestTrade: null, worstTrade: null, maxWinStreak: 0, maxLossStreak: 0, bestDay: null }`, `perAssetPnl: []`). Ajouter dans `src/api/client.ts` la branche `if (path === '/stats') return mockStats as T` dans `mockFetch`, après les entrées existantes.

### Page Progression (`/progression`)

**Données :**
- `usePerformance()` — deux usages :
  - `data.dailyPnl[]` : série temporelle. Chaque entrée `cumulativePnl` est **déjà le cumul**. Conversion : `portfolioValue = 100_000 + entry.cumulativePnl`.
  - `data.winRate` (number) et `data.totalTrades` (number) : champs top-level du type `Performance`, utilisés directement pour les props `winRate` et `totalTrades` de `KpiSidebar`.
- `useStats()` — pour `perAssetPnl[]` (P&L total par actif)

**Filtrage par période (côté client) :**
- 7J = garder les entrées des 7 derniers jours calendaires
- 1M = garder les entrées des 30 derniers jours
- 3M = garder les entrées des 90 derniers jours
- Tout = toutes les entrées

**Layout :**

```
[Titre] Progression du Portfolio     [7J] [1M] [3M] [Tout]

+----------------------------------+  +------------------+
|  PortfolioChart                  |  | KpiSidebar       |
|  - Recharts AreaChart            |  | - Capital total  |
|  - dataKey="portfolioValue"      |  | - Gain net + %   |
|  - dégradé vert si gain, rouge   |  | - Win rate       |
|    si perte (selon lastValue)    |  | - Nb trades      |
|  - Tooltip : "€X,XXX"           |  |                  |
+----------------------------------+  +------------------+

+-------+-------+-------+-------+-------+
| NVDA  | AAPL  | GLD   |ISWD.L | MSFT  |  ← AssetPnlRow
|+€2180 |+€1240 | +€890 | +€120 | -€190 |
+-------+-------+-------+-------+-------+
```

**Composants :**
- `src/features/progression/index.tsx` — page, gère l'état `period: '7J'|'1M'|'3M'|'Tout'`, filtre les données, passe aux composants
- `src/features/progression/PortfolioChart.tsx` — Recharts `AreaChart` avec `Area` (fill dégradé) et `Line`. Les données reçues sont `{ date: string, portfolioValue: number }[]`. Couleur : `#00d4aa` si dernière valeur > 100000, sinon `#ff4444`.
- `src/features/progression/PeriodSelector.tsx` — 4 boutons (7J/1M/3M/Tout), variante active en teal, inactif en gris
- `src/features/progression/KpiSidebar.tsx` — 4 cards empilées, reçoit `{ totalValue, gainAbs, gainPct, winRate, totalTrades }` en props
- `src/features/progression/AssetPnlRow.tsx` — grille de cards par actif depuis `perAssetPnl[]`, couleur teal si positif, rouge si négatif

### Page Statistiques (`/statistiques`)

**Layout :**

```
[Titre] Statistiques IA

+---------------------------+  +---------------------------+
| MonthlyGrid               |  | RecordsPanel              |
| - Tuiles colorées / mois  |  | - 🏆 Meilleur trade        |
| - Vert intensité = gain   |  | - 💀 Pire trade            |
| - Rouge = perte           |  | - 🔥 Série de gains max    |
| - Bar chart en dessous    |  | - 📅 Meilleur jour         |
+---------------------------+  +---------------------------+
```

**Composants :**
- `src/features/statistiques/index.tsx` — page principale, appelle `useStats()`
- `src/features/statistiques/MonthlyGrid.tsx` — reçoit `monthlyPerformance[]`, affiche tuiles + Recharts `BarChart`
- `src/features/statistiques/RecordsPanel.tsx` — reçoit `records`, 4 lignes avec icône + label + valeur colorée. Gère les `null` (affiche "—" si pas de données)

**Couleurs des tuiles mensuelles :**
- `pnl > 0` : `bg-teal-900/40 border-teal-700` intensité proportionnelle à `pnl`
- `pnl < 0` : `bg-red-900/30 border-red-800`
- `pnl === 0` : `bg-slate-800`

### Navigation

`src/components/AppLayout.tsx` — ajouter dans `navItems` :
```typescript
import { LineChart, PieChart } from 'lucide-react'
// ...
{ to: '/progression', icon: LineChart, label: 'Progression', end: false },
{ to: '/statistiques', icon: PieChart, label: 'Statistiques', end: false },
```

`src/router/index.tsx` — ajouter :
```typescript
import Progression from '../features/progression'
import Statistiques from '../features/statistiques'
// ...
{ path: '/progression', element: <Progression /> },
{ path: '/statistiques', element: <Statistiques /> },
```

---

## Ce qui ne change pas

- Page Performance — intacte
- Endpoint `/api/performance` — inchangé (pas d'extension)
- DB schema — aucune migration
- Agents, orchestrator, WebSocket — intacts

---

## Tests

### Backend
- `StatsControllerTest` : `@WebMvcTest` + `@MockBean AnthropicProperties` (pattern existant) + `@MockBean StatsQueryService`. Vérifie 200 + structure JSON.
- `StatsQueryServiceTest` : `@ExtendWith(MockitoExtension)`, mock `PortfolioQueryService` + mock `TradeRepository`. Fixtures : 3 trades avec dates, actifs, pnl. Vérifie monthly pnl, bestTrade, maxWinStreak, perAssetPnl.

### Frontend
Pas de tests unitaires frontend (cohérent avec l'existant).
