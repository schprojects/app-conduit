package com.integration.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Failures outside the sample operation (APP-B failures are covered by GetSomeDataCharacterizationTest). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorResponseIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    private HttpResponse<String> get(String path) throws Exception {
        return TestHttp.get(port, path, TestHttp.INBOUND);
    }

    @Test
    void unknownOperationReturns404Standard() throws Exception {
        HttpResponse<String> response = get("/api/v1/does-not-exist");

        String requestId = response.headers().firstValue("X-Request-Id").orElseThrow();
        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("\"errorCode\":\"UNKNOWN_OPERATION\""));
        assertTrue(response.body().contains("\"requestId\":\"" + requestId + "\""));
    }

    @Test
    void contractViolationReturns400Standard() throws Exception {
        HttpResponse<String> response = get("/api/v1/test/required");

        String requestId = response.headers().firstValue("X-Request-Id").orElseThrow();
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("\"errorCode\":\"INVALID_REQUEST\""));
        assertTrue(response.body().contains("\"requestId\":\"" + requestId + "\""));
        assertEquals(200, get("/api/v1/test/required?q=x").statusCode());
    }

    @Test
    void unexpectedFailureReturns500WithoutDetails() throws Exception {
        HttpResponse<String> response = get("/api/v1/test/boom");

        assertEquals(500, response.statusCode());
        assertTrue(response.body().contains("\"errorCode\":\"INTERNAL_ERROR\""));
        assertFalse(response.body().contains("secret detail"));
    }
}
