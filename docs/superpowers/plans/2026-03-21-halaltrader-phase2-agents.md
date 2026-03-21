# HalalTrader Phase 2 — AI Trading Agents Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implémenter un pipeline multi-agents IA (Anthropic API) qui analyse les actifs halal, évalue le risque et exécute des trades simulés sur un scheduler cron.

**Architecture:** `TradingOrchestrator` (@Service) est déclenché toutes les 30 min par `TradingScheduler`. Pour chaque actif APPROVED en base : (1) `HalalScreenerAgent` vérifie la conformité — pipeline stoppé si rejected. (2) `MarketAnalystAgent` récupère les données live depuis le service market-data et produit un rapport technique. (3) `RiskManagerAgent` calcule le risque de position par rapport à l'état du portefeuille. (4) `DecisionAgent` synthétise les 3 rapports et retourne BUY/SELL/HOLD. (5) `TradeExecutionService` persiste le trade et met à jour le portefeuille. (6) `PerformanceAgent` calcule le PnL. Tout le raisonnement IA est sauvegardé en base (champs `ai_reasoning` et `technical_data` de `Trade`) pour audit.

**Tech Stack:** Spring Boot 3.3.5, WebFlux WebClient, Anthropic Messages API, Jackson (inclus via starter-web), JUnit 5 + Mockito (inclus via starter-test), OkHttp MockWebServer (dépendance test à ajouter).

---

## File Map

| Fichier | Action | Responsabilité |
|---|---|---|
| `pom.xml` | Modify | Ajouter dépendance test MockWebServer |
| `src/main/resources/application.yml` | Modify | Ajouter modèles par agent |
| `src/main/java/.../config/AnthropicProperties.java` | Create | @ConfigurationProperties("anthropic") |
| `src/main/java/.../config/WebClientConfig.java` | Create | WebClient beans (Anthropic + MarketData) |
| `src/main/java/.../dto/MarketDataDto.java` | Create | record: données marché (price, RSI, MACD…) |
| `src/main/java/.../dto/HalalReport.java` | Create | record: approved, reason |
| `src/main/java/.../dto/MarketAnalysisReport.java` | Create | record: trend, momentum, summary |
| `src/main/java/.../dto/RiskReport.java` | Create | record: level, maxQuantity, reason |
| `src/main/java/.../dto/TradeDecision.java` | Create | record: action, quantity, reasoning |
| `src/main/java/.../client/AnthropicClient.java` | Create | call(model, system, user, maxTokens) → String |
| `src/main/java/.../client/MarketDataClient.java` | Create | price(symbol) → MarketDataDto |
| `src/main/java/.../agent/HalalScreenerAgent.java` | Create | claude-opus-4-6 — conformité charia |
| `src/main/java/.../agent/MarketAnalystAgent.java` | Create | claude-sonnet-4-6 — analyse technique |
| `src/main/java/.../agent/RiskManagerAgent.java` | Create | claude-sonnet-4-6 — calcul risque |
| `src/main/java/.../agent/DecisionAgent.java` | Create | claude-opus-4-6 — décision finale |
| `src/main/java/.../agent/PerformanceAgent.java` | Create | claude-haiku-4-5-20251001 — métriques PnL |
| `src/main/java/.../service/TradeExecutionService.java` | Create | @Transactional BUY/SELL, update portfolio |
| `src/main/java/.../service/TradingOrchestrator.java` | Create | pipeline complet par actif |
| `src/main/java/.../scheduler/TradingScheduler.java` | Create | @Scheduled cron trigger |
| `src/test/java/.../client/AnthropicClientTest.java` | Create | MockWebServer — test HTTP |
| `src/test/java/.../client/MarketDataClientTest.java` | Create | MockWebServer — test HTTP |
| `src/test/java/.../agent/HalalScreenerAgentTest.java` | Create | Mockito — mock AnthropicClient |
| `src/test/java/.../agent/MarketAnalystAgentTest.java` | Create | Mockito |
| `src/test/java/.../agent/RiskManagerAgentTest.java` | Create | Mockito |
| `src/test/java/.../agent/DecisionAgentTest.java` | Create | Mockito |
| `src/test/java/.../agent/PerformanceAgentTest.java` | Create | Mockito |
| `src/test/java/.../service/TradeExecutionServiceTest.java` | Create | Mockito — mock repos |
| `src/test/java/.../service/TradingOrchestratorTest.java` | Create | Mockito — mock agents + service |

> Pas de Flyway V2 : les colonnes `ai_reasoning` et `technical_data` (TEXT) dans `trades` sont déjà prévues pour l'audit.

---

## Task 1 — Config & DTOs

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yml`
- Create: `src/main/java/com/halaltrader/backend/config/AnthropicProperties.java`
- Create: `src/main/java/com/halaltrader/backend/config/WebClientConfig.java`
- Create: `src/main/java/com/halaltrader/backend/dto/MarketDataDto.java`
- Create: `src/main/java/com/halaltrader/backend/dto/HalalReport.java`
- Create: `src/main/java/com/halaltrader/backend/dto/MarketAnalysisReport.java`
- Create: `src/main/java/com/halaltrader/backend/dto/RiskReport.java`
- Create: `src/main/java/com/halaltrader/backend/dto/TradeDecision.java`

- [ ] **Step 1: Ajouter dépendance test MockWebServer dans pom.xml**

  Ajouter dans `<dependencies>` :

  ```xml
  <dependency>
      <groupId>com.squareup.okhttp3</groupId>
      <artifactId>mockwebserver</artifactId>
      <version>4.12.0</version>
      <scope>test</scope>
  </dependency>
  ```

- [ ] **Step 2: Mettre à jour application.yml**

  Remplacer la section `anthropic:` par :

  ```yaml
  anthropic:
    api-key: ${ANTHROPIC_API_KEY:}
    models:
      halal-screener: claude-opus-4-6
      market-analyst: claude-sonnet-4-6
      risk-manager: claude-sonnet-4-6
      decision: claude-opus-4-6
      performance: claude-haiku-4-5-20251001
  ```

  Conserver le reste du fichier inchangé.

- [ ] **Step 3: Créer AnthropicProperties.java**

  ```java
  package com.halaltrader.backend.config;

  import org.springframework.boot.context.properties.ConfigurationProperties;
  import org.springframework.stereotype.Component;
  import java.util.Map;

  @Component
  @ConfigurationProperties(prefix = "anthropic")
  public class AnthropicProperties {

      private String apiKey;
      private Map<String, String> models = Map.of();

      public String getApiKey() { return apiKey; }
      public void setApiKey(String apiKey) { this.apiKey = apiKey; }
      public Map<String, String> getModels() { return models; }
      public void setModels(Map<String, String> models) { this.models = models; }

      public String model(String agentKey) {
          return models.getOrDefault(agentKey, "claude-haiku-4-5-20251001");
      }
  }
  ```

- [ ] **Step 4: Créer WebClientConfig.java**

  ```java
  package com.halaltrader.backend.config;

  import org.springframework.beans.factory.annotation.Value;
  import org.springframework.context.annotation.Bean;
  import org.springframework.context.annotation.Configuration;
  import org.springframework.web.reactive.function.client.WebClient;

  @Configuration
  public class WebClientConfig {

      @Bean("anthropicWebClient")
      public WebClient anthropicWebClient(AnthropicProperties props) {
          return WebClient.builder()
                  .baseUrl("https://api.anthropic.com")
                  .defaultHeader("x-api-key", props.getApiKey())
                  .defaultHeader("anthropic-version", "2023-06-01")
                  .defaultHeader("content-type", "application/json")
                  .build();
      }

      @Bean("marketDataWebClient")
      public WebClient marketDataWebClient(
              @Value("${market-data.base-url:http://localhost:8081}") String baseUrl) {
          return WebClient.builder()
                  .baseUrl(baseUrl)
                  .build();
      }
  }
  ```

- [ ] **Step 5: Créer les DTOs (records Java)**

  `MarketDataDto.java` :
  ```java
  package com.halaltrader.backend.dto;

  import java.util.List;

  public record MarketDataDto(
          String symbol,
          double price,
          double changePct,
          long volume,
          double rsi,
          double macd,
          double macdSignal,
          double ma20,
          double ma50,
          List<String> newsTitles
  ) {}
  ```

  `HalalReport.java` :
  ```java
  package com.halaltrader.backend.dto;

  public record HalalReport(boolean approved, String reason) {}
  ```

  `MarketAnalysisReport.java` :
  ```java
  package com.halaltrader.backend.dto;

  public record MarketAnalysisReport(String trend, String momentum, String summary) {}
  ```

  `RiskReport.java` :
  ```java
  package com.halaltrader.backend.dto;

  import java.math.BigDecimal;

  public record RiskReport(String level, BigDecimal maxQuantity, String reason) {}
  ```

  `TradeDecision.java` :
  ```java
  package com.halaltrader.backend.dto;

  import com.halaltrader.backend.entity.TradeAction;
  import java.math.BigDecimal;

  public record TradeDecision(TradeAction action, BigDecimal quantity, String reasoning) {}
  ```

- [ ] **Step 6: Vérifier la compilation**

  ```bash
  mvn compile -q
  ```
  Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

  ```bash
  git add pom.xml src/main/resources/application.yml \
    src/main/java/com/halaltrader/backend/config/ \
    src/main/java/com/halaltrader/backend/dto/
  git commit -m "feat: add Anthropic config, WebClient beans, and Phase 2 DTOs"
  ```

---

## Task 2 — AnthropicClient (TDD)

**Files:**
- Create: `src/main/java/com/halaltrader/backend/client/AnthropicClient.java`
- Create: `src/test/java/com/halaltrader/backend/client/AnthropicClientTest.java`

- [ ] **Step 1: Écrire le test en premier**

  ```java
  package com.halaltrader.backend.client;

  import com.halaltrader.backend.config.AnthropicProperties;
  import com.halaltrader.backend.config.WebClientConfig;
  import okhttp3.mockwebserver.MockResponse;
  import okhttp3.mockwebserver.MockWebServer;
  import org.junit.jupiter.api.AfterEach;
  import org.junit.jupiter.api.BeforeEach;
  import org.junit.jupiter.api.Test;
  import org.springframework.web.reactive.function.client.WebClient;

  import java.io.IOException;
  import java.util.Map;

  import static org.assertj.core.api.Assertions.assertThat;

  class AnthropicClientTest {

      private MockWebServer server;
      private AnthropicClient client;

      @BeforeEach
      void setUp() throws IOException {
          server = new MockWebServer();
          server.start();
          WebClient webClient = WebClient.builder()
                  .baseUrl(server.url("/").toString())
                  .defaultHeader("content-type", "application/json")
                  .build();
          client = new AnthropicClient(webClient);
      }

      @AfterEach
      void tearDown() throws IOException {
          server.shutdown();
      }

      @Test
      void call_returnsContentText() {
          server.enqueue(new MockResponse()
                  .setBody("""
                      {"content":[{"type":"text","text":"{\\"approved\\":true}"}]}
                      """)
                  .addHeader("Content-Type", "application/json"));

          String result = client.call("claude-haiku-4-5-20251001", "system", "user msg", 300);

          assertThat(result).isEqualTo("{\"approved\":true}");
      }
  }
  ```

- [ ] **Step 2: Vérifier que le test échoue (classe inexistante)**

  ```bash
  mvn test -pl . -Dtest=AnthropicClientTest -q 2>&1 | tail -5
  ```
  Expected: compilation error "cannot find symbol AnthropicClient"

- [ ] **Step 3: Créer AnthropicClient.java**

  ```java
  package com.halaltrader.backend.client;

  import com.fasterxml.jackson.databind.JsonNode;
  import com.fasterxml.jackson.databind.ObjectMapper;
  import com.fasterxml.jackson.databind.node.ArrayNode;
  import com.fasterxml.jackson.databind.node.ObjectNode;
  import org.slf4j.Logger;
  import org.slf4j.LoggerFactory;
  import org.springframework.beans.factory.annotation.Qualifier;
  import org.springframework.stereotype.Component;
  import org.springframework.web.reactive.function.client.WebClient;

  @Component
  public class AnthropicClient {

      private static final Logger log = LoggerFactory.getLogger(AnthropicClient.class);
      private static final String MESSAGES_PATH = "/v1/messages";

      private final WebClient webClient;
      private final ObjectMapper objectMapper = new ObjectMapper();

      public AnthropicClient(@Qualifier("anthropicWebClient") WebClient webClient) {
          this.webClient = webClient;
      }

      public String call(String model, String systemPrompt, String userMessage, int maxTokens) {
          ObjectNode body = objectMapper.createObjectNode();
          body.put("model", model);
          body.put("max_tokens", maxTokens);
          body.put("system", systemPrompt);

          ArrayNode messages = body.putArray("messages");
          ObjectNode msg = messages.addObject();
          msg.put("role", "user");
          msg.put("content", userMessage);

          try {
              String response = webClient.post()
                      .uri(MESSAGES_PATH)
                      .bodyValue(body.toString())
                      .retrieve()
                      .bodyToMono(String.class)
                      .block();

              JsonNode root = objectMapper.readTree(response);
              return root.path("content").get(0).path("text").asText();
          } catch (Exception e) {
              log.error("[AnthropicClient] call failed for model={}: {}", model, e.getMessage());
              throw new RuntimeException("Anthropic API call failed", e);
          }
      }
  }
  ```

- [ ] **Step 4: Lancer le test**

  ```bash
  mvn test -Dtest=AnthropicClientTest -q
  ```
  Expected: BUILD SUCCESS, 1 test passed

- [ ] **Step 5: Commit**

  ```bash
  git add src/main/java/com/halaltrader/backend/client/AnthropicClient.java \
    src/test/java/com/halaltrader/backend/client/AnthropicClientTest.java
  git commit -m "feat: add AnthropicClient with Anthropic Messages API integration"
  ```

---

## Task 3 — MarketDataClient (TDD)

**Files:**
- Create: `src/main/java/com/halaltrader/backend/client/MarketDataClient.java`
- Create: `src/test/java/com/halaltrader/backend/client/MarketDataClientTest.java`

- [ ] **Step 1: Écrire le test**

  ```java
  package com.halaltrader.backend.client;

  import com.halaltrader.backend.dto.MarketDataDto;
  import okhttp3.mockwebserver.MockResponse;
  import okhttp3.mockwebserver.MockWebServer;
  import org.junit.jupiter.api.AfterEach;
  import org.junit.jupiter.api.BeforeEach;
  import org.junit.jupiter.api.Test;
  import org.springframework.web.reactive.function.client.WebClient;

  import java.io.IOException;

  import static org.assertj.core.api.Assertions.assertThat;

  class MarketDataClientTest {

      private MockWebServer server;
      private MarketDataClient client;

      @BeforeEach
      void setUp() throws IOException {
          server = new MockWebServer();
          server.start();
          WebClient webClient = WebClient.builder()
                  .baseUrl(server.url("/").toString())
                  .build();
          client = new MarketDataClient(webClient);
      }

      @AfterEach
      void tearDown() throws IOException {
          server.shutdown();
      }

      @Test
      void getPrice_mapsFieldsCorrectly() {
          server.enqueue(new MockResponse()
                  .setBody("""
                      {"symbol":"AAPL","price":175.5,"change_pct":1.2,"volume":50000000,
                       "rsi":58.3,"macd":1.23,"macd_signal":0.98,"ma20":172.0,"ma50":168.0}
                      """)
                  .addHeader("Content-Type", "application/json"));

          // Mock news endpoint
          server.enqueue(new MockResponse()
                  .setBody("""
                      [{"title":"Apple hits record"},{"title":"WWDC announced"}]
                      """)
                  .addHeader("Content-Type", "application/json"));

          MarketDataDto dto = client.getMarketData("AAPL");

          assertThat(dto.symbol()).isEqualTo("AAPL");
          assertThat(dto.price()).isEqualTo(175.5);
          assertThat(dto.rsi()).isEqualTo(58.3);
          assertThat(dto.newsTitles()).hasSize(2);
          assertThat(dto.newsTitles().get(0)).isEqualTo("Apple hits record");
      }
  }
  ```

- [ ] **Step 2: Vérifier que le test échoue**

  ```bash
  mvn test -Dtest=MarketDataClientTest -q 2>&1 | tail -5
  ```
  Expected: compilation error

- [ ] **Step 3: Créer MarketDataClient.java**

  ```java
  package com.halaltrader.backend.client;

  import com.fasterxml.jackson.databind.JsonNode;
  import com.fasterxml.jackson.databind.ObjectMapper;
  import com.halaltrader.backend.dto.MarketDataDto;
  import org.slf4j.Logger;
  import org.slf4j.LoggerFactory;
  import org.springframework.beans.factory.annotation.Qualifier;
  import org.springframework.stereotype.Component;
  import org.springframework.web.reactive.function.client.WebClient;

  import java.util.ArrayList;
  import java.util.List;

  @Component
  public class MarketDataClient {

      private static final Logger log = LoggerFactory.getLogger(MarketDataClient.class);

      private final WebClient webClient;
      private final ObjectMapper objectMapper = new ObjectMapper();

      public MarketDataClient(@Qualifier("marketDataWebClient") WebClient webClient) {
          this.webClient = webClient;
      }

      public MarketDataDto getMarketData(String symbol) {
          try {
              String priceJson = webClient.get()
                      .uri("/price/{symbol}", symbol)
                      .retrieve()
                      .bodyToMono(String.class)
                      .block();

              JsonNode price = objectMapper.readTree(priceJson);

              List<String> news = fetchNewsTitles(symbol);

              return new MarketDataDto(
                      price.path("symbol").asText(),
                      price.path("price").asDouble(),
                      price.path("change_pct").asDouble(),
                      price.path("volume").asLong(),
                      price.path("rsi").asDouble(),
                      price.path("macd").asDouble(),
                      price.path("macd_signal").asDouble(),
                      price.path("ma20").asDouble(),
                      price.path("ma50").asDouble(),
                      news
              );
          } catch (Exception e) {
              log.error("[MarketDataClient] failed to fetch data for {}: {}", symbol, e.getMessage());
              throw new RuntimeException("MarketData fetch failed for " + symbol, e);
          }
      }

      private List<String> fetchNewsTitles(String symbol) {
          try {
              String newsJson = webClient.get()
                      .uri("/news/{symbol}", symbol)
                      .retrieve()
                      .bodyToMono(String.class)
                      .block();

              JsonNode newsArray = objectMapper.readTree(newsJson);
              List<String> titles = new ArrayList<>();
              for (JsonNode item : newsArray) {
                  String title = item.path("title").asText();
                  if (!title.isBlank()) titles.add(title);
              }
              return titles;
          } catch (Exception e) {
              log.warn("[MarketDataClient] news fetch failed for {}: {}", symbol, e.getMessage());
              return List.of();
          }
      }
  }
  ```

- [ ] **Step 4: Lancer le test**

  ```bash
  mvn test -Dtest=MarketDataClientTest -q
  ```
  Expected: BUILD SUCCESS, 1 test passed

- [ ] **Step 5: Commit**

  ```bash
  git add src/main/java/com/halaltrader/backend/client/MarketDataClient.java \
    src/test/java/com/halaltrader/backend/client/MarketDataClientTest.java
  git commit -m "feat: add MarketDataClient for market-data microservice integration"
  ```

---

## Task 4 — HalalScreenerAgent (TDD)

**Files:**
- Create: `src/main/java/com/halaltrader/backend/agent/HalalScreenerAgent.java`
- Create: `src/test/java/com/halaltrader/backend/agent/HalalScreenerAgentTest.java`

- [ ] **Step 1: Écrire le test**

  ```java
  package com.halaltrader.backend.agent;

  import com.halaltrader.backend.client.AnthropicClient;
  import com.halaltrader.backend.dto.HalalReport;
  import org.junit.jupiter.api.Test;
  import org.junit.jupiter.api.extension.ExtendWith;
  import org.mockito.InjectMocks;
  import org.mockito.Mock;
  import org.mockito.junit.jupiter.MockitoExtension;

  import static org.assertj.core.api.Assertions.assertThat;
  import static org.mockito.ArgumentMatchers.*;
  import static org.mockito.Mockito.when;

  @ExtendWith(MockitoExtension.class)
  class HalalScreenerAgentTest {

      @Mock AnthropicClient anthropicClient;
      @Mock com.halaltrader.backend.config.AnthropicProperties props;
      @InjectMocks HalalScreenerAgent agent;

      @Test
      void analyze_approvedAsset_returnsApprovedTrue() {
          when(anthropicClient.call(anyString(), anyString(), anyString(), anyInt()))
                  .thenReturn("{\"approved\":true,\"reason\":\"ETF islamique certifié\"}");

          HalalReport report = agent.analyze("ISWD.L", "iShares MSCI World Islamic ETF", "ETF", "Islamic");

          assertThat(report.approved()).isTrue();
          assertThat(report.reason()).isNotBlank();
      }

      @Test
      void analyze_rejectedAsset_returnsApprovedFalse() {
          when(anthropicClient.call(anyString(), anyString(), anyString(), anyInt()))
                  .thenReturn("{\"approved\":false,\"reason\":\"Secteur alcool interdit\"}");

          HalalReport report = agent.analyze("BUD", "Anheuser-Busch", "STOCK", "Alcohol");

          assertThat(report.approved()).isFalse();
      }
  }
  ```

- [ ] **Step 2: Vérifier que le test échoue**

  ```bash
  mvn test -Dtest=HalalScreenerAgentTest -q 2>&1 | tail -5
  ```

- [ ] **Step 3: Créer HalalScreenerAgent.java**

  ```java
  package com.halaltrader.backend.agent;

  import com.fasterxml.jackson.databind.JsonNode;
  import com.fasterxml.jackson.databind.ObjectMapper;
  import com.halaltrader.backend.client.AnthropicClient;
  import com.halaltrader.backend.config.AnthropicProperties;
  import com.halaltrader.backend.dto.HalalReport;
  import org.slf4j.Logger;
  import org.slf4j.LoggerFactory;
  import org.springframework.stereotype.Component;

  @Component
  public class HalalScreenerAgent {

      private static final Logger log = LoggerFactory.getLogger(HalalScreenerAgent.class);
      private static final int MAX_TOKENS = 600;
      private static final String SYSTEM_PROMPT = """
              You are a halal finance compliance expert. Your sole task is to verify whether a financial asset complies with Islamic finance principles (Shariah law).
              Analyze the provided asset and return ONLY a JSON object — no markdown, no explanation outside JSON:
              {"approved": true|false, "reason": "<concise justification, max 100 chars>"}
              Rules: approved=true only if the asset sector/type contains no alcohol, gambling, pork, interest-based finance, weapons, or tobacco.
              """;

      private final AnthropicClient anthropicClient;
      private final AnthropicProperties props;
      private final ObjectMapper objectMapper = new ObjectMapper();

      public HalalScreenerAgent(AnthropicClient anthropicClient, AnthropicProperties props) {
          this.anthropicClient = anthropicClient;
          this.props = props;
      }

      public HalalReport analyze(String symbol, String name, String assetType, String sector) {
          String userMessage = String.format(
                  "Asset: %s | Name: %s | Type: %s | Sector: %s",
                  symbol, name, assetType, sector
          );
          try {
              String model = props.model("halal-screener");
              String response = anthropicClient.call(model, SYSTEM_PROMPT, userMessage, MAX_TOKENS);
              JsonNode node = objectMapper.readTree(response);
              boolean approved = node.path("approved").asBoolean();
              String reason = node.path("reason").asText();
              log.info("[HalalScreenerAgent] {} → approved={} reason=\"{}\"", symbol, approved, reason);
              return new HalalReport(approved, reason);
          } catch (Exception e) {
              log.error("[HalalScreenerAgent] failed for {}: {}", symbol, e.getMessage());
              return new HalalReport(false, "Screening error — defaulting to rejected");
          }
      }
  }
  ```

- [ ] **Step 4: Lancer le test**

  ```bash
  mvn test -Dtest=HalalScreenerAgentTest -q
  ```
  Expected: BUILD SUCCESS, 2 tests passed

- [ ] **Step 5: Commit**

  ```bash
  git add src/main/java/com/halaltrader/backend/agent/HalalScreenerAgent.java \
    src/test/java/com/halaltrader/backend/agent/HalalScreenerAgentTest.java
  git commit -m "feat: add HalalScreenerAgent (claude-opus-4-6) for Shariah compliance check"
  ```

---

## Task 5 — MarketAnalystAgent (TDD)

**Files:**
- Create: `src/main/java/com/halaltrader/backend/agent/MarketAnalystAgent.java`
- Create: `src/test/java/com/halaltrader/backend/agent/MarketAnalystAgentTest.java`

- [ ] **Step 1: Écrire le test**

  ```java
  package com.halaltrader.backend.agent;

  import com.halaltrader.backend.client.AnthropicClient;
  import com.halaltrader.backend.dto.MarketAnalysisReport;
  import com.halaltrader.backend.dto.MarketDataDto;
  import org.junit.jupiter.api.Test;
  import org.junit.jupiter.api.extension.ExtendWith;
  import org.mockito.InjectMocks;
  import org.mockito.Mock;
  import org.mockito.junit.jupiter.MockitoExtension;

  import java.util.List;

  import static org.assertj.core.api.Assertions.assertThat;
  import static org.mockito.ArgumentMatchers.*;
  import static org.mockito.Mockito.when;

  @ExtendWith(MockitoExtension.class)
  class MarketAnalystAgentTest {

      @Mock AnthropicClient anthropicClient;
      @Mock com.halaltrader.backend.config.AnthropicProperties props;
      @InjectMocks MarketAnalystAgent agent;

      @Test
      void analyze_bullishSignals_returnsBullishTrend() {
          when(anthropicClient.call(anyString(), anyString(), anyString(), anyInt()))
                  .thenReturn("{\"trend\":\"BULLISH\",\"momentum\":\"STRONG\",\"summary\":\"RSI 65, MACD crossover positive.\"}");

          MarketDataDto data = new MarketDataDto("AAPL", 175.5, 1.2, 50000000, 65.0, 1.5, 0.8, 172.0, 168.0, List.of("Apple strong"));
          MarketAnalysisReport report = agent.analyze(data);

          assertThat(report.trend()).isEqualTo("BULLISH");
          assertThat(report.momentum()).isEqualTo("STRONG");
          assertThat(report.summary()).isNotBlank();
      }

      @Test
      void analyze_apiError_returnsNeutralFallback() {
          when(anthropicClient.call(anyString(), anyString(), anyString(), anyInt()))
                  .thenThrow(new RuntimeException("timeout"));

          MarketDataDto data = new MarketDataDto("AAPL", 175.5, 0, 0, 50, 0, 0, 170, 165, List.of());
          MarketAnalysisReport report = agent.analyze(data);

          assertThat(report.trend()).isEqualTo("NEUTRAL");
      }
  }
  ```

- [ ] **Step 2: Créer MarketAnalystAgent.java**

  ```java
  package com.halaltrader.backend.agent;

  import com.fasterxml.jackson.databind.JsonNode;
  import com.fasterxml.jackson.databind.ObjectMapper;
  import com.halaltrader.backend.client.AnthropicClient;
  import com.halaltrader.backend.config.AnthropicProperties;
  import com.halaltrader.backend.dto.MarketAnalysisReport;
  import com.halaltrader.backend.dto.MarketDataDto;
  import org.slf4j.Logger;
  import org.slf4j.LoggerFactory;
  import org.springframework.stereotype.Component;

  @Component
  public class MarketAnalystAgent {

      private static final Logger log = LoggerFactory.getLogger(MarketAnalystAgent.class);
      private static final int MAX_TOKENS = 300;
      private static final String SYSTEM_PROMPT = """
              You are a quantitative market analyst. Your sole task is to produce a technical analysis from market indicators.
              Return ONLY a JSON object — no markdown:
              {"trend": "BULLISH"|"BEARISH"|"NEUTRAL", "momentum": "STRONG"|"MODERATE"|"WEAK", "summary": "<2 sentences max>"}
              Base your analysis strictly on the provided price, RSI, MACD, moving averages, and recent news.
              """;

      private final AnthropicClient anthropicClient;
      private final AnthropicProperties props;
      private final ObjectMapper objectMapper = new ObjectMapper();

      public MarketAnalystAgent(AnthropicClient anthropicClient, AnthropicProperties props) {
          this.anthropicClient = anthropicClient;
          this.props = props;
      }

      public MarketAnalysisReport analyze(MarketDataDto data) {
          String userMessage = String.format(
                  "Symbol: %s | Price: %.4f | Change: %.2f%% | RSI: %.2f | MACD: %.4f | Signal: %.4f | MA20: %.4f | MA50: %.4f | News: %s",
                  data.symbol(), data.price(), data.changePct(), data.rsi(),
                  data.macd(), data.macdSignal(), data.ma20(), data.ma50(),
                  String.join("; ", data.newsTitles())
          );
          try {
              String model = props.model("market-analyst");
              String response = anthropicClient.call(model, SYSTEM_PROMPT, userMessage, MAX_TOKENS);
              JsonNode node = objectMapper.readTree(response);
              String trend = node.path("trend").asText("NEUTRAL");
              String momentum = node.path("momentum").asText("MODERATE");
              String summary = node.path("summary").asText();
              log.info("[MarketAnalystAgent] {} → trend={} momentum={}", data.symbol(), trend, momentum);
              return new MarketAnalysisReport(trend, momentum, summary);
          } catch (Exception e) {
              log.error("[MarketAnalystAgent] failed for {}: {}", data.symbol(), e.getMessage());
              return new MarketAnalysisReport("NEUTRAL", "WEAK", "Analysis unavailable");
          }
      }
  }
  ```

- [ ] **Step 3: Lancer les tests**

  ```bash
  mvn test -Dtest=MarketAnalystAgentTest -q
  ```
  Expected: BUILD SUCCESS, 2 tests passed

- [ ] **Step 4: Commit**

  ```bash
  git add src/main/java/com/halaltrader/backend/agent/MarketAnalystAgent.java \
    src/test/java/com/halaltrader/backend/agent/MarketAnalystAgentTest.java
  git commit -m "feat: add MarketAnalystAgent (claude-sonnet-4-6) for technical analysis"
  ```

---

## Task 6 — RiskManagerAgent (TDD)

**Files:**
- Create: `src/main/java/com/halaltrader/backend/agent/RiskManagerAgent.java`
- Create: `src/test/java/com/halaltrader/backend/agent/RiskManagerAgentTest.java`

- [ ] **Step 1: Écrire le test**

  ```java
  package com.halaltrader.backend.agent;

  import com.halaltrader.backend.client.AnthropicClient;
  import com.halaltrader.backend.dto.MarketAnalysisReport;
  import com.halaltrader.backend.dto.RiskReport;
  import org.junit.jupiter.api.Test;
  import org.junit.jupiter.api.extension.ExtendWith;
  import org.mockito.InjectMocks;
  import org.mockito.Mock;
  import org.mockito.junit.jupiter.MockitoExtension;

  import java.math.BigDecimal;

  import static org.assertj.core.api.Assertions.assertThat;
  import static org.mockito.ArgumentMatchers.*;
  import static org.mockito.Mockito.when;

  @ExtendWith(MockitoExtension.class)
  class RiskManagerAgentTest {

      @Mock AnthropicClient anthropicClient;
      @Mock com.halaltrader.backend.config.AnthropicProperties props;
      @InjectMocks RiskManagerAgent agent;

      @Test
      void assess_lowRisk_returnsPositiveMaxQuantity() {
          when(anthropicClient.call(anyString(), anyString(), anyString(), anyInt()))
                  .thenReturn("{\"level\":\"LOW\",\"max_quantity\":5,\"reason\":\"Volatility low, portfolio well diversified.\"}");

          MarketAnalysisReport analysis = new MarketAnalysisReport("BULLISH", "STRONG", "Positive signals.");
          RiskReport report = agent.assess("AAPL", BigDecimal.valueOf(50000), BigDecimal.ZERO, BigDecimal.valueOf(175), analysis);

          assertThat(report.level()).isEqualTo("LOW");
          assertThat(report.maxQuantity()).isEqualByComparingTo(BigDecimal.valueOf(5));
      }

      @Test
      void assess_highRisk_returnsZeroMaxQuantity() {
          when(anthropicClient.call(anyString(), anyString(), anyString(), anyInt()))
                  .thenReturn("{\"level\":\"HIGH\",\"max_quantity\":0,\"reason\":\"High volatility detected.\"}");

          MarketAnalysisReport analysis = new MarketAnalysisReport("BEARISH", "WEAK", "Negative signals.");
          RiskReport report = agent.assess("NVDA", BigDecimal.valueOf(10000), BigDecimal.valueOf(5), BigDecimal.valueOf(500), analysis);

          assertThat(report.level()).isEqualTo("HIGH");
          assertThat(report.maxQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
      }
  }
  ```

- [ ] **Step 2: Créer RiskManagerAgent.java**

  ```java
  package com.halaltrader.backend.agent;

  import com.fasterxml.jackson.databind.JsonNode;
  import com.fasterxml.jackson.databind.ObjectMapper;
  import com.halaltrader.backend.client.AnthropicClient;
  import com.halaltrader.backend.config.AnthropicProperties;
  import com.halaltrader.backend.dto.MarketAnalysisReport;
  import com.halaltrader.backend.dto.RiskReport;
  import org.slf4j.Logger;
  import org.slf4j.LoggerFactory;
  import org.springframework.stereotype.Component;

  import java.math.BigDecimal;

  @Component
  public class RiskManagerAgent {

      private static final Logger log = LoggerFactory.getLogger(RiskManagerAgent.class);
      private static final int MAX_TOKENS = 300;
      private static final String SYSTEM_PROMPT = """
              You are a risk manager for a simulated halal trading portfolio. Your sole task is to evaluate position risk.
              Return ONLY a JSON object — no markdown:
              {"level": "LOW"|"MEDIUM"|"HIGH", "max_quantity": <integer>, "reason": "<1 sentence>"}
              Rules:
              - Never risk more than 10% of portfolio cash in a single trade.
              - If level=HIGH, max_quantity must be 0.
              - max_quantity is the number of shares/units, computed from cash_available * 10% / price.
              """;

      private final AnthropicClient anthropicClient;
      private final AnthropicProperties props;
      private final ObjectMapper objectMapper = new ObjectMapper();

      public RiskManagerAgent(AnthropicClient anthropicClient, AnthropicProperties props) {
          this.anthropicClient = anthropicClient;
          this.props = props;
      }

      public RiskReport assess(String symbol, BigDecimal cashBalance, BigDecimal currentPositionQty,
                               BigDecimal currentPrice, MarketAnalysisReport analysis) {
          String userMessage = String.format(
                  "Symbol: %s | Cash: %.2f | Current position qty: %.4f | Price: %.4f | Trend: %s | Momentum: %s | Analysis: %s",
                  symbol, cashBalance, currentPositionQty, currentPrice,
                  analysis.trend(), analysis.momentum(), analysis.summary()
          );
          try {
              String model = props.model("risk-manager");
              String response = anthropicClient.call(model, SYSTEM_PROMPT, userMessage, MAX_TOKENS);
              JsonNode node = objectMapper.readTree(response);
              String level = node.path("level").asText("HIGH");
              BigDecimal maxQty = BigDecimal.valueOf(node.path("max_quantity").asLong(0));
              String reason = node.path("reason").asText();
              log.info("[RiskManagerAgent] {} → level={} maxQty={}", symbol, level, maxQty);
              return new RiskReport(level, maxQty, reason);
          } catch (Exception e) {
              log.error("[RiskManagerAgent] failed for {}: {}", symbol, e.getMessage());
              return new RiskReport("HIGH", BigDecimal.ZERO, "Risk assessment error — defaulting to HIGH");
          }
      }
  }
  ```

- [ ] **Step 3: Lancer les tests**

  ```bash
  mvn test -Dtest=RiskManagerAgentTest -q
  ```
  Expected: BUILD SUCCESS, 2 tests passed

- [ ] **Step 4: Commit**

  ```bash
  git add src/main/java/com/halaltrader/backend/agent/RiskManagerAgent.java \
    src/test/java/com/halaltrader/backend/agent/RiskManagerAgentTest.java
  git commit -m "feat: add RiskManagerAgent (claude-sonnet-4-6) for position risk assessment"
  ```

---

## Task 7 — DecisionAgent (TDD)

**Files:**
- Create: `src/main/java/com/halaltrader/backend/agent/DecisionAgent.java`
- Create: `src/test/java/com/halaltrader/backend/agent/DecisionAgentTest.java`

- [ ] **Step 1: Écrire le test**

  ```java
  package com.halaltrader.backend.agent;

  import com.halaltrader.backend.client.AnthropicClient;
  import com.halaltrader.backend.dto.*;
  import com.halaltrader.backend.entity.TradeAction;
  import org.junit.jupiter.api.Test;
  import org.junit.jupiter.api.extension.ExtendWith;
  import org.mockito.InjectMocks;
  import org.mockito.Mock;
  import org.mockito.junit.jupiter.MockitoExtension;

  import java.math.BigDecimal;

  import static org.assertj.core.api.Assertions.assertThat;
  import static org.mockito.ArgumentMatchers.*;
  import static org.mockito.Mockito.when;

  @ExtendWith(MockitoExtension.class)
  class DecisionAgentTest {

      @Mock AnthropicClient anthropicClient;
      @Mock com.halaltrader.backend.config.AnthropicProperties props;
      @InjectMocks DecisionAgent agent;

      @Test
      void decide_bullishLowRisk_returnsBuy() {
          when(anthropicClient.call(anyString(), anyString(), anyString(), anyInt()))
                  .thenReturn("{\"action\":\"BUY\",\"quantity\":3,\"reasoning\":\"Strong bullish trend, low risk, no current position.\"}");

          HalalReport halal = new HalalReport(true, "ETF islamique certifié");
          MarketAnalysisReport analysis = new MarketAnalysisReport("BULLISH", "STRONG", "RSI 65.");
          RiskReport risk = new RiskReport("LOW", BigDecimal.valueOf(5), "10% cash limit OK.");
          TradeDecision decision = agent.decide("ISWD.L", halal, analysis, risk, BigDecimal.ZERO);

          assertThat(decision.action()).isEqualTo(TradeAction.BUY);
          assertThat(decision.quantity()).isEqualByComparingTo(BigDecimal.valueOf(3));
          assertThat(decision.reasoning()).isNotBlank();
      }

      @Test
      void decide_highRisk_returnsHold() {
          when(anthropicClient.call(anyString(), anyString(), anyString(), anyInt()))
                  .thenReturn("{\"action\":\"HOLD\",\"quantity\":0,\"reasoning\":\"Risk too high.\"}");

          HalalReport halal = new HalalReport(true, "Approved");
          MarketAnalysisReport analysis = new MarketAnalysisReport("BEARISH", "WEAK", "Bearish.");
          RiskReport risk = new RiskReport("HIGH", BigDecimal.ZERO, "Volatility too high.");
          TradeDecision decision = agent.decide("AAPL", halal, analysis, risk, BigDecimal.valueOf(10));

          assertThat(decision.action()).isEqualTo(TradeAction.HOLD);
          assertThat(decision.quantity()).isEqualByComparingTo(BigDecimal.ZERO);
      }
  }
  ```

- [ ] **Step 2: Créer DecisionAgent.java**

  ```java
  package com.halaltrader.backend.agent;

  import com.fasterxml.jackson.databind.JsonNode;
  import com.fasterxml.jackson.databind.ObjectMapper;
  import com.halaltrader.backend.client.AnthropicClient;
  import com.halaltrader.backend.config.AnthropicProperties;
  import com.halaltrader.backend.dto.*;
  import com.halaltrader.backend.entity.TradeAction;
  import org.slf4j.Logger;
  import org.slf4j.LoggerFactory;
  import org.springframework.stereotype.Component;

  import java.math.BigDecimal;

  @Component
  public class DecisionAgent {

      private static final Logger log = LoggerFactory.getLogger(DecisionAgent.class);
      private static final int MAX_TOKENS = 600;
      private static final String SYSTEM_PROMPT = """
              You are the final decision maker for a halal trading simulation. Your sole task is to synthesize three reports and make a trade decision.
              Return ONLY a JSON object — no markdown:
              {"action": "BUY"|"SELL"|"HOLD", "quantity": <integer>, "reasoning": "<3 sentences max>"}
              Rules:
              - If risk level is HIGH, action MUST be HOLD and quantity MUST be 0.
              - quantity must never exceed max_quantity from the risk report.
              - quantity must be 0 for HOLD.
              - For SELL, quantity is the number of shares to sell (max = current position).
              """;

      private final AnthropicClient anthropicClient;
      private final AnthropicProperties props;
      private final ObjectMapper objectMapper = new ObjectMapper();

      public DecisionAgent(AnthropicClient anthropicClient, AnthropicProperties props) {
          this.anthropicClient = anthropicClient;
          this.props = props;
      }

      public TradeDecision decide(String symbol, HalalReport halal, MarketAnalysisReport analysis,
                                  RiskReport risk, BigDecimal currentPositionQty) {
          String userMessage = String.format(
                  "Symbol: %s\n" +
                  "Halal: approved=%s, reason=%s\n" +
                  "Market: trend=%s, momentum=%s, summary=%s\n" +
                  "Risk: level=%s, max_quantity=%s, reason=%s\n" +
                  "Current position: %s shares",
                  symbol,
                  halal.approved(), halal.reason(),
                  analysis.trend(), analysis.momentum(), analysis.summary(),
                  risk.level(), risk.maxQuantity(), risk.reason(),
                  currentPositionQty
          );
          try {
              String model = props.model("decision");
              String response = anthropicClient.call(model, SYSTEM_PROMPT, userMessage, MAX_TOKENS);
              JsonNode node = objectMapper.readTree(response);
              TradeAction action = TradeAction.valueOf(node.path("action").asText("HOLD"));
              BigDecimal quantity = BigDecimal.valueOf(node.path("quantity").asLong(0));
              String reasoning = node.path("reasoning").asText();
              log.info("[DecisionAgent] {} → action={} qty={}", symbol, action, quantity);
              return new TradeDecision(action, quantity, reasoning);
          } catch (Exception e) {
              log.error("[DecisionAgent] failed for {}: {}", symbol, e.getMessage());
              return new TradeDecision(TradeAction.HOLD, BigDecimal.ZERO, "Decision error — defaulting to HOLD");
          }
      }
  }
  ```

- [ ] **Step 3: Lancer les tests**

  ```bash
  mvn test -Dtest=DecisionAgentTest -q
  ```
  Expected: BUILD SUCCESS, 2 tests passed

- [ ] **Step 4: Commit**

  ```bash
  git add src/main/java/com/halaltrader/backend/agent/DecisionAgent.java \
    src/test/java/com/halaltrader/backend/agent/DecisionAgentTest.java
  git commit -m "feat: add DecisionAgent (claude-opus-4-6) for final trade synthesis"
  ```

---

## Task 8 — PerformanceAgent (TDD)

**Files:**
- Create: `src/main/java/com/halaltrader/backend/agent/PerformanceAgent.java`
- Create: `src/test/java/com/halaltrader/backend/agent/PerformanceAgentTest.java`

- [ ] **Step 1: Écrire le test**

  ```java
  package com.halaltrader.backend.agent;

  import com.halaltrader.backend.client.AnthropicClient;
  import com.halaltrader.backend.entity.TradeAction;
  import org.junit.jupiter.api.Test;
  import org.junit.jupiter.api.extension.ExtendWith;
  import org.mockito.InjectMocks;
  import org.mockito.Mock;
  import org.mockito.junit.jupiter.MockitoExtension;

  import java.math.BigDecimal;

  import static org.assertj.core.api.Assertions.assertThat;
  import static org.mockito.ArgumentMatchers.*;
  import static org.mockito.Mockito.when;

  @ExtendWith(MockitoExtension.class)
  class PerformanceAgentTest {

      @Mock AnthropicClient anthropicClient;
      @Mock com.halaltrader.backend.config.AnthropicProperties props;
      @InjectMocks PerformanceAgent agent;

      @Test
      void compute_sellWithProfit_returnsPositivePnl() {
          when(anthropicClient.call(anyString(), anyString(), anyString(), anyInt()))
                  .thenReturn("{\"pnl\":250.00,\"pnl_pct\":16.67,\"comment\":\"Profitable exit.\"}");

          BigDecimal pnl = agent.compute(TradeAction.SELL, BigDecimal.valueOf(5),
                  BigDecimal.valueOf(175), BigDecimal.valueOf(150));

          assertThat(pnl).isEqualByComparingTo(BigDecimal.valueOf(250.00));
      }

      @Test
      void compute_buyTrade_returnsZeroPnl() {
          when(anthropicClient.call(anyString(), anyString(), anyString(), anyInt()))
                  .thenReturn("{\"pnl\":0,\"pnl_pct\":0,\"comment\":\"BUY trade, PnL not realized.\"}");

          BigDecimal pnl = agent.compute(TradeAction.BUY, BigDecimal.valueOf(3),
                  BigDecimal.valueOf(175), BigDecimal.ZERO);

          assertThat(pnl).isEqualByComparingTo(BigDecimal.ZERO);
      }
  }
  ```

- [ ] **Step 2: Créer PerformanceAgent.java**

  ```java
  package com.halaltrader.backend.agent;

  import com.fasterxml.jackson.databind.JsonNode;
  import com.fasterxml.jackson.databind.ObjectMapper;
  import com.halaltrader.backend.client.AnthropicClient;
  import com.halaltrader.backend.config.AnthropicProperties;
  import com.halaltrader.backend.entity.TradeAction;
  import org.slf4j.Logger;
  import org.slf4j.LoggerFactory;
  import org.springframework.stereotype.Component;

  import java.math.BigDecimal;

  @Component
  public class PerformanceAgent {

      private static final Logger log = LoggerFactory.getLogger(PerformanceAgent.class);
      private static final int MAX_TOKENS = 300;
      private static final String SYSTEM_PROMPT = """
              You are a trading performance analyst. Your sole task is to calculate trade metrics.
              Return ONLY a JSON object — no markdown:
              {"pnl": <number>, "pnl_pct": <number>, "comment": "<brief comment>"}
              Rules:
              - For BUY trades: pnl=0, pnl_pct=0 (unrealized).
              - For SELL trades: pnl = (price - avg_buy_price) * quantity, pnl_pct = ((price - avg_buy_price) / avg_buy_price) * 100.
              - For HOLD: pnl=0, pnl_pct=0.
              """;

      private final AnthropicClient anthropicClient;
      private final AnthropicProperties props;
      private final ObjectMapper objectMapper = new ObjectMapper();

      public PerformanceAgent(AnthropicClient anthropicClient, AnthropicProperties props) {
          this.anthropicClient = anthropicClient;
          this.props = props;
      }

      public BigDecimal compute(TradeAction action, BigDecimal quantity, BigDecimal price, BigDecimal avgBuyPrice) {
          String userMessage = String.format(
                  "Action: %s | Quantity: %s | Price: %s | Avg buy price: %s",
                  action, quantity, price, avgBuyPrice
          );
          try {
              String model = props.model("performance");
              String response = anthropicClient.call(model, SYSTEM_PROMPT, userMessage, MAX_TOKENS);
              JsonNode node = objectMapper.readTree(response);
              BigDecimal pnl = new BigDecimal(node.path("pnl").asText("0"));
              double pnlPct = node.path("pnl_pct").asDouble(0);
              log.info("[PerformanceAgent] action={} pnl={} pnl_pct={}%", action, pnl, pnlPct);
              return pnl;
          } catch (Exception e) {
              log.error("[PerformanceAgent] failed: {}", e.getMessage());
              return BigDecimal.ZERO;
          }
      }
  }
  ```

- [ ] **Step 3: Lancer les tests**

  ```bash
  mvn test -Dtest=PerformanceAgentTest -q
  ```
  Expected: BUILD SUCCESS, 2 tests passed

- [ ] **Step 4: Commit**

  ```bash
  git add src/main/java/com/halaltrader/backend/agent/PerformanceAgent.java \
    src/test/java/com/halaltrader/backend/agent/PerformanceAgentTest.java
  git commit -m "feat: add PerformanceAgent (claude-haiku-4-5-20251001) for PnL metrics"
  ```

---

## Task 9 — TradeExecutionService (TDD)

**Files:**
- Create: `src/main/java/com/halaltrader/backend/service/TradeExecutionService.java`
- Create: `src/test/java/com/halaltrader/backend/service/TradeExecutionServiceTest.java`

- [ ] **Step 1: Écrire le test**

  ```java
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

      @Test
      void execute_buyTrade_deductsFromCashAndSavesTrade() {
          Portfolio portfolio = new Portfolio();
          portfolio.setId(UUID.randomUUID());
          portfolio.setName("Test");
          portfolio.setCashBalance(BigDecimal.valueOf(10000));

          Asset asset = new Asset();
          asset.setId(UUID.randomUUID());
          asset.setSymbol("AAPL");

          when(positionRepository.findByPortfolioAndAsset(portfolio, asset))
                  .thenReturn(Optional.empty());
          when(tradeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

          TradeDecision decision = new TradeDecision(TradeAction.BUY, BigDecimal.valueOf(2), "Bullish.");

          service.execute(portfolio, asset, decision, BigDecimal.valueOf(175), BigDecimal.ZERO, "full audit json");

          ArgumentCaptor<Portfolio> portfolioCaptor = ArgumentCaptor.forClass(Portfolio.class);
          verify(portfolioRepository).save(portfolioCaptor.capture());
          assertThat(portfolioCaptor.getValue().getCashBalance())
                  .isEqualByComparingTo(BigDecimal.valueOf(9650)); // 10000 - 2*175

          ArgumentCaptor<Trade> tradeCaptor = ArgumentCaptor.forClass(Trade.class);
          verify(tradeRepository).save(tradeCaptor.capture());
          assertThat(tradeCaptor.getValue().getAction()).isEqualTo(TradeAction.BUY);
          assertThat(tradeCaptor.getValue().getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(2));
      }

      @Test
      void execute_holdTrade_doesNothing() {
          Portfolio portfolio = new Portfolio();
          portfolio.setCashBalance(BigDecimal.valueOf(10000));
          Asset asset = new Asset();

          TradeDecision decision = new TradeDecision(TradeAction.HOLD, BigDecimal.ZERO, "Waiting.");

          service.execute(portfolio, asset, decision, BigDecimal.valueOf(175), BigDecimal.ZERO, "{}");

          verifyNoInteractions(portfolioRepository, positionRepository, tradeRepository);
      }
  }
  ```

- [ ] **Step 2: Créer TradeExecutionService.java**

  ```java
  package com.halaltrader.backend.service;

  import com.halaltrader.backend.dto.TradeDecision;
  import com.halaltrader.backend.entity.*;
  import com.halaltrader.backend.repository.*;
  import org.slf4j.Logger;
  import org.slf4j.LoggerFactory;
  import org.springframework.stereotype.Service;
  import org.springframework.transaction.annotation.Transactional;

  import java.math.BigDecimal;
  import java.time.LocalDateTime;
  import java.util.Optional;

  @Service
  public class TradeExecutionService {

      private static final Logger log = LoggerFactory.getLogger(TradeExecutionService.class);

      private final PortfolioRepository portfolioRepository;
      private final PortfolioPositionRepository positionRepository;
      private final TradeRepository tradeRepository;

      public TradeExecutionService(PortfolioRepository portfolioRepository,
                                   PortfolioPositionRepository positionRepository,
                                   TradeRepository tradeRepository) {
          this.portfolioRepository = portfolioRepository;
          this.positionRepository = positionRepository;
          this.tradeRepository = tradeRepository;
      }

      @Transactional
      public void execute(Portfolio portfolio, Asset asset, TradeDecision decision,
                          BigDecimal price, BigDecimal simulatedPnl, String auditJson) {
          if (decision.action() == TradeAction.HOLD || decision.quantity().compareTo(BigDecimal.ZERO) == 0) {
              log.info("[TradeExecutionService] HOLD for {} — no action taken", asset.getSymbol());
              return;
          }

          BigDecimal totalAmount = price.multiply(decision.quantity());

          if (decision.action() == TradeAction.BUY) {
              executeBuy(portfolio, asset, decision, price, totalAmount, simulatedPnl, auditJson);
          } else if (decision.action() == TradeAction.SELL) {
              executeSell(portfolio, asset, decision, price, totalAmount, simulatedPnl, auditJson);
          }
      }

      private void executeBuy(Portfolio portfolio, Asset asset, TradeDecision decision,
                              BigDecimal price, BigDecimal totalAmount, BigDecimal pnl, String audit) {
          portfolio.setCashBalance(portfolio.getCashBalance().subtract(totalAmount));
          portfolioRepository.save(portfolio);

          Optional<PortfolioPosition> existing = positionRepository.findByPortfolioAndAsset(portfolio, asset);
          if (existing.isPresent()) {
              PortfolioPosition pos = existing.get();
              BigDecimal newQty = pos.getQuantity().add(decision.quantity());
              BigDecimal newAvg = pos.getAverageBuyPrice()
                      .multiply(pos.getQuantity())
                      .add(price.multiply(decision.quantity()))
                      .divide(newQty, 4, java.math.RoundingMode.HALF_UP);
              pos.setQuantity(newQty);
              pos.setAverageBuyPrice(newAvg);
              positionRepository.save(pos);
          } else {
              PortfolioPosition pos = new PortfolioPosition();
              pos.setPortfolio(portfolio);
              pos.setAsset(asset);
              pos.setQuantity(decision.quantity());
              pos.setAverageBuyPrice(price);
              positionRepository.save(pos);
          }

          saveTrade(portfolio, asset, decision, price, totalAmount, pnl, audit);
          log.info("[TradeExecutionService] BUY {} qty={} price={} total={}", asset.getSymbol(), decision.quantity(), price, totalAmount);
      }

      private void executeSell(Portfolio portfolio, Asset asset, TradeDecision decision,
                               BigDecimal price, BigDecimal totalAmount, BigDecimal pnl, String audit) {
          portfolio.setCashBalance(portfolio.getCashBalance().add(totalAmount));
          portfolioRepository.save(portfolio);

          positionRepository.findByPortfolioAndAsset(portfolio, asset).ifPresent(pos -> {
              BigDecimal newQty = pos.getQuantity().subtract(decision.quantity());
              if (newQty.compareTo(BigDecimal.ZERO) <= 0) {
                  positionRepository.delete(pos);
              } else {
                  pos.setQuantity(newQty);
                  positionRepository.save(pos);
              }
          });

          saveTrade(portfolio, asset, decision, price, totalAmount, pnl, audit);
          log.info("[TradeExecutionService] SELL {} qty={} price={} pnl={}", asset.getSymbol(), decision.quantity(), price, pnl);
      }

      private void saveTrade(Portfolio portfolio, Asset asset, TradeDecision decision,
                             BigDecimal price, BigDecimal totalAmount, BigDecimal pnl, String audit) {
          Trade trade = new Trade();
          trade.setPortfolio(portfolio);
          trade.setAsset(asset);
          trade.setAction(decision.action());
          trade.setQuantity(decision.quantity());
          trade.setPrice(price);
          trade.setTotalAmount(totalAmount);
          trade.setAiReasoning(decision.reasoning());
          trade.setTechnicalData(audit);
          trade.setSimulatedPnl(pnl);
          trade.setExecutedAt(LocalDateTime.now());
          tradeRepository.save(trade);
      }
  }
  ```

- [ ] **Step 3: Lancer les tests**

  ```bash
  mvn test -Dtest=TradeExecutionServiceTest -q
  ```
  Expected: BUILD SUCCESS, 2 tests passed

- [ ] **Step 4: Commit**

  ```bash
  git add src/main/java/com/halaltrader/backend/service/TradeExecutionService.java \
    src/test/java/com/halaltrader/backend/service/TradeExecutionServiceTest.java
  git commit -m "feat: add TradeExecutionService for BUY/SELL portfolio updates"
  ```

---

## Task 10 — TradingOrchestrator (TDD)

**Files:**
- Create: `src/main/java/com/halaltrader/backend/service/TradingOrchestrator.java`
- Create: `src/test/java/com/halaltrader/backend/service/TradingOrchestratorTest.java`

- [ ] **Step 1: Écrire le test**

  ```java
  package com.halaltrader.backend.service;

  import com.halaltrader.backend.agent.*;
  import com.halaltrader.backend.client.MarketDataClient;
  import com.halaltrader.backend.dto.*;
  import com.halaltrader.backend.entity.*;
  import com.halaltrader.backend.repository.*;
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
      @InjectMocks TradingOrchestrator orchestrator;

      @Test
      void run_halalRejected_stopsPipelineEarly() {
          Asset asset = buildAsset("BUD", "Anheuser-Busch");
          Portfolio portfolio = buildPortfolio();

          when(assetRepository.findByHalalScreening(HalalScreening.APPROVED)).thenReturn(List.of(asset));
          when(portfolioRepository.findAll()).thenReturn(List.of(portfolio));
          when(halalScreenerAgent.analyze(anyString(), anyString(), anyString(), anyString()))
                  .thenReturn(new HalalReport(false, "Secteur alcool interdit"));

          orchestrator.run();

          verifyNoInteractions(marketDataClient, marketAnalystAgent, riskManagerAgent, decisionAgent);
      }

      @Test
      void run_validPipeline_callsAllAgentsAndExecutes() {
          Asset asset = buildAsset("AAPL", "Apple Inc");
          Portfolio portfolio = buildPortfolio();
          MarketDataDto marketData = new MarketDataDto("AAPL", 175.0, 1.0, 1000000, 60, 1.0, 0.5, 170, 165, List.of());
          MarketAnalysisReport analysis = new MarketAnalysisReport("BULLISH", "STRONG", "Good signals.");
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

      private Asset buildAsset(String symbol, String name) {
          Asset a = new Asset();
          a.setId(UUID.randomUUID());
          a.setSymbol(symbol);
          a.setName(name);
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
  }
  ```

- [ ] **Step 2: Créer TradingOrchestrator.java**

  ```java
  package com.halaltrader.backend.service;

  import com.fasterxml.jackson.databind.ObjectMapper;
  import com.halaltrader.backend.agent.*;
  import com.halaltrader.backend.client.MarketDataClient;
  import com.halaltrader.backend.dto.*;
  import com.halaltrader.backend.entity.*;
  import com.halaltrader.backend.repository.*;
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
      private final ObjectMapper objectMapper = new ObjectMapper();

      public TradingOrchestrator(HalalScreenerAgent halalScreenerAgent,
                                 MarketAnalystAgent marketAnalystAgent,
                                 RiskManagerAgent riskManagerAgent,
                                 DecisionAgent decisionAgent,
                                 PerformanceAgent performanceAgent,
                                 MarketDataClient marketDataClient,
                                 AssetRepository assetRepository,
                                 PortfolioRepository portfolioRepository,
                                 PortfolioPositionRepository positionRepository,
                                 TradeExecutionService tradeExecutionService) {
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
      }

      public void run() {
          List<Asset> assets = assetRepository.findByHalalScreening(HalalScreening.APPROVED);
          List<Portfolio> portfolios = portfolioRepository.findAll();

          if (portfolios.isEmpty()) {
              log.warn("[TradingOrchestrator] No portfolio found — aborting");
              return;
          }
          Portfolio portfolio = portfolios.get(0);
          log.info("[TradingOrchestrator] Starting cycle — {} assets, cash={}", assets.size(), portfolio.getCashBalance());

          for (Asset asset : assets) {
              try {
                  processAsset(portfolio, asset);
              } catch (Exception e) {
                  log.error("[TradingOrchestrator] Unexpected error for {} — skipping: {}", asset.getSymbol(), e.getMessage());
              }
          }
          log.info("[TradingOrchestrator] Cycle complete");
      }

      private void processAsset(Portfolio portfolio, Asset asset) {
          String symbol = asset.getSymbol();

          // Step 1 — Halal check (pipeline stops if rejected)
          HalalReport halal = halalScreenerAgent.analyze(
                  symbol, asset.getName(), asset.getAssetType().name(), asset.getSector());
          if (!halal.approved()) {
              log.info("[TradingOrchestrator] {} rejected by HalalScreener — stopping pipeline", symbol);
              return;
          }

          // Step 2 — Market data + analysis
          MarketDataDto marketData = marketDataClient.getMarketData(symbol);
          MarketAnalysisReport analysis = marketAnalystAgent.analyze(marketData);

          // Step 3 — Current position
          Optional<PortfolioPosition> position = positionRepository.findByPortfolioAndAsset(portfolio, asset);
          BigDecimal currentQty = position.map(PortfolioPosition::getQuantity).orElse(BigDecimal.ZERO);
          BigDecimal avgBuyPrice = position.map(PortfolioPosition::getAverageBuyPrice).orElse(BigDecimal.ZERO);

          // Step 4 — Risk assessment
          RiskReport risk = riskManagerAgent.assess(
                  symbol, portfolio.getCashBalance(), currentQty, BigDecimal.valueOf(marketData.price()), analysis);

          // Step 5 — Trade decision
          TradeDecision decision = decisionAgent.decide(symbol, halal, analysis, risk, currentQty);

          // Step 6 — Performance
          BigDecimal pnl = performanceAgent.compute(
                  decision.action(), decision.quantity(), BigDecimal.valueOf(marketData.price()), avgBuyPrice);

          // Step 7 — Build audit JSON
          String auditJson = buildAuditJson(halal, analysis, risk, decision);

          // Step 8 — Execute
          tradeExecutionService.execute(portfolio, asset, decision,
                  BigDecimal.valueOf(marketData.price()), pnl, auditJson);
      }

      private String buildAuditJson(HalalReport halal, MarketAnalysisReport analysis,
                                    RiskReport risk, TradeDecision decision) {
          try {
              Map<String, Object> audit = new LinkedHashMap<>();
              audit.put("halal", Map.of("approved", halal.approved(), "reason", halal.reason()));
              audit.put("analysis", Map.of("trend", analysis.trend(), "momentum", analysis.momentum(), "summary", analysis.summary()));
              audit.put("risk", Map.of("level", risk.level(), "maxQuantity", risk.maxQuantity(), "reason", risk.reason()));
              audit.put("decision", Map.of("action", decision.action(), "quantity", decision.quantity(), "reasoning", decision.reasoning()));
              return objectMapper.writeValueAsString(audit);
          } catch (Exception e) {
              return "{}";
          }
      }
  }
  ```

- [ ] **Step 3: Lancer les tests**

  ```bash
  mvn test -Dtest=TradingOrchestratorTest -q
  ```
  Expected: BUILD SUCCESS, 2 tests passed

- [ ] **Step 4: Commit**

  ```bash
  git add src/main/java/com/halaltrader/backend/service/TradingOrchestrator.java \
    src/test/java/com/halaltrader/backend/service/TradingOrchestratorTest.java
  git commit -m "feat: add TradingOrchestrator — full agent pipeline with halal-first guard"
  ```

---

## Task 11 — TradingScheduler + Full Test Suite

**Files:**
- Create: `src/main/java/com/halaltrader/backend/scheduler/TradingScheduler.java`

- [ ] **Step 1: Créer TradingScheduler.java**

  Ajouter `@EnableScheduling` sur `HalalTraderApplication` (pas sur `TradingScheduler`) :

  `src/main/java/com/halaltrader/backend/HalalTraderApplication.java` — modifier :
  ```java
  @SpringBootApplication
  @EnableScheduling
  public class HalalTraderApplication {
  ```

  Créer `TradingScheduler.java` :
  ```java
  package com.halaltrader.backend.scheduler;

  import com.halaltrader.backend.service.TradingOrchestrator;
  import org.slf4j.Logger;
  import org.slf4j.LoggerFactory;
  import org.springframework.scheduling.annotation.Scheduled;
  import org.springframework.stereotype.Component;

  @Component
  public class TradingScheduler {

      private static final Logger log = LoggerFactory.getLogger(TradingScheduler.class);

      private final TradingOrchestrator orchestrator;

      public TradingScheduler(TradingOrchestrator orchestrator) {
          this.orchestrator = orchestrator;
      }

      @Scheduled(cron = "${trading.scheduler.cron}")
      public void scheduledCycle() {
          log.info("[TradingScheduler] Cron triggered — starting trading cycle");
          orchestrator.run();
      }
  }
  ```

- [ ] **Step 2: Compiler le projet entier**

  ```bash
  mvn compile -q
  ```
  Expected: BUILD SUCCESS

- [ ] **Step 3: Lancer tous les tests**

  ```bash
  mvn test -q
  ```
  Expected: BUILD SUCCESS, tous les tests passent (10+ tests)

- [ ] **Step 4: Commit final**

  ```bash
  git add src/main/java/com/halaltrader/backend/scheduler/TradingScheduler.java
  git commit -m "feat: add TradingScheduler — cron-triggered trading pipeline"
  ```

---

## Verification Checklist

Après toutes les tâches :

- [ ] `mvn compile` passe sans erreur
- [ ] `mvn test` : tous les tests passent (AnthropicClient, MarketDataClient, 5 agents, TradeExecutionService, TradingOrchestrator)
- [ ] `mvn spring-boot:run` démarre sans erreur (avec Docker Compose + ANTHROPIC_API_KEY en env var)
- [ ] Les logs au démarrage montrent `Started HalalTraderApplication`
- [ ] Les logs cron toutes les 30 min montrent `[TradingScheduler] Cron triggered`
- [ ] Un cycle complet avec `ANTHROPIC_API_KEY` réelle loggue : `[DecisionAgent] AAPL → action=BUY/SELL/HOLD`

---

## Common Pitfalls

| Problème | Fix |
|---|---|
| `@Qualifier("anthropicWebClient") not found` | Vérifier que `WebClientConfig` crée bien un bean avec ce nom exact |
| `AnthropicProperties` ne mappe pas les modèles | Vérifier que `application.yml` utilise des tirets (`halal-screener`) et que `setModels` accepte `Map<String, String>` |
| `TradeAction.valueOf` lance une exception | Le modèle a renvoyé `"Buy"` au lieu de `"BUY"` — ajouter `.toUpperCase()` avant `valueOf` si besoin |
| `MockWebServer` non trouvé en test | Vérifier que `com.squareup.okhttp3:mockwebserver:4.12.0` est dans pom.xml avec `<scope>test</scope>` |
| Spring Boot ne trouve pas `@EnableScheduling` | Vérifier que l'annotation est sur `HalalTraderApplication`, pas sur `TradingScheduler` |
| Flyway échoue au démarrage Phase 2 | Aucune migration V2 n'est attendue — si Flyway se plaint, vérifier que V1 est déjà appliqué |
