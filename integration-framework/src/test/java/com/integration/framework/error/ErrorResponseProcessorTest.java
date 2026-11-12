package com.integration.framework.error;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import tools.jackson.databind.json.JsonMapper;

import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ErrorResponseProcessorTest {

    private final ErrorResponseProcessor processor = new ErrorResponseProcessor(JsonMapper.builder().build());

    @AfterEach
    void clean() {
        MDC.clear();
    }

    @Test
    void writesStandardBodyStatusHeadersAndErrorCode() {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.setProperty(Exchange.EXCEPTION_CAUGHT, new SocketTimeoutException("slow"));
        exchange.setProperty("targetApp", "APPB");
        exchange.setProperty("requestId", "INTX-abc");
        exchange.getMessage().setHeader("Authorization", "Basic leftover");

        processor.process(exchange);

        assertEquals(504, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("INTX-abc", exchange.getMessage().getHeader("X-Request-Id"));
        assertEquals("application/json", exchange.getMessage().getHeader(Exchange.CONTENT_TYPE));
        assertFalse(exchange.getMessage().getHeaders().containsKey("Authorization"));
        assertEquals("{\"requestId\":\"INTX-abc\",\"status\":\"FAILED\",\"errorCode\":\"APPB_TIMEOUT\","
                + "\"message\":\"APPB timed out\"}", exchange.getMessage().getBody(String.class));
        assertEquals("APPB_TIMEOUT", MDC.get("errorCode"));
    }

    @Test
    void unexpectedFailureReturnsInternalErrorWithoutDetails() {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.setProperty(Exchange.EXCEPTION_CAUGHT, new IllegalStateException("secret detail"));
        exchange.setProperty("requestId", "INTX-abc");

        processor.process(exchange);

        assertEquals(500, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertFalse(exchange.getMessage().getBody(String.class).contains("secret detail"));
        assertEquals("INTERNAL_ERROR", MDC.get("errorCode"));
    }
}
