# HalalTrader Phase 1 — Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish the Spring Boot + PostgreSQL + Python FastAPI foundation for HalalTrader with JPA entities, Flyway migrations, Spring Data repositories, and a live market-data microservice.

**Architecture:** Spring Boot 3.3.x backend (Java 21, Maven) connects to PostgreSQL via JPA/Flyway. A separate Python FastAPI service fetches live data from Yahoo Finance on port 8081. Both infrastructure services run via Docker Compose. No business logic, controllers, or services in this phase.

**Tech Stack:** Spring Boot 3.3.x, Hibernate 6, Flyway, Lombok, PostgreSQL 15, Python 3.11, FastAPI, yfinance, pandas, Docker Compose.

---

## File Map

| File | Action | Responsibility |
|---|---|---|
| `pom.xml` | Modify | Add Spring Boot parent + all dependencies |
| `src/main/resources/application.yml` | Create | DB config, Flyway, custom properties |
| `src/main/java/com/halaltrader/backend/HalalTraderApplication.java` | Create | Spring Boot entry point |
| `src/main/java/com/halaltrader/backend/entity/AssetType.java` | Create | Enum: ETF, STOCK, COMMODITY |
| `src/main/java/com/halaltrader/backend/entity/HalalScreening.java` | Create | Enum: APPROVED, PENDING, REJECTED |
| `src/main/java/com/halaltrader/backend/entity/TradeAction.java` | Create | Enum: BUY, SELL, HOLD |
| `src/main/java/com/halaltrader/backend/entity/Asset.java` | Create | JPA entity |
| `src/main/java/com/halaltrader/backend/entity/Portfolio.java` | Create | JPA entity |
| `src/main/java/com/halaltrader/backend/entity/PortfolioPosition.java` | Create | JPA entity |
| `src/main/java/com/halaltrader/backend/entity/Trade.java` | Create | JPA entity |
| `src/main/resources/db/migration/V1__init.sql` | Create | Tables + seed data |
| `src/main/java/com/halaltrader/backend/repository/AssetRepository.java` | Create | Spring Data JPA |
| `src/main/java/com/halaltrader/backend/repository/PortfolioRepository.java` | Create | Spring Data JPA |
| `src/main/java/com/halaltrader/backend/repository/PortfolioPositionRepository.java` | Create | Spring Data JPA |
| `src/main/java/com/halaltrader/backend/repository/TradeRepository.java` | Create | Spring Data JPA |
| `market-data/main.py` | Create | FastAPI app with live yfinance data |
| `market-data/requirements.txt` | Create | Python dependencies |
| `market-data/Dockerfile` | Create | Container definition |
| `docker-compose.yml` | Create | postgres + market-data services |

---

## Task 1 — Maven Setup (Agent: maven-setup)

**Files:**
- Modify: `pom.xml`
- Create: `src/main/resources/application.yml`
- Create: `src/main/java/com/halaltrader/backend/HalalTraderApplication.java`

- [ ] **Step 1: Read the existing pom.xml**

  Read `pom.xml` before modifying. It currently has groupId/artifactId/Java 21 but no Spring Boot parent.

- [ ] **Step 2: Replace pom.xml with full Spring Boot config**

  ```xml
  <?xml version="1.0" encoding="UTF-8"?>
  <project xmlns="http://maven.apache.org/POM/4.0.0"
           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
      <modelVersion>4.0.0</modelVersion>

      <parent>
          <groupId>org.springframework.boot</groupId>
          <artifactId>spring-boot-starter-parent</artifactId>
          <version>3.3.5</version>
          <relativePath/>
      </parent>

      <groupId>com.halaltrader</groupId>
      <artifactId>halaltrader-backend</artifactId>
      <version>1.0-SNAPSHOT</version>

      <properties>
          <java.version>21</java.version>
      </properties>

      <dependencies>
          <dependency>
              <groupId>org.springframework.boot</groupId>
              <artifactId>spring-boot-starter-web</artifactId>
          </dependency>
          <dependency>
              <groupId>org.springframework.boot</groupId>
              <artifactId>spring-boot-starter-data-jpa</artifactId>
          </dependency>
          <dependency>
              <groupId>org.springframework.boot</groupId>
              <artifactId>spring-boot-starter-webflux</artifactId>
          </dependency>
          <dependency>
              <groupId>org.postgresql</groupId>
              <artifactId>postgresql</artifactId>
              <scope>runtime</scope>
          </dependency>
          <dependency>
              <groupId>org.flywaydb</groupId>
              <artifactId>flyway-core</artifactId>
          </dependency>
          <dependency>
              <groupId>org.flywaydb</groupId>
              <artifactId>flyway-database-postgresql</artifactId>
          </dependency>
          <dependency>
              <groupId>org.projectlombok</groupId>
              <artifactId>lombok</artifactId>
              <optional>true</optional>
          </dependency>
          <dependency>
              <groupId>org.springframework.boot</groupId>
              <artifactId>spring-boot-starter-test</artifactId>
              <scope>test</scope>
          </dependency>
      </dependencies>

      <build>
          <plugins>
              <plugin>
                  <groupId>org.springframework.boot</groupId>
                  <artifactId>spring-boot-maven-plugin</artifactId>
                  <configuration>
                      <excludes>
                          <exclude>
                              <groupId>org.projectlombok</groupId>
                              <artifactId>lombok</artifactId>
                          </exclude>
                      </excludes>
                  </configuration>
              </plugin>
          </plugins>
      </build>

  </project>
  ```

  Note: `flyway-database-postgresql` is required separately in Spring Boot 3.3.x / Flyway 10+.

- [ ] **Step 3: Create src/main/resources/application.yml**

  ```yaml
  spring:
    datasource:
      url: ${DB_URL:jdbc:postgresql://localhost:5432/halaltrader}
      username: ${DB_USER:halal}
      password: ${DB_PASS:halal123}
      driver-class-name: org.postgresql.Driver
    jpa:
      hibernate:
        ddl-auto: validate
      show-sql: false
      properties:
        hibernate:
          dialect: org.hibernate.dialect.PostgreSQLDialect
          format_sql: false
    flyway:
      enabled: true
      locations: classpath:db/migration

  anthropic:
    api-key: ${ANTHROPIC_API_KEY:}
    model: claude-haiku-4-5

  trading:
    scheduler:
      cron: "0 */30 * * * *"
    simulation:
      initial-cash: 100000

  market-data:
    base-url: http://localhost:8081
  ```

- [ ] **Step 4: Create HalalTraderApplication.java**

  Path: `src/main/java/com/halaltrader/backend/HalalTraderApplication.java`

  ```java
  package com.halaltrader.backend;

  import org.springframework.boot.SpringApplication;
  import org.springframework.boot.autoconfigure.SpringBootApplication;

  @SpringBootApplication
  public class HalalTraderApplication {
      public static void main(String[] args) {
          SpringApplication.run(HalalTraderApplication.class, args);
      }
  }
  ```

- [ ] **Step 5: Verify Maven resolves dependencies**

  Run: `mvn dependency:resolve -q`
  Expected: BUILD SUCCESS (no errors)

- [ ] **Step 6: Commit**

  ```bash
  git add pom.xml src/main/resources/application.yml src/main/java/com/halaltrader/backend/HalalTraderApplication.java
  git commit -m "feat: add Spring Boot 3.3.x Maven config and application.yml"
  ```

---

## Task 2 — Database Schema (Agent: db-schema)

**Files:**
- Create: `src/main/java/com/halaltrader/backend/entity/AssetType.java`
- Create: `src/main/java/com/halaltrader/backend/entity/HalalScreening.java`
- Create: `src/main/java/com/halaltrader/backend/entity/TradeAction.java`
- Create: `src/main/java/com/halaltrader/backend/entity/Asset.java`
- Create: `src/main/java/com/halaltrader/backend/entity/Portfolio.java`
- Create: `src/main/java/com/halaltrader/backend/entity/PortfolioPosition.java`
- Create: `src/main/java/com/halaltrader/backend/entity/Trade.java`
- Create: `src/main/resources/db/migration/V1__init.sql`

- [ ] **Step 1: Read pom.xml and application.yml to confirm context**

  Verify Spring Boot 3.3.x and Flyway location `classpath:db/migration`.

- [ ] **Step 2: Create the 3 enums**

  `AssetType.java`:
  ```java
  package com.halaltrader.backend.entity;

  public enum AssetType {
      ETF, STOCK, COMMODITY
  }
  ```

  `HalalScreening.java`:
  ```java
  package com.halaltrader.backend.entity;

  public enum HalalScreening {
      APPROVED, PENDING, REJECTED
  }
  ```

  `TradeAction.java`:
  ```java
  package com.halaltrader.backend.entity;

  public enum TradeAction {
      BUY, SELL, HOLD
  }
  ```

- [ ] **Step 3: Create Asset.java**

  ```java
  package com.halaltrader.backend.entity;

  import jakarta.persistence.*;
  import lombok.Data;
  import org.hibernate.annotations.CreationTimestamp;
  import org.hibernate.annotations.UpdateTimestamp;

  import java.time.LocalDateTime;
  import java.util.UUID;

  @Data
  @Entity
  @Table(name = "assets")
  public class Asset {

      @Id
      @GeneratedValue(strategy = GenerationType.UUID)
      private UUID id;

      @Column(nullable = false, unique = true)
      private String symbol;

      @Column(nullable = false)
      private String name;

      @Enumerated(EnumType.STRING)
      @Column(name = "asset_type", nullable = false)
      private AssetType assetType;

      @Enumerated(EnumType.STRING)
      @Column(name = "halal_screening", nullable = false)
      private HalalScreening halalScreening;

      @Column(name = "halal_reason")
      private String halalReason;

      private String sector;

      @CreationTimestamp
      @Column(name = "created_at", updatable = false)
      private LocalDateTime createdAt;

      @UpdateTimestamp
      @Column(name = "updated_at")
      private LocalDateTime updatedAt;
  }
  ```

- [ ] **Step 4: Create Portfolio.java**

  ```java
  package com.halaltrader.backend.entity;

  import jakarta.persistence.*;
  import lombok.Data;
  import org.hibernate.annotations.CreationTimestamp;

  import java.math.BigDecimal;
  import java.time.LocalDateTime;
  import java.util.UUID;

  @Data
  @Entity
  @Table(name = "portfolios")
  public class Portfolio {

      @Id
      @GeneratedValue(strategy = GenerationType.UUID)
      private UUID id;

      @Column(nullable = false)
      private String name;

      @Column(name = "cash_balance", nullable = false, precision = 19, scale = 4)
      private BigDecimal cashBalance;

      @CreationTimestamp
      @Column(name = "created_at", updatable = false)
      private LocalDateTime createdAt;
  }
  ```

- [ ] **Step 5: Create PortfolioPosition.java**

  ```java
  package com.halaltrader.backend.entity;

  import jakarta.persistence.*;
  import lombok.Data;

  import java.math.BigDecimal;
  import java.util.UUID;

  @Data
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
  ```

- [ ] **Step 6: Create Trade.java**

  ```java
  package com.halaltrader.backend.entity;

  import jakarta.persistence.*;
  import lombok.Data;

  import java.math.BigDecimal;
  import java.time.LocalDateTime;
  import java.util.UUID;

  @Data
  @Entity
  @Table(name = "trades")
  public class Trade {

      @Id
      @GeneratedValue(strategy = GenerationType.UUID)
      private UUID id;

      @ManyToOne(fetch = FetchType.LAZY)
      @JoinColumn(name = "portfolio_id", nullable = false)
      private Portfolio portfolio;

      @ManyToOne(fetch = FetchType.LAZY)
      @JoinColumn(name = "asset_id", nullable = false)
      private Asset asset;

      @Enumerated(EnumType.STRING)
      @Column(nullable = false)
      private TradeAction action;

      @Column(nullable = false, precision = 19, scale = 8)
      private BigDecimal quantity;

      @Column(nullable = false, precision = 19, scale = 4)
      private BigDecimal price;

      @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
      private BigDecimal totalAmount;

      @Column(name = "ai_reasoning", columnDefinition = "TEXT")
      private String aiReasoning;

      @Column(name = "technical_data", columnDefinition = "TEXT")
      private String technicalData;

      @Column(name = "executed_at")
      private LocalDateTime executedAt;

      @Column(name = "simulated_pnl", precision = 19, scale = 4)
      private BigDecimal simulatedPnl;
  }
  ```

- [ ] **Step 7: Create V1__init.sql**

  Path: `src/main/resources/db/migration/V1__init.sql`

  ```sql
  CREATE EXTENSION IF NOT EXISTS "pgcrypto";

  CREATE TABLE assets (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      symbol VARCHAR(20) NOT NULL UNIQUE,
      name VARCHAR(255) NOT NULL,
      asset_type VARCHAR(20) NOT NULL,
      halal_screening VARCHAR(20) NOT NULL,
      halal_reason VARCHAR(500),
      sector VARCHAR(100),
      created_at TIMESTAMP NOT NULL DEFAULT NOW(),
      updated_at TIMESTAMP NOT NULL DEFAULT NOW()
  );

  CREATE TABLE portfolios (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      name VARCHAR(255) NOT NULL,
      cash_balance NUMERIC(19, 4) NOT NULL,
      created_at TIMESTAMP NOT NULL DEFAULT NOW()
  );

  CREATE TABLE portfolio_positions (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      portfolio_id UUID NOT NULL REFERENCES portfolios(id),
      asset_id UUID NOT NULL REFERENCES assets(id),
      quantity NUMERIC(19, 8) NOT NULL,
      average_buy_price NUMERIC(19, 4) NOT NULL
  );

  CREATE TABLE trades (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      portfolio_id UUID NOT NULL REFERENCES portfolios(id),
      asset_id UUID NOT NULL REFERENCES assets(id),
      action VARCHAR(10) NOT NULL,
      quantity NUMERIC(19, 8) NOT NULL,
      price NUMERIC(19, 4) NOT NULL,
      total_amount NUMERIC(19, 4) NOT NULL,
      ai_reasoning TEXT,
      technical_data TEXT,
      executed_at TIMESTAMP,
      simulated_pnl NUMERIC(19, 4)
  );

  -- Seed: approved halal assets
  INSERT INTO assets (symbol, name, asset_type, halal_screening, halal_reason, sector) VALUES
      ('ISWD.L', 'iShares MSCI World Islamic ETF', 'ETF',       'APPROVED', 'ETF islamique certifié',        'Islamic'),
      ('GLD',    'SPDR Gold Trust',                'COMMODITY', 'APPROVED', 'Or physique halal',              'Commodity'),
      ('AAPL',   'Apple Inc',                      'STOCK',     'APPROVED', 'Secteur tech, ratio dette OK',  'Technology'),
      ('MSFT',   'Microsoft Corp',                 'STOCK',     'APPROVED', 'Secteur tech, ratio dette OK',  'Technology'),
      ('NVDA',   'NVIDIA Corp',                    'STOCK',     'APPROVED', 'Secteur tech, ratio dette OK',  'Technology');

  -- Seed: initial portfolio
  INSERT INTO portfolios (name, cash_balance) VALUES
      ('Simulation Portfolio', 100000.00);
  ```

- [ ] **Step 8: Compile to check for errors**

  Run: `mvn compile -q`
  Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

  ```bash
  git add src/main/java/com/halaltrader/backend/entity/ src/main/resources/db/migration/
  git commit -m "feat: add JPA entities, enums, and Flyway V1 migration"
  ```

---

## Task 3 — Spring Data Repositories (Agent: repositories)

**Files:**
- Create: `src/main/java/com/halaltrader/backend/repository/AssetRepository.java`
- Create: `src/main/java/com/halaltrader/backend/repository/PortfolioRepository.java`
- Create: `src/main/java/com/halaltrader/backend/repository/PortfolioPositionRepository.java`
- Create: `src/main/java/com/halaltrader/backend/repository/TradeRepository.java`

- [ ] **Step 1: Read entity files before writing**

  Read all 4 entity files and 3 enum files to verify field names and types.

- [ ] **Step 2: Create AssetRepository.java**

  ```java
  package com.halaltrader.backend.repository;

  import com.halaltrader.backend.entity.Asset;
  import com.halaltrader.backend.entity.HalalScreening;
  import org.springframework.data.jpa.repository.JpaRepository;
  import org.springframework.stereotype.Repository;

  import java.util.List;
  import java.util.UUID;

  @Repository
  public interface AssetRepository extends JpaRepository<Asset, UUID> {
      List<Asset> findByHalalScreening(HalalScreening status);
  }
  ```

- [ ] **Step 3: Create PortfolioRepository.java**

  ```java
  package com.halaltrader.backend.repository;

  import com.halaltrader.backend.entity.Portfolio;
  import org.springframework.data.jpa.repository.JpaRepository;
  import org.springframework.stereotype.Repository;

  import java.util.UUID;

  @Repository
  public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {
  }
  ```

- [ ] **Step 4: Create PortfolioPositionRepository.java**

  ```java
  package com.halaltrader.backend.repository;

  import com.halaltrader.backend.entity.Asset;
  import com.halaltrader.backend.entity.Portfolio;
  import com.halaltrader.backend.entity.PortfolioPosition;
  import org.springframework.data.jpa.repository.JpaRepository;
  import org.springframework.stereotype.Repository;

  import java.util.Optional;
  import java.util.UUID;

  @Repository
  public interface PortfolioPositionRepository extends JpaRepository<PortfolioPosition, UUID> {
      Optional<PortfolioPosition> findByPortfolioAndAsset(Portfolio portfolio, Asset asset);
  }
  ```

- [ ] **Step 5: Create TradeRepository.java**

  ```java
  package com.halaltrader.backend.repository;

  import com.halaltrader.backend.entity.Asset;
  import com.halaltrader.backend.entity.Trade;
  import org.springframework.data.jpa.repository.JpaRepository;
  import org.springframework.stereotype.Repository;

  import java.util.List;
  import java.util.UUID;

  @Repository
  public interface TradeRepository extends JpaRepository<Trade, UUID> {
      List<Trade> findTop5ByAssetOrderByExecutedAtDesc(Asset asset);
  }
  ```

- [ ] **Step 6: Compile**

  Run: `mvn compile -q`
  Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

  ```bash
  git add src/main/java/com/halaltrader/backend/repository/
  git commit -m "feat: add Spring Data JPA repositories"
  ```

---

## Task 4 — Python Market-Data Service (Agent: market-data-service)

**Files:**
- Create: `market-data/main.py`
- Create: `market-data/requirements.txt`
- Create: `market-data/Dockerfile`

- [ ] **Step 1: Create market-data/requirements.txt**

  ```
  fastapi==0.115.0
  uvicorn==0.30.6
  yfinance==0.2.44
  pandas==2.2.3
  ```

- [ ] **Step 2: Create market-data/main.py**

  ```python
  from fastapi import FastAPI, HTTPException
  import yfinance as yf
  import pandas as pd
  from typing import Optional

  app = FastAPI(title="HalalTrader Market Data Service")


  def compute_rsi(series: pd.Series, period: int = 14) -> float:
      delta = series.diff()
      gain = delta.where(delta > 0, 0.0).rolling(window=period).mean()
      loss = (-delta.where(delta < 0, 0.0)).rolling(window=period).mean()
      rs = gain / loss
      rsi = 100 - (100 / (1 + rs))
      return round(float(rsi.iloc[-1]), 2)


  def compute_macd(series: pd.Series, fast: int = 12, slow: int = 26, signal: int = 9) -> dict:
      ema_fast = series.ewm(span=fast, adjust=False).mean()
      ema_slow = series.ewm(span=slow, adjust=False).mean()
      macd_line = ema_fast - ema_slow
      signal_line = macd_line.ewm(span=signal, adjust=False).mean()
      return {
          "macd": round(float(macd_line.iloc[-1]), 4),
          "signal": round(float(signal_line.iloc[-1]), 4),
          "histogram": round(float((macd_line - signal_line).iloc[-1]), 4),
      }


  @app.get("/health")
  def health():
      return {"status": "ok"}


  @app.get("/price/{symbol}")
  def get_price(symbol: str):
      try:
          ticker = yf.Ticker(symbol)
          hist = ticker.history(period="3mo")
          if hist.empty:
              raise HTTPException(status_code=404, detail=f"No data for symbol: {symbol}")

          close = hist["Close"]
          current_price = round(float(close.iloc[-1]), 4)
          prev_price = round(float(close.iloc[-2]), 4) if len(close) > 1 else current_price
          change_pct = round(((current_price - prev_price) / prev_price) * 100, 2) if prev_price else 0.0
          volume = int(hist["Volume"].iloc[-1]) if "Volume" in hist.columns else 0

          rsi = compute_rsi(close)
          macd_data = compute_macd(close)
          ma20 = round(float(close.rolling(window=20).mean().iloc[-1]), 4)
          ma50 = round(float(close.rolling(window=50).mean().iloc[-1]), 4)

          return {
              "symbol": symbol.upper(),
              "price": current_price,
              "change_pct": change_pct,
              "volume": volume,
              "rsi": rsi,
              "macd": macd_data["macd"],
              "macd_signal": macd_data["signal"],
              "macd_histogram": macd_data["histogram"],
              "ma20": ma20,
              "ma50": ma50,
          }
      except HTTPException:
          raise
      except Exception as e:
          raise HTTPException(status_code=500, detail=str(e))


  @app.get("/history/{symbol}")
  def get_history(symbol: str, period: Optional[str] = "1mo"):
      try:
          ticker = yf.Ticker(symbol)
          hist = ticker.history(period=period)
          if hist.empty:
              raise HTTPException(status_code=404, detail=f"No data for symbol: {symbol}")

          candles = []
          for date, row in hist.iterrows():
              candles.append({
                  "date": date.strftime("%Y-%m-%d"),
                  "open": round(float(row["Open"]), 4),
                  "high": round(float(row["High"]), 4),
                  "low": round(float(row["Low"]), 4),
                  "close": round(float(row["Close"]), 4),
                  "volume": int(row["Volume"]),
              })
          return candles
      except HTTPException:
          raise
      except Exception as e:
          raise HTTPException(status_code=500, detail=str(e))


  @app.get("/news/{symbol}")
  def get_news(symbol: str):
      try:
          ticker = yf.Ticker(symbol)
          news = ticker.news or []
          results = []
          for item in news[:3]:
              title = item.get("content", {}).get("title") or item.get("title", "")
              if title:
                  results.append({"title": title})
          return results
      except Exception as e:
          raise HTTPException(status_code=500, detail=str(e))
  ```

  Note on `/news`: yfinance 0.2.x changed the news response structure. The code handles both old (`item["title"]`) and new (`item["content"]["title"]`) formats.

- [ ] **Step 3: Create market-data/Dockerfile**

  ```dockerfile
  FROM python:3.11-slim

  WORKDIR /app

  COPY requirements.txt .
  RUN pip install --no-cache-dir -r requirements.txt

  COPY main.py .

  EXPOSE 8081

  CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8081"]
  ```

- [ ] **Step 4: Commit**

  ```bash
  git add market-data/
  git commit -m "feat: add Python FastAPI market-data service with live yfinance data"
  ```

---

## Task 5 — Docker Infrastructure (Agent: docker-infra)

**Files:**
- Create: `docker-compose.yml`

- [ ] **Step 1: Read application.yml to verify port and DB credentials**

  Confirm: DB_URL uses port 5432, DB_USER=halal, DB_PASS=halal123, DB_NAME=halaltrader, market-data on 8081.

- [ ] **Step 2: Create docker-compose.yml**

  ```yaml
  services:
    postgres:
      image: postgres:15
      container_name: halaltrader-postgres
      environment:
        POSTGRES_DB: halaltrader
        POSTGRES_USER: halal
        POSTGRES_PASSWORD: halal123
      ports:
        - "5432:5432"
      volumes:
        - postgres_data:/var/lib/postgresql/data
      healthcheck:
        test: ["CMD-SHELL", "pg_isready -U halal -d halaltrader"]
        interval: 10s
        timeout: 5s
        retries: 5

    market-data:
      build: ./market-data
      container_name: halaltrader-market-data
      ports:
        - "8081:8081"
      depends_on:
        postgres:
          condition: service_healthy
      restart: on-failure

  volumes:
    postgres_data:
  ```

- [ ] **Step 3: Start infrastructure and verify**

  ```bash
  docker compose up -d
  ```

  Expected: both containers start without error.

  ```bash
  docker compose ps
  ```

  Expected: postgres = healthy, market-data = running.

- [ ] **Step 4: Smoke test market-data service**

  ```bash
  curl http://localhost:8081/health
  ```
  Expected: `{"status":"ok"}`

  ```bash
  curl http://localhost:8081/price/AAPL
  ```
  Expected: JSON with price, rsi, macd, ma20, ma50 fields.

- [ ] **Step 5: Start Spring Boot and verify Flyway**

  Prerequisites: Docker Compose must be running.

  ```bash
  mvn spring-boot:run
  ```

  Expected output includes:
  ```
  Successfully applied 1 migration to schema "public"
  Started HalalTraderApplication
  ```

  If you see `ddl-auto=validate` errors, the Flyway migration did not run — check the `db/migration` path.

- [ ] **Step 6: Commit**

  ```bash
  git add docker-compose.yml
  git commit -m "feat: add Docker Compose for postgres and market-data services"
  ```

---

## Verification Checklist

After all 5 tasks:

- [ ] `mvn compile` passes with no errors
- [ ] `mvn spring-boot:run` starts and Flyway logs "Successfully applied 1 migration"
- [ ] `curl http://localhost:8081/health` returns `{"status":"ok"}`
- [ ] `curl http://localhost:8081/price/AAPL` returns JSON with price + indicators
- [ ] `curl http://localhost:8081/history/AAPL?period=1mo` returns array of OHLCV candles
- [ ] `docker compose ps` shows both containers healthy/running
- [ ] No `@Service` or `@RestController` annotations exist anywhere in the codebase

---

## Common Pitfalls

| Problem | Fix |
|---|---|
| `flyway-database-postgresql not found` | Add `flyway-database-postgresql` artifact (Flyway 10+ requires it separately) |
| `ddl-auto=validate` fails on startup | Flyway migration hasn't run — check `spring.flyway.locations` path |
| `GenerationType.UUID` not found | Requires Hibernate 6+ (included in Spring Boot 3.x via starter-data-jpa) |
| market-data container crashes on start | yfinance network call at startup? No — endpoints are lazy. Check requirements.txt versions |
| yfinance news returns empty | yfinance 0.2.x changed news format — the code handles both formats |
