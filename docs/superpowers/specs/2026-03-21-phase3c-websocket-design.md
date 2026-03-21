# Phase 3C — WebSocket Live Updates

## Goal

Remplacer les polls toutes les 30s du frontend par un feed WebSocket temps réel. Pendant un cycle de trading, chaque décision IA (BUY/SELL/HOLD par actif) est poussée instantanément au browser. Un event `CYCLE_COMPLETE` en fin de cycle invalide le cache TanStack Query pour rafraîchir portfolio, trades et performance.

## Architecture

```
TradingOrchestrator
  └── après chaque décision ──► TradingEventPublisher
                                    └── SimpMessagingTemplate
                                          └── STOMP /topic/trading-events ──► browser

Frontend
  └── useTradingEvents() — STOMP subscribe sur /topic/trading-events
        └── LiveFeed component — section "Activité live" sur la page Vue d'ensemble
              └── liste scrollable des derniers events (max 50, LIFO)
              └── CYCLE_COMPLETE → queryClient.invalidateQueries(['portfolio', 'trades', 'performance'])
```

---

## Backend

### Nouveaux fichiers

**`src/main/java/com/halaltrader/backend/config/WebSocketConfig.java`**

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
```

**`src/main/java/com/halaltrader/backend/websocket/TradingEventDto.java`**

Record Java immuable représentant un event trading :

```java
public record TradingEventDto(
    String type,           // "TRADE_EXECUTED" | "CYCLE_COMPLETE"
    String symbol,         // nullable pour CYCLE_COMPLETE
    String action,         // "BUY" | "SELL" | "HOLD" | null
    Integer quantity,      // nullable
    BigDecimal price,      // nullable
    String agentSummary,   // résumé court du DecisionAgent, nullable
    Integer totalDecisions,// pour CYCLE_COMPLETE, nullable sinon
    Instant timestamp
) {}
```

**`src/main/java/com/halaltrader/backend/websocket/TradingEventPublisher.java`**

```java
@Service
@RequiredArgsConstructor
public class TradingEventPublisher {

    private final SimpMessagingTemplate messaging;

    public void publishTradeExecuted(String symbol, String action,
                                     int quantity, BigDecimal price,
                                     String agentSummary) {
        messaging.convertAndSend("/topic/trading-events",
            new TradingEventDto("TRADE_EXECUTED", symbol, action,
                quantity, price, agentSummary, null, Instant.now()));
    }

    public void publishCycleComplete(int totalDecisions) {
        messaging.convertAndSend("/topic/trading-events",
            new TradingEventDto("CYCLE_COMPLETE", null, null,
                null, null, null, totalDecisions, Instant.now()));
    }
}
```

### Modification existante

**`TradingOrchestrator.java`**

- Injecter `TradingEventPublisher`
- Après chaque décision persistée : appeler `publisher.publishTradeExecuted(...)`
- Après la boucle sur tous les actifs : appeler `publisher.publishCycleComplete(count)`

L'appel `publishTradeExecuted` reçoit le résumé du `DecisionAgent` (dernière ligne du raisonnement IA, ou la raison de l'action). L'orchestrateur a déjà accès à ces données lors de la persistance du trade.

### Dépendance Maven

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

---

## Frontend

### Nouvelles dépendances

```
@stomp/stompjs
sockjs-client
@types/sockjs-client
```

### Nouveaux fichiers

**`src/api/websocket.ts`**

Hook `useTradingEvents()` :
- Crée un `Client` STOMP (SockJS transport sur `/ws`)
- `onConnect` : subscribe à `/topic/trading-events`
- À chaque message : parse le JSON, prepend à la liste locale (max 50 items via `slice(0, 50)`)
- Si `type === 'CYCLE_COMPLETE'` : invalide les queries `['portfolio']`, `['trades']`, `['performance']` via `useQueryClient()`
- `onDisconnect` : reconnexion automatique gérée par `@stomp/stompjs` (`reconnectDelay: 5000`)
- Cleanup `client.deactivate()` au unmount

```typescript
export interface TradingEvent {
  type: 'TRADE_EXECUTED' | 'CYCLE_COMPLETE'
  symbol: string | null
  action: 'BUY' | 'SELL' | 'HOLD' | null
  quantity: number | null
  price: number | null
  agentSummary: string | null
  totalDecisions: number | null
  timestamp: string
}

export function useTradingEvents(): TradingEvent[]
```

**`src/components/LiveFeed.tsx`**

Composant affichant la liste des events :
- Titre "Activité live" avec indicateur de connexion (point vert animé si connecté)
- Si liste vide : message "En attente du prochain cycle..." en texte muted
- Pour chaque `TRADE_EXECUTED` : `<Badge>` action (BUY/SELL/HOLD) + symbole + prix + agentSummary tronqué (max 80 chars) + timestamp relatif ("il y a 2 min")
- Pour chaque `CYCLE_COMPLETE` : ligne spéciale teal "Cycle terminé — N décisions" + timestamp
- Hauteur fixe avec scroll (`max-h-64 overflow-y-auto`)
- Styles cohérents avec le thème dark/light existant

### Modification existante

**`src/features/overview/index.tsx`**

Ajouter `<LiveFeed />` en bas de la page Overview, après les derniers trades. Le composant gère sa propre connexion WebSocket via `useTradingEvents()`.

---

## Tests backend

**`WebSocketConfigTest.java`** (`@SpringBootTest` + `WebSocketStompClient`) :
- Test `connectToWebSocket_succeeds` : vérifie que la connexion au endpoint `/ws` réussit
- Test `publishTradeExecuted_receivedBySubscriber` : publie via `TradingEventPublisher`, vérifie réception sur `/topic/trading-events`
- Test `publishCycleComplete_receivedBySubscriber` : idem pour `CYCLE_COMPLETE`

---

## Ce qui ne change pas

- Les polls TanStack Query existants (30s) restent en place — le WebSocket les complète, ne les remplace pas. Si le WebSocket est déconnecté, les données continuent de se rafraîchir via polling.
- Les endpoints REST — intacts
- La logique des agents — intacte
