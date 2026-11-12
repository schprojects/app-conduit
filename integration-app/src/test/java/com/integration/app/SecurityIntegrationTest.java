package com.integration.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    private int status(String path, String authorization) throws Exception {
        return TestHttp.get(port, path, authorization).statusCode();
    }

    @Test
    void missingOrInvalidCredentialsReturn401AuthFailedAndAreAudited(CapturedOutput output) throws Exception {
        for (String authorization : new String[]{null, TestHttp.basic("inbound", "wrong")}) {
            HttpResponse<String> response = TestHttp.get(port, "/api/v1/test/ok", authorization);

            assertEquals(401, response.statusCode());
            String requestId = response.headers().firstValue("X-Request-Id").orElseThrow();
            assertTrue(response.body().contains("\"errorCode\":\"AUTH_FAILED\""));
            assertTrue(response.body().contains("\"requestId\":\"" + requestId + "\""));
            List<Map<String, Object>> lines = AuditLines.await(output, "audit", requestId, 2);
            assertEquals(2, lines.size());
            assertEquals("request.started", lines.get(0).get("message"));
            assertEquals("request.completed", lines.get(1).get("message"));
            assertEquals("FAILED", lines.get(1).get("status"));
            assertEquals("401", lines.get(1).get("httpStatus"));
            assertEquals("AUTH_FAILED", lines.get(1).get("errorCode"));
            assertEquals("UNKNOWN", lines.get(1).get("operation"));
        }
    }

    @Test
    void inboundAccountCanCallApiButNotActuatorMetrics() throws Exception {
        assertEquals(200, status("/api/v1/test/ok", TestHttp.INBOUND));
        assertEquals(401, status("/actuator/metrics", TestHttp.INBOUND));
    }

    @Test
    void operationsAccountCanReadMetricsAndInfoButNotCallApi() throws Exception {
        assertEquals(200, status("/actuator/metrics", TestHttp.OPERATIONS));
        assertEquals(200, status("/actuator/info", TestHttp.OPERATIONS));
        assertEquals(401, status("/api/v1/test/ok", TestHttp.OPERATIONS));
    }

    @Test
    void healthIsOpenAndOtherActuatorEndpointsAreNotExposed() throws Exception {
        assertEquals(200, status("/actuator/health", null));
        assertEquals(404, status("/actuator/env", TestHttp.OPERATIONS));
    }
}
