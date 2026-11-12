package com.integration.app;

import com.integration.app.appb.FakeAppB;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Failure kinds of the APP-B connector that the characterization tests do not cover (AC-15). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConnectorFailureIntegrationTest {

    private static final FakeAppB APP_B = new FakeAppB();

    @DynamicPropertySource
    static void appB(DynamicPropertyRegistry registry) {
        registry.add("integration.connectors.appb.base-url", APP_B::baseUrl);
    }

    @AfterAll
    static void stop() {
        APP_B.close();
    }

    @Value("${local.server.port}")
    private int port;

    @Test
    void targetAnswering404ReturnsNotFound() throws Exception {
        APP_B.respondWithStatus(404);

        HttpResponse<String> response = TestHttp.get(port, "/api/v1/getSomeData", TestHttp.INBOUND);

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("\"errorCode\":\"APPB_NOT_FOUND\""));
    }

    @Test
    void targetAnsweringAnyOtherErrorReturnsUnavailableWithoutTargetDetails() throws Exception {
        APP_B.respondWithStatus(503);

        HttpResponse<String> response = TestHttp.get(port, "/api/v1/getSomeData", TestHttp.INBOUND);

        assertEquals(502, response.statusCode());
        assertTrue(response.body().contains("\"errorCode\":\"APPB_UNAVAILABLE\""));
        assertFalse(response.body().contains("503"));
    }
}
