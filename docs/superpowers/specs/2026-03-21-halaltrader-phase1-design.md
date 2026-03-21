# HalalTrader Phase 1 — Foundation Design Spec

**Date:** 2026-03-21
**Status:** Approved

## Context

Simulated halal trading platform with AI agents. Phase 1 establishes the foundation: Maven config, JPA entities, Flyway migrations, Spring Data repositories, and a Python market-data microservice with live Yahoo Finance data.

Stack: Spring Boot 3.3.x (Java 21, Maven), Python 3.11 FastAPI, PostgreSQL 15.

## Scope

This phase produces NO controllers, NO services, NO frontend. Only infrastructure and data layer.

---

## Component 1 — Maven + Spring Boot Config

### pom.xml

- Parent: `spring-boot-starter-parent` 3.3.x
- Java 21
- Group: `com.halaltrader`, Artifact: `halaltrader-backend`
- Dependencies:
  - `spring-boot-starter-web`
  - `spring-boot-starter-data-jpa`
  - `spring-boot-starter-webflux`
  - `postgresql` (runtime)
  - `flyway-core`
  - `lombok`

### application.yml

- Datasource URL: `${DB_URL}` (default: `jdbc:postgresql://localhost:5432/halaltrader`)
- User: `${DB_USER}` (default: `halal`)
- Password: `${DB_PASS}` (default: `halal123`)
- `ddl-auto: validate`
- `show-sql: false`
- Flyway enabled
- Custom properties:
  - `anthropic.model=claude-haiku-4-5`
  - `anthropic.api-key=${ANTHROPIC_API_KEY}`
  - `trading.scheduler.cron=0 */30 * * * *`
  - `trading.simulation.initial-cash=100000`
  - `market-data.base-url=http://localhost:8081`

---

## Component 2 — JPA Entities

Package: `com.halaltrader.backend.entity`

All entities use Lombok `@Data @Entity`, UUID primary keys with `@GeneratedValue(strategy = GenerationType.UUID)` (JPA 3.0, supported natively by Hibernate 6 bundled with Spring Boot 3.x), and `@CreationTimestamp` / `@UpdateTimestamp` from `org.hibernate.annotations` (no extra dependency — Hibernate 6 is transitively included via spring-boot-starter-data-jpa).

### Enums

```
AssetType    : ETF, STOCK, COMMODITY
HalalScreening : APPROVED, PENDING, REJECTED
TradeAction  : BUY, SELL, HOLD
```

### Asset

| Field | Type | Notes |
|---|---|---|
| id | UUID | PK, auto-generated |
| symbol | String | e.g. AAPL |
| name | String | |
| assetType | AssetType | enum |
| halalScreening | HalalScreening | enum |
| halalReason | String | |
| sector | String | |
| createdAt | LocalDateTime | |
| updatedAt | LocalDateTime | |

### Portfolio

| Field | Type |
|---|---|
| id | UUID |
| name | String |
| cashBalance | BigDecimal |
| createdAt | LocalDateTime |

### PortfolioPosition

| Field | Type | Notes |
|---|---|---|
| id | UUID | |
| portfolio | Portfolio | @ManyToOne |
| asset | Asset | @ManyToOne |
| quantity | BigDecimal | |
| averageBuyPrice | BigDecimal | |

### Trade

| Field | Type | Notes |
|---|---|---|
| id | UUID | |
| portfolio | Portfolio | @ManyToOne |
| asset | Asset | @ManyToOne |
| action | TradeAction | enum |
| quantity | BigDecimal | |
| price | BigDecimal | |
| totalAmount | BigDecimal | |
| aiReasoning | String | @Column(columnDefinition="TEXT") |
| technicalData | String | @Column(columnDefinition="TEXT") |
| executedAt | LocalDateTime | |
| simulatedPnl | BigDecimal | |

---

## Component 3 — Flyway V1__init.sql

Location: `src/main/resources/db/migration/V1__init.sql`

Creates tables: `assets`, `portfolios`, `portfolio_positions`, `trades`.

Seed data:

**Assets (APPROVED):**
- `ISWD.L` — iShares MSCI World Islamic ETF — ETF — Islamic
- `GLD` — SPDR Gold Trust — COMMODITY — Commodity
- `AAPL` — Apple Inc — STOCK — Technology
- `MSFT` — Microsoft Corp — STOCK — Technology
- `NVDA` — NVIDIA Corp — STOCK — Technology

**Portfolio:**
- "Simulation Portfolio" — cash_balance: 100000.00

---

## Component 4 — Spring Data Repositories

Package: `com.halaltrader.backend.repository`

```java
AssetRepository extends JpaRepository<Asset, UUID>
  + findByHalalScreening(HalalScreening status)

PortfolioRepository extends JpaRepository<Portfolio, UUID>
  // standard only

PortfolioPositionRepository extends JpaRepository<PortfolioPosition, UUID>
  + findByPortfolioAndAsset(Portfolio p, Asset a)

TradeRepository extends JpaRepository<Trade, UUID>
  + findTop5ByAssetOrderByExecutedAtDesc(Asset asset)
```

---

## Component 5 — Python market-data Service

Location: `market-data/`

### Endpoints

| Method | Path | Response |
|---|---|---|
| GET | `/health` | `{"status": "ok"}` |
| GET | `/price/{symbol}` | price, change_pct, volume, rsi, macd, ma20, ma50 |
| GET | `/history/{symbol}?period=1mo` | list of OHLCV candles |
| GET | `/news/{symbol}` | list of up to 3 news titles |

### Data source

- **Live data** via `yfinance`. When market is closed, yfinance returns the last known price — this is the expected behavior.
- RSI(14), MACD(12,26,9), MA20, MA50 calculated with `pandas` from downloaded history.

### Files

- `main.py` — FastAPI app
- `requirements.txt` — fastapi, uvicorn, yfinance, pandas
- `Dockerfile` — `python:3.11-slim`, expose 8081, `uvicorn main:app --host 0.0.0.0 --port 8081`

---

## Component 6 — Docker Compose

Services:
- `postgres` — image `postgres:15`, port `5432:5432`, env: POSTGRES_DB=halaltrader, POSTGRES_USER=halal, POSTGRES_PASSWORD=halal123
- `market-data` — build `./market-data`, port `8081:8081`, depends_on postgres (health check)

Backend and frontend are NOT included in docker-compose for this phase.

---

## Execution Strategy

5 sequential sub-agents, each reads existing files before writing:

| Agent | Mission | Output |
|---|---|---|
| `maven-setup` | pom.xml + application.yml | Config only, no Java logic |
| `db-schema` | JPA entities + enums + V1__init.sql | No services/controllers |
| `repositories` | 4 Spring Data repositories | Reads entities first |
| `market-data-service` | main.py + requirements.txt + Dockerfile | Python only |
| `docker-infra` | docker-compose.yml | Validates ports vs application.yml |

---

## Constraints

- No `@Service`, no `@RestController` in this phase
- Each agent reads existing files before writing
- Modify existing files rather than recreating when possible
- `claude-haiku-4-5` model for sub-agents (cost efficiency)
