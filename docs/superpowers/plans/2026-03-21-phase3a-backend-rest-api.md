# Phase 3A — Backend REST API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose les données du portfolio, trades, assets et performance via une REST API JSON consommable par le dashboard React.

**Architecture:** 4 controllers REST thin (délèguent à des services) + 4 query services + 6 response DTOs records. Pas de logique métier dans les controllers. Les DTOs de réponse sont dans le package `dto/` existant, clairement nommés pour éviter toute confusion avec les DTOs agents.

**Tech Stack:** Spring Boot 3.3.5, Spring MVC, Spring Data JPA, Lombok, JUnit 5, MockMvc, Mockito

---

## Contexte codebase

- **Maven wrapper** : utiliser `./mvnw` (présent à la racine via `.mvn/`)
- **JAVA_HOME** : `C:\Program Files\Java\jdk-21` (à définir dans le shell si besoin)
- **Profile local** : `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`
- **Tests** : `./mvnw test`
- **Package racine** : `com.halaltrader.backend`
- **Entités JPA existantes** : `Portfolio`, `PortfolioPosition`, `Trade`, `Asset`, `TradeAction`, `HalalScreening`, `AssetType`
- **Repositories existants** : `PortfolioRepository`, `PortfolioPositionRepository`, `TradeRepository`, `AssetRepository`
- **Pattern Lombok** : toutes les entités utilisent `@Data` + `@RequiredArgsConstructor` via Lombok — les services doivent faire pareil

---

## Fichiers créés / modifiés

### Nouveaux
```
src/main/java/com/halaltrader/backend/
├── config/CorsConfig.java
├── dto/
│   ├── PortfolioSummaryDto.java
│   ├── PositionDto.java
│   ├── TradeDto.java
│   ├── TradeDetailDto.java
│   ├── AssetDto.java
│   └── PerformanceDto.java
├── service/
│   ├── PortfolioQueryService.java
│   ├── TradeQueryService.java
│   ├── AssetQueryService.java
│   └── PerformanceQueryService.java
└── controller/
    ├── PortfolioController.java
    ├── TradeController.java
    ├── AssetController.java
    └── PerformanceController.java

src/test/java/com/halaltrader/backend/controller/
├── PortfolioControllerTest.java
├── TradeControllerTest.java
├── AssetControllerTest.java
└── PerformanceControllerTest.java
```

### Modifiés
```
src/main/java/com/halaltrader/backend/repository/
├── TradeRepository.java               ← +2 méthodes Spring Data
└── PortfolioPositionRepository.java   ← +1 méthode Spring Data

src/main/resources/application.yml    ← +jackson dates + cors origin
```

---

## Task 1: CORS Config + Jackson dates

**Files:**
- Create: `src/main/java/com/halaltrader/backend/config/CorsConfig.java`
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: Ajouter la config Jackson dans application.yml**

  Ajouter sous la section `spring:` existante (au même niveau que `datasource:`, `jpa:`, `flyway:`) :

  ```yaml
  spring:
    # ... existant ...
    jackson:
      serialization:
        write-dates-as-timestamps: false
  ```

  Et à la fin du fichier, ajouter :

  ```yaml
  app:
    cors:
      allowed-origins: http://localhost:5173
  ```

- [ ] **Step 2: Créer CorsConfig.java**

  ```java
  package com.halaltrader.backend.config;

  import org.springframework.beans.factory.annotation.Value;
  import org.springframework.context.annotation.Configuration;
  import org.springframework.web.servlet.config.annotation.CorsRegistry;
  import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

  @Configuration
  public class CorsConfig implements WebMvcConfigurer {

      @Value("${app.cors.allowed-origins:http://localhost:5173}")
      private String allowedOrigins;

      @Override
      public void addCorsMappings(CorsRegistry registry) {
          registry.addMapping("/api/**")
                  .allowedOrigins(allowedOrigins)
                  .allowedMethods("GET", "OPTIONS")
                  .allowedHeaders("*");
      }
  }
  ```

- [ ] **Step 3: Vérifier que le projet compile**

  ```bash
  ./mvnw compile -q
  ```
  Attendu : pas d'erreur.

- [ ] **Step 4: Commit**

  ```bash
  git add src/main/java/com/halaltrader/backend/config/CorsConfig.java \
          src/main/resources/application.yml
  git commit -m "feat: add CORS config and Jackson date serialization"
  ```

---

## Task 2: Response DTOs

**Files:**
- Create: `src/main/java/com/halaltrader/backend/dto/PortfolioSummaryDto.java`
- Create: `src/main/java/com/halaltrader/backend/dto/PositionDto.java`
- Create: `src/main/java/com/halaltrader/backend/dto/TradeDto.java`
- Create: `src/main/java/com/halaltrader/backend/dto/TradeDetailDto.java`
- Create: `src/main/java/com/halaltrader/backend/dto/AssetDto.java`
- Create: `src/main/java/com/halaltrader/backend/dto/PerformanceDto.java`

Ces DTOs sont des records Java immuables. Aucun test requis pour les records purs.

- [ ] **Step 1: Créer PortfolioSummaryDto.java**

  ```java
  package com.halaltrader.backend.dto;

  import java.math.BigDecimal;
  import java.util.UUID;

  public record PortfolioSummaryDto(
          UUID id,
          String name,
          BigDecimal cashBalance,
          BigDecimal totalValue,
          BigDecimal totalPnl,
          BigDecimal totalPnlPct,
          int positionCount
  ) {}
  ```

- [ ] **Step 2: Créer PositionDto.java**

  ```java
  package com.halaltrader.backend.dto;

  import java.math.BigDecimal;

  public record PositionDto(
          String symbol,
          String name,
          String assetType,
          BigDecimal quantity,
          BigDecimal avgPrice,
          BigDecimal value
  ) {}
  ```

- [ ] **Step 3: Créer TradeDto.java**

  ```java
  package com.halaltrader.backend.dto;

  import java.math.BigDecimal;
  import java.time.LocalDateTime;
  import java.util.UUID;

  public record TradeDto(
          UUID id,
          String symbol,
          String action,
          BigDecimal quantity,
          BigDecimal price,
          BigDecimal totalAmount,
          BigDecimal simulatedPnl,
          LocalDateTime executedAt
  ) {}
  ```

- [ ] **Step 4: Créer TradeDetailDto.java**

  ```java
  package com.halaltrader.backend.dto;

  import java.math.BigDecimal;
  import java.time.LocalDateTime;
  import java.util.UUID;

  public record TradeDetailDto(
          UUID id,
          String symbol,
          String action,
          BigDecimal quantity,
          BigDecimal price,
          BigDecimal totalAmount,
          BigDecimal simulatedPnl,
          LocalDateTime executedAt,
          String aiReasoning,
          String technicalData
  ) {}
  ```

- [ ] **Step 5: Créer AssetDto.java**

  ```java
  package com.halaltrader.backend.dto;

  import java.util.UUID;

  public record AssetDto(
          UUID id,
          String symbol,
          String name,
          String assetType,
          String halalScreening,
          String halalReason,
          String sector
  ) {}
  ```

- [ ] **Step 6: Créer PerformanceDto.java**

  ```java
  package com.halaltrader.backend.dto;

  import java.math.BigDecimal;
  import java.time.LocalDateTime;
  import java.util.List;

  public record PerformanceDto(
          List<DailyPnlEntry> dailyPnl,
          BigDecimal winRate,
          int totalTrades,
          AssetPnlEntry bestAsset,
          AssetPnlEntry worstAsset,
          BigDecimal halalApprovalRate,
          LocalDateTime lastCycleAt,
          int approvedAssets,
          int totalAssets
  ) {
      public record DailyPnlEntry(String date, BigDecimal cumulativePnl) {}
      public record AssetPnlEntry(String symbol, BigDecimal totalPnl) {}
  }
  ```

- [ ] **Step 7: Vérifier que le projet compile**

  ```bash
  ./mvnw compile -q
  ```
  Attendu : pas d'erreur.

- [ ] **Step 8: Commit**

  ```bash
  git add src/main/java/com/halaltrader/backend/dto/PortfolioSummaryDto.java \
          src/main/java/com/halaltrader/backend/dto/PositionDto.java \
          src/main/java/com/halaltrader/backend/dto/TradeDto.java \
          src/main/java/com/halaltrader/backend/dto/TradeDetailDto.java \
          src/main/java/com/halaltrader/backend/dto/AssetDto.java \
          src/main/java/com/halaltrader/backend/dto/PerformanceDto.java
  git commit -m "feat: add REST API response DTOs"
  ```

---

## Task 3: Repository additions

**Files:**
- Modify: `src/main/java/com/halaltrader/backend/repository/TradeRepository.java`
- Modify: `src/main/java/com/halaltrader/backend/repository/PortfolioPositionRepository.java`

Spring Data JPA génère les requêtes automatiquement à partir des noms de méthodes — pas de JPQL nécessaire.

- [ ] **Step 1: Ajouter les méthodes à TradeRepository.java**

  Le fichier actuel :
  ```java
  @Repository
  public interface TradeRepository extends JpaRepository<Trade, UUID> {
      List<Trade> findTop5ByAssetOrderByExecutedAtDesc(Asset asset);
  }
  ```

  Ajouter deux méthodes :
  ```java
  Page<Trade> findByPortfolio(Portfolio portfolio, Pageable pageable);
  List<Trade> findByPortfolioOrderByExecutedAtAsc(Portfolio portfolio);
  ```

  Résultat final :
  ```java
  package com.halaltrader.backend.repository;

  import com.halaltrader.backend.entity.Asset;
  import com.halaltrader.backend.entity.Portfolio;
  import com.halaltrader.backend.entity.Trade;
  import org.springframework.data.domain.Page;
  import org.springframework.data.domain.Pageable;
  import org.springframework.data.jpa.repository.JpaRepository;
  import org.springframework.stereotype.Repository;

  import java.util.List;
  import java.util.UUID;

  @Repository
  public interface TradeRepository extends JpaRepository<Trade, UUID> {
      List<Trade> findTop5ByAssetOrderByExecutedAtDesc(Asset asset);
      Page<Trade> findByPortfolio(Portfolio portfolio, Pageable pageable);
      List<Trade> findByPortfolioOrderByExecutedAtAsc(Portfolio portfolio);
  }
  ```

- [ ] **Step 2: Ajouter la méthode à PortfolioPositionRepository.java**

  Ajouter :
  ```java
  List<PortfolioPosition> findByPortfolio(Portfolio portfolio);
  ```

  Résultat final :
  ```java
  package com.halaltrader.backend.repository;

  import com.halaltrader.backend.entity.Asset;
  import com.halaltrader.backend.entity.Portfolio;
  import com.halaltrader.backend.entity.PortfolioPosition;
  import org.springframework.data.jpa.repository.JpaRepository;
  import org.springframework.stereotype.Repository;

  import java.util.List;
  import java.util.Optional;
  import java.util.UUID;

  @Repository
  public interface PortfolioPositionRepository extends JpaRepository<PortfolioPosition, UUID> {
      Optional<PortfolioPosition> findByPortfolioAndAsset(Portfolio portfolio, Asset asset);
      List<PortfolioPosition> findByPortfolio(Portfolio portfolio);
  }
  ```

- [ ] **Step 3: Vérifier que le projet compile**

  ```bash
  ./mvnw compile -q
  ```
  Attendu : pas d'erreur.

- [ ] **Step 4: Commit**

  ```bash
  git add src/main/java/com/halaltrader/backend/repository/TradeRepository.java \
          src/main/java/com/halaltrader/backend/repository/PortfolioPositionRepository.java
  git commit -m "feat: add query methods to Trade and PortfolioPosition repositories"
  ```

---

## Task 4: Portfolio API

**Files:**
- Create: `src/main/java/com/halaltrader/backend/service/PortfolioQueryService.java`
- Create: `src/main/java/com/halaltrader/backend/controller/PortfolioController.java`
- Create: `src/test/java/com/halaltrader/backend/controller/PortfolioControllerTest.java`

- [ ] **Step 1: Écrire le test qui échoue**

  ```java
  package com.halaltrader.backend.controller;

  import com.halaltrader.backend.config.AnthropicProperties;
  import com.halaltrader.backend.dto.PortfolioSummaryDto;
  import com.halaltrader.backend.dto.PositionDto;
  import com.halaltrader.backend.service.PortfolioQueryService;
  import org.junit.jupiter.api.Test;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
  import org.springframework.boot.test.mock.mockito.MockBean;
  import org.springframework.test.web.servlet.MockMvc;

  import java.math.BigDecimal;
  import java.util.List;
  import java.util.UUID;

  import static org.mockito.Mockito.when;
  import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

  @WebMvcTest(PortfolioController.class)
  class PortfolioControllerTest {

      @Autowired MockMvc mvc;
      @MockBean PortfolioQueryService portfolioQueryService;
      @MockBean AnthropicProperties anthropicProperties; // évite l'échec de WebClientConfig

      @Test
      void getSummary_returns200WithPortfolioData() throws Exception {
          var dto = new PortfolioSummaryDto(
                  UUID.randomUUID(), "Main Portfolio",
                  new BigDecimal("48000.0000"), new BigDecimal("102450.0000"),
                  new BigDecimal("2450.0000"), new BigDecimal("2.4500"), 2);
          when(portfolioQueryService.getSummary()).thenReturn(dto);

          mvc.perform(get("/api/portfolio"))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.name").value("Main Portfolio"))
                  .andExpect(jsonPath("$.positionCount").value(2));
      }

      @Test
      void getPositions_returnsPositionList() throws Exception {
          var position = new PositionDto("AAPL", "Apple Inc.", "STOCK",
                  new BigDecimal("5.00000000"), new BigDecimal("175.0000"),
                  new BigDecimal("875.0000"));
          when(portfolioQueryService.getPositions()).thenReturn(List.of(position));

          mvc.perform(get("/api/portfolio/positions"))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$[0].symbol").value("AAPL"));
      }

      @Test
      void getPositions_returnsEmptyList() throws Exception {
          when(portfolioQueryService.getPositions()).thenReturn(List.of());

          mvc.perform(get("/api/portfolio/positions"))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$").isEmpty());
      }
  }
  ```

- [ ] **Step 2: Lancer le test pour confirmer qu'il échoue**

  ```bash
  ./mvnw test -Dtest=PortfolioControllerTest -q
  ```
  Attendu : FAIL — `PortfolioController` n'existe pas encore.

- [ ] **Step 3: Créer PortfolioQueryService.java**

  ```java
  package com.halaltrader.backend.service;

  import com.halaltrader.backend.dto.PortfolioSummaryDto;
  import com.halaltrader.backend.dto.PositionDto;
  import com.halaltrader.backend.entity.Portfolio;
  import com.halaltrader.backend.entity.PortfolioPosition;
  import com.halaltrader.backend.repository.PortfolioPositionRepository;
  import com.halaltrader.backend.repository.PortfolioRepository;
  import lombok.RequiredArgsConstructor;
  import org.springframework.beans.factory.annotation.Value;
  import org.springframework.http.HttpStatus;
  import org.springframework.stereotype.Service;
  import org.springframework.web.server.ResponseStatusException;

  import java.math.BigDecimal;
  import java.math.RoundingMode;
  import java.util.List;

  @Service
  @RequiredArgsConstructor
  public class PortfolioQueryService {

      @Value("${trading.simulation.initial-cash:100000}")
      private BigDecimal initialCash;

      private final PortfolioRepository portfolioRepository;
      private final PortfolioPositionRepository positionRepository;

      public Portfolio getPortfolio() {
          return portfolioRepository.findAll().stream()
                  .findFirst()
                  .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No portfolio found"));
      }

      public PortfolioSummaryDto getSummary() {
          Portfolio p = getPortfolio();
          List<PortfolioPosition> positions = positionRepository.findByPortfolio(p);
          BigDecimal positionsValue = positions.stream()
                  .map(pos -> pos.getQuantity().multiply(pos.getAverageBuyPrice()))
                  .reduce(BigDecimal.ZERO, BigDecimal::add);
          BigDecimal totalValue = p.getCashBalance().add(positionsValue);
          BigDecimal totalPnl = totalValue.subtract(initialCash);
          BigDecimal totalPnlPct = initialCash.compareTo(BigDecimal.ZERO) == 0
                  ? BigDecimal.ZERO
                  : totalPnl.divide(initialCash, 4, RoundingMode.HALF_UP)
                             .multiply(new BigDecimal("100"));

          return new PortfolioSummaryDto(
                  p.getId(), p.getName(), p.getCashBalance(),
                  totalValue, totalPnl, totalPnlPct, positions.size());
      }

      public List<PositionDto> getPositions() {
          Portfolio p = getPortfolio();
          return positionRepository.findByPortfolio(p).stream()
                  .map(pos -> new PositionDto(
                          pos.getAsset().getSymbol(),
                          pos.getAsset().getName(),
                          pos.getAsset().getAssetType().name(),
                          pos.getQuantity(),
                          pos.getAverageBuyPrice(),
                          pos.getQuantity().multiply(pos.getAverageBuyPrice())))
                  .toList();
      }
  }
  ```

- [ ] **Step 4: Créer PortfolioController.java**

  ```java
  package com.halaltrader.backend.controller;

  import com.halaltrader.backend.dto.PortfolioSummaryDto;
  import com.halaltrader.backend.dto.PositionDto;
  import com.halaltrader.backend.service.PortfolioQueryService;
  import lombok.RequiredArgsConstructor;
  import org.springframework.web.bind.annotation.GetMapping;
  import org.springframework.web.bind.annotation.RequestMapping;
  import org.springframework.web.bind.annotation.RestController;

  import java.util.List;

  @RestController
  @RequestMapping("/api/portfolio")
  @RequiredArgsConstructor
  public class PortfolioController {

      private final PortfolioQueryService portfolioQueryService;

      @GetMapping
      public PortfolioSummaryDto getSummary() {
          return portfolioQueryService.getSummary();
      }

      @GetMapping("/positions")
      public List<PositionDto> getPositions() {
          return portfolioQueryService.getPositions();
      }
  }
  ```

- [ ] **Step 5: Lancer le test pour confirmer qu'il passe**

  ```bash
  ./mvnw test -Dtest=PortfolioControllerTest -q
  ```
  Attendu : 3 tests PASS.

- [ ] **Step 6: Commit**

  ```bash
  git add src/main/java/com/halaltrader/backend/service/PortfolioQueryService.java \
          src/main/java/com/halaltrader/backend/controller/PortfolioController.java \
          src/test/java/com/halaltrader/backend/controller/PortfolioControllerTest.java
  git commit -m "feat: add GET /api/portfolio and GET /api/portfolio/positions"
  ```

---

## Task 5: Trades API

**Files:**
- Create: `src/main/java/com/halaltrader/backend/service/TradeQueryService.java`
- Create: `src/main/java/com/halaltrader/backend/controller/TradeController.java`
- Create: `src/test/java/com/halaltrader/backend/controller/TradeControllerTest.java`

- [ ] **Step 1: Écrire le test qui échoue**

  ```java
  package com.halaltrader.backend.controller;

  import com.halaltrader.backend.config.AnthropicProperties;
  import com.halaltrader.backend.dto.TradeDetailDto;
  import com.halaltrader.backend.dto.TradeDto;
  import com.halaltrader.backend.service.TradeQueryService;
  import org.junit.jupiter.api.Test;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
  import org.springframework.boot.test.mock.mockito.MockBean;
  import org.springframework.data.domain.PageImpl;
  import org.springframework.http.HttpStatus;
  import org.springframework.test.web.servlet.MockMvc;
  import org.springframework.web.server.ResponseStatusException;

  import java.math.BigDecimal;
  import java.time.LocalDateTime;
  import java.util.List;
  import java.util.UUID;

  import static org.mockito.ArgumentMatchers.anyInt;
  import static org.mockito.Mockito.when;
  import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

  @WebMvcTest(TradeController.class)
  class TradeControllerTest {

      @Autowired MockMvc mvc;
      @MockBean TradeQueryService tradeQueryService;
      @MockBean AnthropicProperties anthropicProperties;

      private final UUID tradeId = UUID.randomUUID();

      @Test
      void list_returns200WithPaginatedTrades() throws Exception {
          var dto = new TradeDto(tradeId, "AAPL", "BUY",
                  new BigDecimal("5"), new BigDecimal("175"),
                  new BigDecimal("875"), new BigDecimal("50"), LocalDateTime.now());
          when(tradeQueryService.list(anyInt(), anyInt()))
                  .thenReturn(new PageImpl<>(List.of(dto)));

          mvc.perform(get("/api/trades"))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.content[0].symbol").value("AAPL"))
                  .andExpect(jsonPath("$.content[0].action").value("BUY"));
      }

      @Test
      void getById_returns200WithDetail() throws Exception {
          var dto = new TradeDetailDto(tradeId, "AAPL", "BUY",
                  new BigDecimal("5"), new BigDecimal("175"),
                  new BigDecimal("875"), new BigDecimal("50"),
                  LocalDateTime.now(), "{\"decision\":\"BUY\"}", "{\"rsi\":45}");
          when(tradeQueryService.getById(tradeId)).thenReturn(dto);

          mvc.perform(get("/api/trades/{id}", tradeId))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.aiReasoning").isNotEmpty());
      }

      @Test
      void getById_returns404WhenNotFound() throws Exception {
          UUID unknown = UUID.randomUUID();
          when(tradeQueryService.getById(unknown))
                  .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

          mvc.perform(get("/api/trades/{id}", unknown))
                  .andExpect(status().isNotFound());
      }
  }
  ```

- [ ] **Step 2: Lancer le test pour confirmer qu'il échoue**

  ```bash
  ./mvnw test -Dtest=TradeControllerTest -q
  ```
  Attendu : FAIL — `TradeController` n'existe pas encore.

- [ ] **Step 3: Créer TradeQueryService.java**

  ```java
  package com.halaltrader.backend.service;

  import com.halaltrader.backend.dto.TradeDetailDto;
  import com.halaltrader.backend.dto.TradeDto;
  import com.halaltrader.backend.entity.Trade;
  import com.halaltrader.backend.repository.TradeRepository;
  import lombok.RequiredArgsConstructor;
  import org.springframework.data.domain.Page;
  import org.springframework.data.domain.PageRequest;
  import org.springframework.data.domain.Pageable;
  import org.springframework.data.domain.Sort;
  import org.springframework.http.HttpStatus;
  import org.springframework.stereotype.Service;
  import org.springframework.web.server.ResponseStatusException;

  import java.util.UUID;

  @Service
  @RequiredArgsConstructor
  public class TradeQueryService {

      private final TradeRepository tradeRepository;
      private final PortfolioQueryService portfolioQueryService;

      public Page<TradeDto> list(int page, int size) {
          var portfolio = portfolioQueryService.getPortfolio();
          Pageable pageable = PageRequest.of(page, size, Sort.by("executedAt").descending());
          return tradeRepository.findByPortfolio(portfolio, pageable)
                  .map(this::toDto);
      }

      public TradeDetailDto getById(UUID id) {
          Trade t = tradeRepository.findById(id)
                  .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trade not found"));
          return new TradeDetailDto(
                  t.getId(), t.getAsset().getSymbol(), t.getAction().name(),
                  t.getQuantity(), t.getPrice(), t.getTotalAmount(),
                  t.getSimulatedPnl(), t.getExecutedAt(),
                  t.getAiReasoning(), t.getTechnicalData());
      }

      private TradeDto toDto(Trade t) {
          return new TradeDto(
                  t.getId(), t.getAsset().getSymbol(), t.getAction().name(),
                  t.getQuantity(), t.getPrice(), t.getTotalAmount(),
                  t.getSimulatedPnl(), t.getExecutedAt());
      }
  }
  ```

- [ ] **Step 4: Créer TradeController.java**

  ```java
  package com.halaltrader.backend.controller;

  import com.halaltrader.backend.dto.TradeDetailDto;
  import com.halaltrader.backend.dto.TradeDto;
  import com.halaltrader.backend.service.TradeQueryService;
  import lombok.RequiredArgsConstructor;
  import org.springframework.data.domain.Page;
  import org.springframework.web.bind.annotation.*;

  import java.util.UUID;

  @RestController
  @RequestMapping("/api/trades")
  @RequiredArgsConstructor
  public class TradeController {

      private final TradeQueryService tradeQueryService;

      @GetMapping
      public Page<TradeDto> list(
              @RequestParam(defaultValue = "0") int page,
              @RequestParam(defaultValue = "20") int size) {
          return tradeQueryService.list(page, size);
      }

      @GetMapping("/{id}")
      public TradeDetailDto getById(@PathVariable UUID id) {
          return tradeQueryService.getById(id);
      }
  }
  ```

- [ ] **Step 5: Lancer le test pour confirmer qu'il passe**

  ```bash
  ./mvnw test -Dtest=TradeControllerTest -q
  ```
  Attendu : 3 tests PASS.

- [ ] **Step 6: Commit**

  ```bash
  git add src/main/java/com/halaltrader/backend/service/TradeQueryService.java \
          src/main/java/com/halaltrader/backend/controller/TradeController.java \
          src/test/java/com/halaltrader/backend/controller/TradeControllerTest.java
  git commit -m "feat: add GET /api/trades and GET /api/trades/{id}"
  ```

---

## Task 6: Assets API

**Files:**
- Create: `src/main/java/com/halaltrader/backend/service/AssetQueryService.java`
- Create: `src/main/java/com/halaltrader/backend/controller/AssetController.java`
- Create: `src/test/java/com/halaltrader/backend/controller/AssetControllerTest.java`

- [ ] **Step 1: Écrire le test qui échoue**

  ```java
  package com.halaltrader.backend.controller;

  import com.halaltrader.backend.config.AnthropicProperties;
  import com.halaltrader.backend.dto.AssetDto;
  import com.halaltrader.backend.service.AssetQueryService;
  import org.junit.jupiter.api.Test;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
  import org.springframework.boot.test.mock.mockito.MockBean;
  import org.springframework.test.web.servlet.MockMvc;

  import java.util.List;
  import java.util.UUID;

  import static org.mockito.Mockito.when;
  import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

  @WebMvcTest(AssetController.class)
  class AssetControllerTest {

      @Autowired MockMvc mvc;
      @MockBean AssetQueryService assetQueryService;
      @MockBean AnthropicProperties anthropicProperties;

      @Test
      void list_returns200WithAssets() throws Exception {
          var dto = new AssetDto(UUID.randomUUID(), "AAPL", "Apple Inc.",
                  "STOCK", "APPROVED", "Technology sector", "Technology");
          when(assetQueryService.list()).thenReturn(List.of(dto));

          mvc.perform(get("/api/assets"))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                  .andExpect(jsonPath("$[0].halalScreening").value("APPROVED"));
      }

      @Test
      void list_returnsEmptyWhenNoAssets() throws Exception {
          when(assetQueryService.list()).thenReturn(List.of());

          mvc.perform(get("/api/assets"))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$").isEmpty());
      }
  }
  ```

- [ ] **Step 2: Lancer le test pour confirmer qu'il échoue**

  ```bash
  ./mvnw test -Dtest=AssetControllerTest -q
  ```
  Attendu : FAIL.

- [ ] **Step 3: Créer AssetQueryService.java**

  ```java
  package com.halaltrader.backend.service;

  import com.halaltrader.backend.dto.AssetDto;
  import com.halaltrader.backend.repository.AssetRepository;
  import lombok.RequiredArgsConstructor;
  import org.springframework.stereotype.Service;

  import java.util.List;

  @Service
  @RequiredArgsConstructor
  public class AssetQueryService {

      private final AssetRepository assetRepository;

      public List<AssetDto> list() {
          return assetRepository.findAll().stream()
                  .map(a -> new AssetDto(
                          a.getId(), a.getSymbol(), a.getName(),
                          a.getAssetType().name(), a.getHalalScreening().name(),
                          a.getHalalReason(), a.getSector()))
                  .toList();
      }
  }
  ```

- [ ] **Step 4: Créer AssetController.java**

  ```java
  package com.halaltrader.backend.controller;

  import com.halaltrader.backend.dto.AssetDto;
  import com.halaltrader.backend.service.AssetQueryService;
  import lombok.RequiredArgsConstructor;
  import org.springframework.web.bind.annotation.GetMapping;
  import org.springframework.web.bind.annotation.RequestMapping;
  import org.springframework.web.bind.annotation.RestController;

  import java.util.List;

  @RestController
  @RequestMapping("/api/assets")
  @RequiredArgsConstructor
  public class AssetController {

      private final AssetQueryService assetQueryService;

      @GetMapping
      public List<AssetDto> list() {
          return assetQueryService.list();
      }
  }
  ```

- [ ] **Step 5: Lancer le test pour confirmer qu'il passe**

  ```bash
  ./mvnw test -Dtest=AssetControllerTest -q
  ```
  Attendu : 2 tests PASS.

- [ ] **Step 6: Commit**

  ```bash
  git add src/main/java/com/halaltrader/backend/service/AssetQueryService.java \
          src/main/java/com/halaltrader/backend/controller/AssetController.java \
          src/test/java/com/halaltrader/backend/controller/AssetControllerTest.java
  git commit -m "feat: add GET /api/assets"
  ```

---

## Task 7: Performance API

**Files:**
- Create: `src/main/java/com/halaltrader/backend/service/PerformanceQueryService.java`
- Create: `src/main/java/com/halaltrader/backend/controller/PerformanceController.java`
- Create: `src/test/java/com/halaltrader/backend/controller/PerformanceControllerTest.java`

- [ ] **Step 1: Écrire le test qui échoue**

  ```java
  package com.halaltrader.backend.controller;

  import com.halaltrader.backend.config.AnthropicProperties;
  import com.halaltrader.backend.dto.PerformanceDto;
  import com.halaltrader.backend.service.PerformanceQueryService;
  import org.junit.jupiter.api.Test;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
  import org.springframework.boot.test.mock.mockito.MockBean;
  import org.springframework.test.web.servlet.MockMvc;

  import java.math.BigDecimal;
  import java.time.LocalDateTime;
  import java.util.List;

  import static org.mockito.Mockito.when;
  import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

  @WebMvcTest(PerformanceController.class)
  class PerformanceControllerTest {

      @Autowired MockMvc mvc;
      @MockBean PerformanceQueryService performanceQueryService;
      @MockBean AnthropicProperties anthropicProperties;

      @Test
      void getPerformance_returns200WithStats() throws Exception {
          var dto = new PerformanceDto(
                  List.of(new PerformanceDto.DailyPnlEntry("2026-03-21", new BigDecimal("50.00"))),
                  new BigDecimal("60.0"), 10,
                  new PerformanceDto.AssetPnlEntry("AAPL", new BigDecimal("200.00")),
                  new PerformanceDto.AssetPnlEntry("GLD", new BigDecimal("-50.00")),
                  new BigDecimal("80.0"), LocalDateTime.now(), 4, 5);
          when(performanceQueryService.getPerformance()).thenReturn(dto);

          mvc.perform(get("/api/performance"))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.winRate").value(60.0))
                  .andExpect(jsonPath("$.totalTrades").value(10))
                  .andExpect(jsonPath("$.dailyPnl[0].date").value("2026-03-21"))
                  .andExpect(jsonPath("$.bestAsset.symbol").value("AAPL"))
                  .andExpect(jsonPath("$.halalApprovalRate").value(80.0));
      }

      @Test
      void getPerformance_returnsEmptyWhenNoTrades() throws Exception {
          var dto = new PerformanceDto(
                  List.of(), BigDecimal.ZERO, 0,
                  null, null, BigDecimal.ZERO, null, 0, 0);
          when(performanceQueryService.getPerformance()).thenReturn(dto);

          mvc.perform(get("/api/performance"))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.totalTrades").value(0))
                  .andExpect(jsonPath("$.dailyPnl").isEmpty());
      }
  }
  ```

- [ ] **Step 2: Lancer le test pour confirmer qu'il échoue**

  ```bash
  ./mvnw test -Dtest=PerformanceControllerTest -q
  ```
  Attendu : FAIL.

- [ ] **Step 3: Créer PerformanceQueryService.java**

  ```java
  package com.halaltrader.backend.service;

  import com.halaltrader.backend.dto.PerformanceDto;
  import com.halaltrader.backend.dto.PerformanceDto.AssetPnlEntry;
  import com.halaltrader.backend.dto.PerformanceDto.DailyPnlEntry;
  import com.halaltrader.backend.entity.HalalScreening;
  import com.halaltrader.backend.entity.Trade;
  import com.halaltrader.backend.repository.AssetRepository;
  import com.halaltrader.backend.repository.TradeRepository;
  import lombok.RequiredArgsConstructor;
  import org.springframework.stereotype.Service;

  import java.math.BigDecimal;
  import java.math.RoundingMode;
  import java.time.LocalDate;
  import java.time.LocalDateTime;
  import java.util.*;

  @Service
  @RequiredArgsConstructor
  public class PerformanceQueryService {

      private final PortfolioQueryService portfolioQueryService;
      private final TradeRepository tradeRepository;
      private final AssetRepository assetRepository;

      public PerformanceDto getPerformance() {
          var portfolio = portfolioQueryService.getPortfolio();
          List<Trade> allTrades = tradeRepository.findByPortfolioOrderByExecutedAtAsc(portfolio);

          // Courbe P&L cumulatif par jour
          Map<LocalDate, BigDecimal> dailyMap = new LinkedHashMap<>();
          for (Trade t : allTrades) {
              if (t.getSimulatedPnl() != null && t.getExecutedAt() != null) {
                  LocalDate date = t.getExecutedAt().toLocalDate();
                  dailyMap.merge(date, t.getSimulatedPnl(), BigDecimal::add);
              }
          }
          List<DailyPnlEntry> dailyPnl = new ArrayList<>();
          BigDecimal cumulative = BigDecimal.ZERO;
          for (var entry : dailyMap.entrySet()) {
              cumulative = cumulative.add(entry.getValue());
              dailyPnl.add(new DailyPnlEntry(entry.getKey().toString(), cumulative));
          }

          // Win rate
          List<Trade> pnlTrades = allTrades.stream()
                  .filter(t -> t.getSimulatedPnl() != null)
                  .toList();
          long winCount = pnlTrades.stream()
                  .filter(t -> t.getSimulatedPnl().compareTo(BigDecimal.ZERO) > 0)
                  .count();
          BigDecimal winRate = pnlTrades.isEmpty() ? BigDecimal.ZERO
                  : BigDecimal.valueOf(winCount * 100.0 / pnlTrades.size())
                              .setScale(1, RoundingMode.HALF_UP);

          // Meilleur / pire actif
          Map<String, BigDecimal> pnlByAsset = new HashMap<>();
          for (Trade t : pnlTrades) {
              pnlByAsset.merge(t.getAsset().getSymbol(), t.getSimulatedPnl(), BigDecimal::add);
          }
          AssetPnlEntry bestAsset = pnlByAsset.entrySet().stream()
                  .max(Map.Entry.comparingByValue())
                  .map(e -> new AssetPnlEntry(e.getKey(), e.getValue()))
                  .orElse(null);
          AssetPnlEntry worstAsset = pnlByAsset.entrySet().stream()
                  .min(Map.Entry.comparingByValue())
                  .map(e -> new AssetPnlEntry(e.getKey(), e.getValue()))
                  .orElse(null);

          // Taux d'approbation halal
          var allAssets = assetRepository.findAll();
          long approvedCount = allAssets.stream()
                  .filter(a -> a.getHalalScreening() == HalalScreening.APPROVED)
                  .count();
          BigDecimal halalRate = allAssets.isEmpty() ? BigDecimal.ZERO
                  : BigDecimal.valueOf(approvedCount * 100.0 / allAssets.size())
                              .setScale(1, RoundingMode.HALF_UP);

          // Dernier cycle
          LocalDateTime lastCycleAt = allTrades.stream()
                  .map(Trade::getExecutedAt)
                  .filter(Objects::nonNull)
                  .max(Comparator.naturalOrder())
                  .orElse(null);

          return new PerformanceDto(
                  dailyPnl, winRate, allTrades.size(),
                  bestAsset, worstAsset, halalRate, lastCycleAt,
                  (int) approvedCount, allAssets.size());
      }
  }
  ```

- [ ] **Step 4: Créer PerformanceController.java**

  ```java
  package com.halaltrader.backend.controller;

  import com.halaltrader.backend.dto.PerformanceDto;
  import com.halaltrader.backend.service.PerformanceQueryService;
  import lombok.RequiredArgsConstructor;
  import org.springframework.web.bind.annotation.GetMapping;
  import org.springframework.web.bind.annotation.RequestMapping;
  import org.springframework.web.bind.annotation.RestController;

  @RestController
  @RequestMapping("/api/performance")
  @RequiredArgsConstructor
  public class PerformanceController {

      private final PerformanceQueryService performanceQueryService;

      @GetMapping
      public PerformanceDto getPerformance() {
          return performanceQueryService.getPerformance();
      }
  }
  ```

- [ ] **Step 5: Lancer le test pour confirmer qu'il passe**

  ```bash
  ./mvnw test -Dtest=PerformanceControllerTest -q
  ```
  Attendu : 2 tests PASS.

- [ ] **Step 6: Lancer tous les tests pour confirmer qu'aucune régression**

  ```bash
  ./mvnw test -q
  ```
  Attendu : tous les tests PASS.

- [ ] **Step 7: Commit**

  ```bash
  git add src/main/java/com/halaltrader/backend/service/PerformanceQueryService.java \
          src/main/java/com/halaltrader/backend/controller/PerformanceController.java \
          src/test/java/com/halaltrader/backend/controller/PerformanceControllerTest.java
  git commit -m "feat: add GET /api/performance with cumulative P&L curve"
  ```

---

## Vérification finale

- [ ] Lancer l'application : `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`
- [ ] Vérifier les endpoints dans un navigateur ou avec curl :
  - `http://localhost:8080/api/portfolio`
  - `http://localhost:8080/api/portfolio/positions`
  - `http://localhost:8080/api/trades`
  - `http://localhost:8080/api/assets`
  - `http://localhost:8080/api/performance`
- [ ] Confirmer que les réponses sont du JSON valide avec les bons champs

---

## Décisions intentionnelles

- **`PositionDto` sans `pnl`/`pnlPct`** : pas de prix live disponible sans appel au market-data service. La valeur de position = `quantity × avgPrice`. Le P&L global est dans la page Performance via la courbe cumulative.
- **`PerformanceDto` sans `nextCycleAt`** : calculer la prochaine exécution nécessite de parser l'expression cron — hors scope Phase 3A.
- **`lastCycleAt`** : dérivé du `executedAt` du dernier trade. Si un cycle ne produit aucun trade (tous HOLD), cette valeur ne se met pas à jour — acceptable pour Phase 3A.

---

> **Suite :** Plan 3B (frontend React) dans `docs/superpowers/plans/2026-03-21-phase3b-frontend-react.md`
