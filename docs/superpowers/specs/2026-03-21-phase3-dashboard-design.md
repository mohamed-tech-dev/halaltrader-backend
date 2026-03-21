# Phase 3 — Dashboard HalalTrader Design Spec

## Goal

Expose les données du backend via une REST API et construire un dashboard React soigné pour visualiser le portfolio, les trades, les raisonnements IA et la performance du pipeline d'agents.

## Architecture

Deux repos séparés :
- `halaltrader-backend` — Spring Boot (existant) : ajout REST API + CORS
- `halaltrader-frontend` — nouveau repo React

---

## Backend — REST API

### Nouveaux composants

**`CorsConfig.java`**
- Autorise `http://localhost:5173` en dev (configurable via `app.cors.allowed-origins`)

**DTOs de réponse** (records Java, jamais les entités JPA directement)
- `PortfolioSummaryDto` — id, name, cashBalance, totalValue, totalPnl, totalPnlPct, positionCount
  - `totalValue` = cashBalance + sum(positions × avgPrice) — utilise le prix moyen en DB, pas d'appel live au market-data service
- `PositionDto` — symbol, name, assetType, quantity, avgPrice, currentPrice, value, pnl, pnlPct
- `TradeDto` — id, symbol, action, quantity, price, totalAmount, simulatedPnl, executedAt
- `TradeDetailDto` — tout TradeDto + aiReasoning (JSON parsé en objet) + technicalData
- `AssetDto` — id, symbol, name, assetType, halalScreening, halalReason, sector
- `PerformanceDto` — dailyPnl (liste de {date, cumulativePnl}), winRate, totalTrades, bestAsset, worstAsset, halalApprovalRate

**Services de lecture**
- `PortfolioQueryService` — calcule totalValue = cashBalance + sum(positions * prix actuel)
- `PerformanceQueryService` — construit la courbe P&L cumulatif depuis le premier trade

**Controllers**
- `PortfolioController` — `GET /api/portfolio`, `GET /api/portfolio/positions`
- `TradeController` — `GET /api/trades?page=0&size=20&sort=executedAt,desc`, `GET /api/trades/{id}`
- `AssetController` — `GET /api/assets`
- `PerformanceController` — `GET /api/performance`

### Endpoints détaillés

```
GET /api/portfolio
  → PortfolioSummaryDto

GET /api/portfolio/positions
  → List<PositionDto>

GET /api/trades?page=0&size=20&sort=executedAt,desc
  → Page<TradeDto>

GET /api/trades/{id}
  → TradeDetailDto

GET /api/assets
  → List<AssetDto>

GET /api/performance
  → PerformanceDto
    - dailyPnl: [{date: "YYYY-MM-DD", cumulativePnl: BigDecimal}] — depuis le premier trade, trié par date ASC
    - winRate: % trades où simulatedPnl > 0
    - totalTrades: nombre total
    - bestAsset: {symbol, totalPnl}
    - worstAsset: {symbol, totalPnl}
    - halalApprovalRate: % actifs avec halalScreening=APPROVED
    - lastCycleAt: dernière exécution du scheduler
    - nextCycleAt: prochaine exécution estimée
```

---

## Frontend — halaltrader-frontend

### Stack

- **React 18** + **TypeScript**
- **Vite** — build tooling
- **TanStack Query v5** — server state, cache, auto-refetch
- **React Router v6** — navigation SPA
- **Recharts** — graphiques (line chart P&L, donut chart halal rate)
- **Tailwind CSS v3** — styling avec dark/light mode via `class` strategy
- **Lucide React** — icônes

### Structure

```
halaltrader-frontend/
├── src/
│   ├── api/
│   │   ├── portfolio.ts       ← usePortfolio(), usePositions()
│   │   ├── trades.ts          ← useTrades(page), useTradeDetail(id)
│   │   ├── assets.ts          ← useAssets()
│   │   └── performance.ts     ← usePerformance()
│   ├── components/
│   │   ├── Sidebar.tsx
│   │   ├── Card.tsx
│   │   ├── Badge.tsx          ← BUY/SELL/HOLD, APPROVED/REJECTED
│   │   ├── DataTable.tsx
│   │   ├── PnlChart.tsx       ← Recharts LineChart cumulatif
│   │   ├── HalalDonut.tsx     ← Recharts PieChart taux approbation
│   │   └── ThemeToggle.tsx
│   ├── features/
│   │   ├── overview/          ← KPI cards + mini graphique + derniers trades
│   │   ├── positions/         ← tableau positions actives
│   │   ├── trades/            ← tableau paginé + modal détail
│   │   ├── ai-reasoning/      ← liste trades avec agents expandables
│   │   ├── halal-screening/   ← liste actifs + statut + raison
│   │   ├── performance/       ← graphique P&L cumulatif + stats + donut
│   │   └── settings/          ← toggle theme + info scheduler + actifs
│   ├── theme/
│   │   └── index.ts           ← tokens dark/light, persisté localStorage
│   └── router/
│       └── index.tsx          ← routes React Router v6
├── tailwind.config.ts
├── vite.config.ts
└── package.json
```

### Thème

- **Dark Pro** par défaut : bg `#0f1117`, accents `#00d4aa`, texte `#e6edf3`
- **Clean Light** : bg `#f8fafc`, accents `#1e3a5f`, texte `#0f172a`
- Persisté dans `localStorage` via `ThemeProvider`
- Toggle dans la page Réglages

### Pages et données

| Page | Hook | Refetch |
|------|------|---------|
| Vue d'ensemble | usePortfolio + usePositions + useTrades(page=0,size=5) | 30s |
| Positions | usePositions | 30s |
| Trades | useTrades(page, size) | manuel |
| Raisonnements IA | useTrades + useTradeDetail (lazy) | manuel |
| Screening Halal | useAssets | manuel |
| Performance | usePerformance | 30s |
| Réglages | — | — |

### Page Performance (détail)

Contient :
1. **Graphique P&L cumulatif** — LineChart Recharts, axe X = date, axe Y = P&L en €. Montre si l'IA gagne ou perd depuis le début.
2. **Win rate** — % trades profitables avec barre de progression
3. **Meilleur actif** — symbole + P&L total en vert
4. **Pire actif** — symbole + P&L total en rouge
5. **Donut chart** — taux d'approbation halal (approuvé vs rejeté)
6. **Dernière exécution** — timestamp du dernier cycle scheduler
7. **Nombre total de trades** — depuis le début

---

## Règles de qualité

- Pas d'entités JPA exposées directement en JSON
- Tous les DTOs sont des records Java immuables
- TanStack Query gère tous les états loading/error côté frontend — pas de `useState` pour les données serveur
- Le thème est le seul état global (Context) — tout le reste via TanStack Query
- Tailwind uniquement pour le styling — pas de CSS custom sauf cas exceptionnel

---

## Ce qui ne change pas

- La logique de trading (agents, scheduler, orchestrator) — Phase 2 intacte
- Le schéma DB — aucune migration Flyway nécessaire
- La DB seed (assets + portfolio) — déjà en place
