package com.integration.framework.route;

import com.integration.framework.tracking.AuditLog;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestContextInterceptorTest {

    private final java.util.List<String> started = new java.util.ArrayList<>();
    private final AuditLog audit = new AuditLog() {
        @Override
        public void started(String requestId, String operation) {
            started.add(requestId + "/" + operation);
        }
    };
    private final RequestContextInterceptor interceptor = new RequestContextInterceptor(audit);
    private final Exchange exchange = new DefaultExchange(new DefaultCamelContext());

    private void httpRequest(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setAttribute("requestId", "INTX-abc");
        exchange.getMessage().setHeader("CamelHttpServletRequest", request);
    }

    @Test
    void takesTheIdFromTheServletRequestDropsCallerCredentialsAndWritesTheStartedLine() {
        httpRequest("/api/v1/getSomeData");
        exchange.getMessage().setHeader("Authorization", "Basic secret");
        exchange.getMessage().setHeader("X-Request-Id", "EVIL");
        exchange.getMessage().setHeader("customerId", "7");

        interceptor.captureRequest(exchange);

        assertEquals("INTX-abc", exchange.getProperty("requestId"));
        assertEquals("UNKNOWN", exchange.getProperty("operation"));
        assertNull(exchange.getMessage().getHeader("Authorization"));
        assertNull(exchange.getMessage().getHeader("X-Request-Id"));
        assertEquals("7", exchange.getMessage().getHeader("customerId"));
        assertEquals(java.util.List.of("INTX-abc/UNKNOWN"), started);
        assertTrue((Boolean) exchange.getProperty("auditStarted"));
    }

    @Test
    void anIdAlreadyOnTheExchangeIsKeptAndNothingIsAuditedWithoutAnHttpRequest() {
        exchange.setProperty("requestId", "INTX-given");

        interceptor.captureRequest(exchange);

        assertEquals("INTX-given", exchange.getProperty("requestId"));
        assertTrue(started.isEmpty());
    }

    @Test
    void actuatorCallsAreNotAudited() {
        httpRequest("/actuator/health");

        interceptor.captureRequest(exchange);

        assertTrue(started.isEmpty());
    }

    @Test
    void firstDirectEndpointWithoutADotNamesTheOperation() {
        exchange.setProperty("operation", "UNKNOWN");
        exchange.setProperty(Exchange.INTERCEPTED_ENDPOINT, "direct://getSomeData");
        interceptor.markOperation(exchange);
        assertEquals("getSomeData", exchange.getProperty("operation"));

        exchange.setProperty(Exchange.INTERCEPTED_ENDPOINT, "direct://other");
        interceptor.markOperation(exchange);
        assertEquals("getSomeData", exchange.getProperty("operation"));
    }

    @Test
    void capabilityEndpointsAndOtherComponentsDoNotNameTheOperation() {
        exchange.setProperty("operation", "UNKNOWN");
        exchange.setProperty(Exchange.INTERCEPTED_ENDPOINT, "direct://appb.getSomeData");
        interceptor.markOperation(exchange);
        exchange.setProperty(Exchange.INTERCEPTED_ENDPOINT, "http://host/x");
        interceptor.markOperation(exchange);
        assertEquals("UNKNOWN", exchange.getProperty("operation"));
    }
}
