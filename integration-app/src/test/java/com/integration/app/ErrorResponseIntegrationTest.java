package com.integration.app;

import com.integration.framework.audit.AuditRecord;
import com.integration.framework.audit.AuditStore;
import com.integration.framework.audit.RequestStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorResponseIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private AuditStore store;

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void timeoutReturnsStandardErrorAndAuditFailure() throws Exception {
        HttpResponse<String> response = get("/api/v1/test/timeout");

        String requestId = response.headers().firstValue("X-Request-Id").orElseThrow();
        assertEquals(504, response.statusCode());
        assertTrue(response.body().contains("\"requestId\":\"" + requestId + "\""));
        assertTrue(response.body().contains("\"status\":\"FAILED\""));
        assertTrue(response.body().contains("\"errorCode\":\"APPB_TIMEOUT\""));
        AuditRecord record = store.findByRequestId(requestId).orElseThrow();
        assertEquals(RequestStatus.FAILED, record.status());
        assertEquals("APPB_TIMEOUT", record.errorCode());
    }

    @Test
    void unknownOperationReturns404Standard() throws Exception {
        HttpResponse<String> response = get("/api/v1/does-not-exist");

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("\"errorCode\":\"UNKNOWN_OPERATION\""));
    }

    @Test
    void unexpectedFailureReturns500WithoutDetails() throws Exception {
        HttpResponse<String> response = get("/api/v1/test/boom");

        assertEquals(500, response.statusCode());
        assertTrue(response.body().contains("\"errorCode\":\"INTERNAL_ERROR\""));
        assertFalse(response.body().contains("secret detail"));
    }
}
