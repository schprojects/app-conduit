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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuditIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private AuditStore store;

    private HttpResponse<String> get(String path) throws Exception {
        return TestHttp.get(port, path, TestHttp.INBOUND);
    }

    @Test
    void everyApiRequestHasAnAuditRecordWithFinalStatusAndEndTime() throws Exception {
        HttpResponse<String> response = get("/api/v1/unknown");

        String requestId = response.headers().firstValue("X-Request-Id").orElseThrow();
        AuditRecord record = store.findByRequestId(requestId).orElseThrow();
        assertEquals("INTX", record.serviceName());
        assertEquals(RequestStatus.FAILED, record.status());
        assertNotNull(record.endTime());
    }

    @Test
    void actuatorCallsAreNotAudited() throws Exception {
        String requestId = TestHttp.get(port, "/actuator/health", null).headers().firstValue("X-Request-Id").orElseThrow();

        assertTrue(store.findByRequestId(requestId).isEmpty());
    }
}
