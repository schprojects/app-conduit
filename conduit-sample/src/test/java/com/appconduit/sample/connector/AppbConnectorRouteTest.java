package com.appconduit.sample.connector;

import com.appconduit.sample.TestHttp;
import com.appconduit.sample.appb.FakeAppB;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Level 1 route test: the capability endpoint direct:appb.getSomeData on its own, without the HTTP front. */
@SpringBootTest(properties = "integration.connectors.appb.timeout-ms=500")
class AppbConnectorRouteTest {

    private static final FakeAppB APP_B = new FakeAppB();

    @DynamicPropertySource
    static void appB(DynamicPropertyRegistry registry) {
        registry.add("integration.connectors.appb.base-url", APP_B::baseUrl);
    }

    @AfterAll
    static void stop() {
        APP_B.close();
    }

    @Autowired
    private ProducerTemplate template;

    @BeforeEach
    void healthyAppB() {
        APP_B.respondOk();
    }

    /** The request id is an exchange property, as the entry interceptor sets it for a real request. */
    private Exchange call() {
        return template.request("direct:appb.getSomeData", exchange -> {
            exchange.setProperty("requestId", "INTX-test12345678");
            exchange.getMessage().setHeader("Authorization", TestHttp.INBOUND);
            exchange.getMessage().setHeader("X-Request-Id", "EVIL-123");
        });
    }

    @Test
    void returnsTheTargetDataAndSendsOnlyItsOwnCredentialsAndTheGeneratedId() {
        Exchange result = call();

        assertTrue(result.getMessage().getBody(String.class).contains("hello from APP-B"));
        assertEquals("APPB", result.getProperty("targetApp"));
        assertEquals("INTX-test12345678", APP_B.lastRequestId());
        assertEquals(TestHttp.basic("appb-user", "appb-pw"), APP_B.lastAuthorization());
    }

    @Test
    void aSlowTargetIsAnsweredByTheSharedErrorRuleWithATimeout() {
        APP_B.respondSlowly(2000);

        Exchange result = call();

        assertEquals(504, result.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertTrue(result.getMessage().getBody(String.class).contains("\"errorCode\":\"APPB_TIMEOUT\""));
    }

    @Test
    void anErrorStatusIsAnsweredByTheSharedErrorRuleAsUnavailable() {
        APP_B.respondWithStatus(500);

        Exchange result = call();

        assertEquals(502, result.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertTrue(result.getMessage().getBody(String.class).contains("\"errorCode\":\"APPB_UNAVAILABLE\""));
    }
}
