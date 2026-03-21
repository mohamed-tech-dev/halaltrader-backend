package com.halaltrader.backend.client;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;

class AnthropicClientTest {

    private MockWebServer server;
    private AnthropicClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start(InetAddress.getLoopbackAddress(), 0);
        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:" + server.getPort())
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
