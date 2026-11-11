package com.integration.app;

import com.integration.framework.audit.AuditRecord;
import com.integration.framework.audit.AuditStore;
import com.integration.framework.audit.RequestStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private AuditStore store;

    private int status(String path, String authorization) throws Exception {
        return TestHttp.get(port, path, authorization).statusCode();
    }

    @Test
    void missingOrInvalidCredentialsReturn401AuthFailedAndAreAudited() throws Exception {
        for (String authorization : new String[]{null, TestHttp.basic("inbound", "wrong")}) {
            HttpResponse<String> response = TestHttp.get(port, "/api/v1/test/appb-log", authorization);

            assertEquals(401, response.statusCode());
            String requestId = response.headers().firstValue("X-Request-Id").orElseThrow();
            assertTrue(response.body().contains("\"errorCode\":\"AUTH_FAILED\""));
            assertTrue(response.body().contains("\"requestId\":\"" + requestId + "\""));
            AuditRecord record = store.findByRequestId(requestId).orElseThrow();
            assertEquals(RequestStatus.FAILED, record.status());
            assertEquals("AUTH_FAILED", record.errorCode());
        }
    }

    @Test
    void inboundAccountCanCallApiButNotActuatorMetrics() throws Exception {
        assertEquals(200, status("/api/v1/test/appb-log", TestHttp.INBOUND));
        assertEquals(401, status("/actuator/metrics", TestHttp.INBOUND));
    }

    @Test
    void operationsAccountCanReadMetricsAndInfoButNotCallApi() throws Exception {
        assertEquals(200, status("/actuator/metrics", TestHttp.OPERATIONS));
        assertEquals(200, status("/actuator/info", TestHttp.OPERATIONS));
        assertEquals(401, status("/api/v1/test/appb-log", TestHttp.OPERATIONS));
    }

    @Test
    void healthIsOpenAndOtherActuatorEndpointsAreNotExposed() throws Exception {
        assertEquals(200, status("/actuator/health", null));
        assertEquals(404, status("/actuator/env", TestHttp.OPERATIONS));
    }
}
