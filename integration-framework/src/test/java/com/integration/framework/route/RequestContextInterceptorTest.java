package com.integration.framework.route;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestContextInterceptorTest {

    private final RequestContextInterceptor interceptor = new RequestContextInterceptor();
    private final Exchange exchange = new DefaultExchange(new DefaultCamelContext());

    @AfterEach
    void clean() {
        MDC.clear();
    }

    @Test
    void capturesRequestIdAndDropsCallerCredentialsAndId() {
        MDC.put("requestId", "INTX-abc");
        exchange.getMessage().setHeader("Authorization", "Basic secret");
        exchange.getMessage().setHeader("X-Request-Id", "EVIL");
        exchange.getMessage().setHeader("customerId", "7");

        interceptor.captureRequest(exchange);

        assertEquals("INTX-abc", exchange.getProperty("requestId"));
        assertNull(exchange.getMessage().getHeader("Authorization"));
        assertNull(exchange.getMessage().getHeader("X-Request-Id"));
        assertEquals("7", exchange.getMessage().getHeader("customerId"));
    }

    @Test
    void firstDirectEndpointWithoutADotNamesTheOperation() {
        MDC.put("operation", "UNKNOWN");
        exchange.setProperty(Exchange.INTERCEPTED_ENDPOINT, "direct://getSomeData");
        interceptor.markOperation(exchange);
        assertEquals("getSomeData", MDC.get("operation"));

        exchange.setProperty(Exchange.INTERCEPTED_ENDPOINT, "direct://other");
        interceptor.markOperation(exchange);
        assertEquals("getSomeData", MDC.get("operation"));
    }

    @Test
    void capabilityEndpointsAndOtherComponentsDoNotNameTheOperation() {
        MDC.put("operation", "UNKNOWN");
        exchange.setProperty(Exchange.INTERCEPTED_ENDPOINT, "direct://appb.getSomeData");
        interceptor.markOperation(exchange);
        exchange.setProperty(Exchange.INTERCEPTED_ENDPOINT, "http://host/x");
        interceptor.markOperation(exchange);
        assertEquals("UNKNOWN", MDC.get("operation"));
    }
}
