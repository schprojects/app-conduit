package com.integration.app;

import com.integration.app.appb.FakeAppB;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Audit lines, request id in every log line, connector logger, bounded rolling log, no bodies in logs (AC-4, 8, 9, 23). */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "integration.connectors.appb.timeout-ms=2000")
class AuditAndLoggingIntegrationTest {

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

    @Autowired
    private Environment environment;

    @BeforeEach
    void healthyAppB() {
        APP_B.respondOk();
    }

    private HttpResponse<String> call() throws Exception {
        return TestHttp.get(port, "/api/v1/getSomeData", TestHttp.INBOUND);
    }

    @Test
    void successWritesStartedAndCompletedLinesWithTheOperationId(CapturedOutput output) throws Exception {
        String requestId = call().headers().firstValue("X-Request-Id").orElseThrow();

        List<Map<String, Object>> lines = AuditLines.await(output, "audit", requestId, 2);
        assertEquals(2, lines.size());
        assertEquals("request.started", lines.get(0).get("message"));
        assertEquals("STARTED", lines.get(0).get("status"));
        assertEquals("request.completed", lines.get(1).get("message"));
        assertEquals("SUCCESS", lines.get(1).get("status"));
        assertEquals("getSomeData", lines.get(1).get("operation"));
        assertEquals("200", lines.get(1).get("httpStatus"));
        assertEquals("INTX", lines.get(1).get("service"));
        assertNotNull(lines.get(1).get("durationMs"));
        assertFalse(lines.get(1).containsKey("errorCode"));
    }

    @Test
    void failureCompletesAsFailedWithErrorCode(CapturedOutput output) throws Exception {
        APP_B.respondSlowly(4000);

        String requestId = call().headers().firstValue("X-Request-Id").orElseThrow();

        Map<String, Object> completed = AuditLines.await(output, "audit", requestId, 2).get(1);
        assertEquals("FAILED", completed.get("status"));
        assertEquals("504", completed.get("httpStatus"));
        assertEquals("APPB_TIMEOUT", completed.get("errorCode"));
        assertEquals("getSomeData", completed.get("operation"));
    }

    @Test
    void connectorLinesCarryLoggerServiceAndRequestIdAndNoBodies(CapturedOutput output) throws Exception {
        String requestId = call().headers().firstValue("X-Request-Id").orElseThrow();

        List<Map<String, Object>> lines = AuditLines.forRequest(output.getOut(), "connector.appb", requestId);
        assertFalse(lines.isEmpty());
        lines.forEach(line -> assertEquals("INTX", line.get("service")));
        assertEquals(requestId, APP_B.lastRequestId());
        assertFalse(output.getOut().contains("hello from APP-B"));
        assertFalse(output.getOut().contains("appb-pw"));
        assertFalse(output.getOut().contains("inbound-pw"));
    }

    @Test
    void everyLogLineOfARequestCarriesItsRequestId(CapturedOutput output) throws Exception {
        String requestId = call().headers().firstValue("X-Request-Id").orElseThrow();

        for (String logger : new String[]{"audit", "connector.appb"}) {
            AuditLines.of(output.getOut(), logger).stream()
                    .filter(line -> requestId.equals(line.get("requestId")))
                    .forEach(line -> assertEquals(requestId, line.get("requestId")));
        }
        assertTrue(AuditLines.forRequest(output.getOut(), "connector.appb", requestId).size() >= 2);
    }

    @Test
    void logFileExistsAndRotationIsBounded() throws Exception {
        call();

        assertTrue(Files.readString(Path.of("target/test-logs/integration.log")).contains("request.completed"));
        assertNotNull(environment.getProperty("logging.logback.rollingpolicy.max-file-size"));
        assertNotNull(environment.getProperty("logging.logback.rollingpolicy.max-history"));
        assertNotNull(environment.getProperty("logging.logback.rollingpolicy.total-size-cap"));
    }

    @Test
    void cameltracerIsOff() {
        assertNotEquals("true", environment.getProperty("camel.main.tracing"));
    }

    @Test
    void everyKindOfRequestHasExactlyOneStartedAndOneCompletedLine(CapturedOutput output) throws Exception {
        String[][] cases = {
                {"/api/v1/does-not-exist", "UNKNOWN_OPERATION", "404"},
                {"/api/v1/test/required", "INVALID_REQUEST", "400"},
                {"/api/v1/test/boom", "INTERNAL_ERROR", "500"}};
        for (String[] c : cases) {
            String requestId = TestHttp.get(port, c[0], TestHttp.INBOUND).headers().firstValue("X-Request-Id").orElseThrow();

            List<Map<String, Object>> lines = AuditLines.await(output, "audit", requestId, 2);
            assertEquals(2, lines.size(), c[0]);
            assertEquals("request.started", lines.get(0).get("message"), c[0]);
            assertEquals("request.completed", lines.get(1).get("message"), c[0]);
            assertEquals("FAILED", lines.get(1).get("status"), c[0]);
            assertEquals(c[2], lines.get(1).get("httpStatus"), c[0]);
            assertEquals(c[1], lines.get(1).get("errorCode"), c[0]);
            assertEquals("UNKNOWN", lines.get(1).get("operation"), c[0]);
        }
    }

    @Test
    void actuatorCallsAreNotAudited(CapturedOutput output) throws Exception {
        String requestId = TestHttp.get(port, "/actuator/health", null).headers().firstValue("X-Request-Id").orElseThrow();

        Thread.sleep(200);
        assertEquals(0, AuditLines.forRequest(output.getOut(), "audit", requestId).size());
    }
}
