# Phase 3C — WebSocket Live Updates

## Goal

Remplacer les polls toutes les 30s du frontend par un feed WebSocket temps réel. Pendant un cycle de trading, chaque décision IA BUY/SELL (pas HOLD) est poussée instantanément au browser. Un event `CYCLE_COMPLETE` en fin de cycle invalide le cache TanStack Query pour rafraîchir portfolio, trades et performance.

## Architecture

```
TradingOrchestrator
  └── après chaque décision BUY/SELL persistée ──► TradingEventPublisher
  └── après la boucle complète              ──► TradingEventPublisher
                                                    └── SimpMessagingTemplate
                                                          └── STOMP /topic/trading-events ──► browser

Frontend
  └── useTradingEvents() — STOMP subscribe sur /topic/trading-events
        └── retourne { events: TradingEvent[], connected: boolean }
        └── CYCLE_COMPLETE → queryClient.invalidateQueries({ queryKey: ['portfolio'] })
                           → queryClient.invalidateQueries({ queryKey: ['trades'] })
                           → queryClient.invalidateQueries({ queryKey: ['performance'] })
        └── LiveFeed component — section "Activité live" sur la page Vue d'ensemble
              └── liste scrollable des derniers events (max 50, LIFO)
              └── indicateur de connexion (point vert animé) via connected
```

Note : les décisions HOLD ne sont pas publiées sur le WebSocket — elles ne génèrent pas de trade persisté et ne sont pas utiles dans le feed.

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
        // setAllowedOriginPatterns("*") couvre le CORS WebSocket —
        // la CorsConfig existante (WebMvcConfigurer) ne s'applique pas aux endpoints WS.
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

Record Java immuable (Jackson 2.17 sur Spring Boot 3.3.5 sérialise les records nativement) :

```java
public record TradingEventDto(
    String type,            // "TRADE_EXECUTED" | "CYCLE_COMPLETE"
    String symbol,          // null pour CYCLE_COMPLETE
    String action,          // "BUY" | "SELL" | null
    BigDecimal quantity,    // BigDecimal — cohérent avec Trade.quantity en DB
    BigDecimal price,       // null pour CYCLE_COMPLETE
    String agentSummary,    // decision.reasoning() tronqué, null pour CYCLE_COMPLETE
    Integer totalDecisions, // null pour TRADE_EXECUTED
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
                                     BigDecimal quantity, BigDecimal price,
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

Note : `SimpMessagingTemplate.convertAndSend` ignore silencieusement les appels sans subscribers — aucune gestion d'erreur nécessaire côté publisher.

### Modification existante

**`TradingOrchestrator.java`**

- Injecter `TradingEventPublisher`
- Après chaque décision BUY ou SELL persistée : appeler `publisher.publishTradeExecuted(symbol, action, quantity, price, decision.reasoning())`
  - `decision.reasoning()` est le string produit par `DecisionAgent` — l'utiliser directement comme `agentSummary`
  - Les décisions HOLD ne sont **pas** publiées (pas de trade persisté, pas d'event WS)
- Après la boucle sur tous les actifs : appeler `publisher.publishCycleComplete(count)` où `count` = nombre de BUY/SELL exécutés

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
```

Note : `sockjs-client` 1.6+ inclut ses propres types TypeScript — pas besoin de `@types/sockjs-client`.

### Nouveaux fichiers

**`src/api/websocket.ts`**

Hook `useTradingEvents()` :

```typescript
import { useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

export interface TradingEvent {
  type: 'TRADE_EXECUTED' | 'CYCLE_COMPLETE'
  symbol: string | null
  action: 'BUY' | 'SELL' | null
  quantity: number | null
  price: number | null
  agentSummary: string | null
  totalDecisions: number | null
  timestamp: string
}

export function useTradingEvents(): { events: TradingEvent[], connected: boolean } {
  const queryClient = useQueryClient()
  const [events, setEvents] = useState<TradingEvent[]>([])
  const [connected, setConnected] = useState(false)

  useEffect(() => {
    const client = new Client({
      // SockJS transport — webSocketFactory requis (pas brokerURL)
      // pour SockJS avec @stomp/stompjs v6+
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 5000,
      onConnect: () => {
        setConnected(true)
        client.subscribe('/topic/trading-events', (message) => {
          const event: TradingEvent = JSON.parse(message.body)
          // Functional updater pour éviter les stale closures
          setEvents(prev => [event, ...prev].slice(0, 50))
          if (event.type === 'CYCLE_COMPLETE') {
            queryClient.invalidateQueries({ queryKey: ['portfolio'] })
            queryClient.invalidateQueries({ queryKey: ['trades'] })
            queryClient.invalidateQueries({ queryKey: ['performance'] })
          }
        })
      },
      onDisconnect: () => setConnected(false),
    })
    client.activate()
    return () => { client.deactivate() }
  }, [queryClient])

  return { events, connected }
}
```

**`src/components/LiveFeed.tsx`**

Composant affichant la liste des events :
- Titre "Activité live" avec indicateur de connexion : point vert animé (`animate-pulse bg-green-400`) si `connected`, gris sinon
- Si liste vide : message "En attente du prochain cycle..." en texte muted
- Pour chaque `TRADE_EXECUTED` : `<Badge>` action (BUY/SELL) + symbole + prix formaté + `agentSummary` tronqué (max 80 chars) + timestamp relatif
- Pour chaque `CYCLE_COMPLETE` : ligne spéciale accent teal "Cycle terminé — N décisions" + timestamp
- Hauteur fixe avec scroll (`max-h-64 overflow-y-auto`)
- Styles cohérents avec le thème dark/light via classes Tailwind `dark:`

### Modification existante

**`src/features/overview/index.tsx`**

Ajouter `<LiveFeed />` en bas de la page Overview, après la section des derniers trades. Le composant reçoit aucune prop — il gère sa connexion WebSocket en interne.

---

## Tests backend

**`WebSocketIntegrationTest.java`**

Pattern : `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)` + `@LocalServerPort` + `WebSocketStompClient`.

Structure des 3 tests :
- Utiliser `BlockingQueue<TradingEventDto>` + `CompletableFuture` pour synchroniser les messages asynchrones
- Timeout de 3 secondes par assertion pour éviter les tests qui bloquent indéfiniment
- `WebSocketStompClient` se connecte à `http://localhost:{port}/ws` via `SockJsClient` (URL HTTP, pas `ws://` — SockJS négocie le protocole via HTTP)

Tests :
1. `connectToWebSocket_succeeds` — vérifie que la connexion au endpoint `/ws` s'établit (future complète sans exception)
2. `publishTradeExecuted_receivedBySubscriber` — subscribe, appelle `publisher.publishTradeExecuted(...)`, vérifie que le message reçu a `type=TRADE_EXECUTED` et le bon symbol
3. `publishCycleComplete_receivedBySubscriber` — idem pour `CYCLE_COMPLETE` avec `totalDecisions`

---

## Ce qui ne change pas

- Les polls TanStack Query existants (30s) restent en place — le WebSocket les complète. Si le WebSocket est déconnecté, les données continuent de se rafraîchir via polling.
- Les endpoints REST — intacts
- La logique des agents — intacte
