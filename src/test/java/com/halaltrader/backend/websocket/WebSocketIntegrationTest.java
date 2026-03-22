package com.halaltrader.backend.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:wstest;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.flyway.enabled=false",
                "anthropic.api-key=test-key"
        }
)
class WebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TradingEventPublisher publisher;

    @Autowired
    private ObjectMapper objectMapper;

    private WebSocketStompClient buildClient() {
        SockJsClient sockJsClient = new SockJsClient(
                List.of(new WebSocketTransport(new StandardWebSocketClient())));
        WebSocketStompClient client = new WebSocketStompClient(sockJsClient);
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        client.setMessageConverter(converter);
        return client;
    }

    @Test
    void connectToWebSocket_succeeds() throws Exception {
        CompletableFuture<Boolean> connected = new CompletableFuture<>();
        WebSocketStompClient client = buildClient();

        client.connectAsync("http://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                connected.complete(true);
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                connected.completeExceptionally(exception);
            }
        });

        assertThat(connected.get(3, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void publishTradeExecuted_receivedBySubscriber() throws Exception {
        BlockingQueue<TradingEventDto> received = new LinkedBlockingQueue<>();
        CompletableFuture<StompSession> sessionFuture = new CompletableFuture<>();
        WebSocketStompClient client = buildClient();

        client.connectAsync("http://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                sessionFuture.complete(session);
            }
        });

        StompSession session = sessionFuture.get(3, TimeUnit.SECONDS);
        session.subscribe("/topic/trading-events", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TradingEventDto.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((TradingEventDto) payload);
            }
        });

        Thread.sleep(500);
        publisher.publishTradeExecuted("AAPL", "BUY", BigDecimal.valueOf(2), BigDecimal.valueOf(175.0), "Bull signal");

        TradingEventDto event = received.poll(3, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.type()).isEqualTo("TRADE_EXECUTED");
        assertThat(event.symbol()).isEqualTo("AAPL");
    }

    @Test
    void publishCycleComplete_receivedBySubscriber() throws Exception {
        BlockingQueue<TradingEventDto> received = new LinkedBlockingQueue<>();
        CompletableFuture<StompSession> sessionFuture = new CompletableFuture<>();
        WebSocketStompClient client = buildClient();

        client.connectAsync("http://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                sessionFuture.complete(session);
            }
        });

        StompSession session = sessionFuture.get(3, TimeUnit.SECONDS);
        session.subscribe("/topic/trading-events", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TradingEventDto.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((TradingEventDto) payload);
            }
        });

        Thread.sleep(500);
        publisher.publishCycleComplete(3);

        TradingEventDto event = received.poll(3, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.type()).isEqualTo("CYCLE_COMPLETE");
        assertThat(event.totalDecisions()).isEqualTo(3);
    }
}
