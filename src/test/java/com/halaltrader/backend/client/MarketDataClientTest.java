package com.halaltrader.backend.client;

import com.halaltrader.backend.dto.MarketDataDto;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;

class MarketDataClientTest {

    private MockWebServer server;
    private MarketDataClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start(InetAddress.getLoopbackAddress(), 0);
        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:" + server.getPort())
                .build();
        client = new MarketDataClient(webClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void getMarketData_mapsFieldsCorrectly() {
        server.enqueue(new MockResponse()
                .setBody("""
                    {"symbol":"AAPL","price":175.5,"change_pct":1.2,"volume":50000000,
                     "rsi":58.3,"macd":1.23,"macd_signal":0.98,"ma20":172.0,"ma50":168.0}
                    """)
                .addHeader("Content-Type", "application/json"));

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
