package com.integration.framework.tracking;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestLifecycleFilterTest {

    /** One entry per audit line: message plus a copy of the log context at the time of the line. */
    private final List<Map<String, String>> lines = new ArrayList<>();
    private final AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
        @Override
        protected void append(ILoggingEvent event) {
            Map<String, String> line = new java.util.HashMap<>(event.getMDCPropertyMap());
            line.put("message", event.getFormattedMessage());
            lines.add(line);
        }
    };
    private final Logger auditLogger = (Logger) LoggerFactory.getLogger("audit");
    private final RequestLifecycleFilter filter = new RequestLifecycleFilter(new RequestIdGenerator("INTX"));

    @BeforeEach
    void capture() {
        appender.setContext(auditLogger.getLoggerContext());
        appender.start();
        auditLogger.addAppender(appender);
    }

    @AfterEach
    void release() {
        auditLogger.detachAppender(appender);
        MDC.clear();
    }

    private MockHttpServletResponse call(String path, MockHttpServletRequest request, MockFilterChain chain)
            throws Exception {
        request.setRequestURI(path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void generatesIdIgnoresCallerIdAndWritesTwoAuditLines() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "EVIL-123");

        MockHttpServletResponse response = call("/api/v1/getSomeData", request, new MockFilterChain());

        String id = response.getHeader("X-Request-Id");
        assertTrue(id.matches("INTX-[A-Za-z0-9]{12}"));
        assertNotEquals("EVIL-123", id);
        assertEquals(2, lines.size());
        assertEquals("request.started", lines.get(0).get("message"));
        assertEquals("STARTED", lines.get(0).get("status"));
        assertEquals("UNKNOWN", lines.get(0).get("operation"));
        assertEquals(id, lines.get(0).get("requestId"));
        assertEquals("request.completed", lines.get(1).get("message"));
        assertEquals("SUCCESS", lines.get(1).get("status"));
        assertEquals("200", lines.get(1).get("httpStatus"));
        assertTrue(Long.parseLong(lines.get(1).get("durationMs")) >= 0);
        assertNull(lines.get(1).get("errorCode"));
    }

    @Test
    void failedRequestCompletesWithErrorCodeAndTheOperationSetDuringTheRequest() throws Exception {
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
                MDC.put("operation", "getSomeData");
                MDC.put("errorCode", "APPB_TIMEOUT");
                ((MockHttpServletResponse) response).setStatus(504);
            }
        };

        call("/api/v1/getSomeData", new MockHttpServletRequest(), chain);

        assertEquals("FAILED", lines.get(1).get("status"));
        assertEquals("504", lines.get(1).get("httpStatus"));
        assertEquals("APPB_TIMEOUT", lines.get(1).get("errorCode"));
        assertEquals("getSomeData", lines.get(1).get("operation"));
    }

    @Test
    void escapedExceptionIsRecordedAsInternalError() {
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
                    throws ServletException {
                throw new IllegalStateException("boom");
            }
        };

        assertThrows(IllegalStateException.class, () -> call("/x", new MockHttpServletRequest(), chain));

        assertEquals("FAILED", lines.get(1).get("status"));
        assertEquals("INTERNAL_ERROR", lines.get(1).get("errorCode"));
        assertEquals("500", lines.get(1).get("httpStatus"));
    }

    @Test
    void actuatorCallsGetAnIdButNoAuditLines() throws Exception {
        MockHttpServletResponse response = call("/actuator/health", new MockHttpServletRequest(), new MockFilterChain());

        assertTrue(response.getHeader("X-Request-Id").startsWith("INTX-"));
        assertTrue(lines.isEmpty());
    }

    @Test
    void logContextIsClearedAfterTheRequest() throws Exception {
        call("/api/v1/getSomeData", new MockHttpServletRequest(), new MockFilterChain());

        assertNull(MDC.get("requestId"));
        assertNull(MDC.get("operation"));
    }
}
