package com.integration.app.somedata;

import com.integration.app.TestHttp;
import com.integration.app.appb.FakeAppB;
import com.integration.framework.audit.AuditRecord;
import com.integration.framework.audit.AuditStore;
import com.integration.framework.audit.RequestStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sample integration end to end: inbound call -> controller -> Camel route -> Business Service -> APP-B client. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "appb.timeout=2s")
class GetSomeDataIntegrationTest {

    private static final FakeAppB APP_B = new FakeAppB();

    @DynamicPropertySource
    static void appB(DynamicPropertyRegistry registry) {
        registry.add("appb.base-url", APP_B::baseUrl);
    }

    @AfterAll
    static void stop() {
        APP_B.close();
    }

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private AuditStore store;

    @BeforeEach
    void healthyAppB() {
        APP_B.respondOk();
    }

    private HttpResponse<String> call() throws Exception {
        return TestHttp.get(port, "/api/v1/getSomeData", TestHttp.INBOUND);
    }

    @Test
    void returnsAppBDataWithRequestIdAndAuditRecord() throws Exception {
        HttpResponse<String> response = call();

        String requestId = response.headers().firstValue("X-Request-Id").orElseThrow();
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"data\":\"hello from APP-B\""));
        assertEquals(requestId, APP_B.lastRequestId());
        AuditRecord record = store.findByRequestId(requestId).orElseThrow();
        assertEquals("getSomeData", record.operation());
        assertEquals(RequestStatus.SUCCESS, record.status());
        assertTrue(Files.readString(Path.of("target/test-logs/appb.log")).contains(requestId));
    }

    @Test
    void appBTimeoutReturns504() throws Exception {
        APP_B.respondSlowly(4000);

        HttpResponse<String> response = call();

        String requestId = response.headers().firstValue("X-Request-Id").orElseThrow();
        assertEquals(504, response.statusCode());
        assertTrue(response.body().contains("\"errorCode\":\"APPB_TIMEOUT\""));
        AuditRecord record = store.findByRequestId(requestId).orElseThrow();
        assertEquals(RequestStatus.FAILED, record.status());
        assertEquals("APPB_TIMEOUT", record.errorCode());
    }

    @Test
    void appBErrorReturns502() throws Exception {
        APP_B.respondWithStatus(500);

        HttpResponse<String> response = call();

        assertEquals(502, response.statusCode());
        assertTrue(response.body().contains("\"errorCode\":\"APPB_UNAVAILABLE\""));
    }
}
