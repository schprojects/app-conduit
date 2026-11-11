package com.integration.app.appb;

import com.integration.framework.error.IntegrationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestAppBClientTest {

    private FakeAppB appB;

    @BeforeEach
    void setUp() {
        appB = new FakeAppB();
    }

    @AfterEach
    void tearDown() {
        appB.close();
        MDC.clear();
    }

    private RestAppBClient client(String baseUrl, Duration timeout) {
        return new RestAppBClient(new AppBProperties(baseUrl, "user", "pw", timeout));
    }

    @Test
    void returnsDataAndSendsCredentialsAndRequestId() {
        MDC.put("requestId", "INTX-abc");

        SomeData data = client(appB.baseUrl(), Duration.ofSeconds(2)).getSomeData();

        assertEquals("hello from APP-B", data.data());
        assertEquals("Basic " + Base64.getEncoder().encodeToString("user:pw".getBytes()), appB.lastAuthorization());
        assertEquals("INTX-abc", appB.lastRequestId());
    }

    @Test
    void slowAnswerIsATimeout() {
        appB.respondSlowly(1500);

        IntegrationException e = assertThrows(IntegrationException.class,
                () -> client(appB.baseUrl(), Duration.ofMillis(300)).getSomeData());

        assertEquals("APPB_TIMEOUT", e.errorCode());
        assertEquals(504, e.httpStatus().value());
    }

    @Test
    void errorStatusIsUnavailable() {
        appB.respondWithStatus(500);

        IntegrationException e = assertThrows(IntegrationException.class,
                () -> client(appB.baseUrl(), Duration.ofSeconds(2)).getSomeData());

        assertEquals("APPB_UNAVAILABLE", e.errorCode());
        assertEquals(502, e.httpStatus().value());
    }

    @Test
    void connectionRefusedIsUnavailable() {
        IntegrationException e = assertThrows(IntegrationException.class,
                () -> client("http://localhost:1", Duration.ofSeconds(2)).getSomeData());

        assertEquals("APPB_UNAVAILABLE", e.errorCode());
    }

    @Test
    void timeoutDefaultsToTenSeconds() {
        assertEquals(Duration.ofSeconds(10), new AppBProperties("http://x", "u", "p", null).timeout());
    }
}
