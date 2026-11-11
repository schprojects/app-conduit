package com.integration.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RequestIdIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Test
    void everyResponseCarriesGeneratedRequestIdAndCallerIdIsIgnored() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
                        .header("X-Request-Id", "CALLER-123").build(),
                HttpResponse.BodyHandlers.ofString());

        String id = response.headers().firstValue("X-Request-Id").orElseThrow();
        assertTrue(id.matches("INTX-[A-Za-z0-9]{12}"));
        assertNotEquals("CALLER-123", id);
    }
}
