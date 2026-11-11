package com.integration.framework.audit;

import com.integration.framework.tracking.RequestTrackingFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditFilterTest {

    private JdbcAuditStore store;
    private AuditFilter filter;

    @BeforeEach
    void setUp() {
        store = new JdbcAuditStore(new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "")));
        store.initSchema();
        Clock clock = Clock.fixed(Instant.parse("2026-11-11T10:15:30.123Z"), ZoneOffset.UTC);
        filter = new AuditFilter(store, "INTX", clock);
    }

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        request.setAttribute(RequestTrackingFilter.REQUEST_ID_KEY, "INTX-abc");
        return request;
    }

    @Test
    void successfulRequestEndsAsSuccess() throws Exception {
        filter.doFilter(request("/api/v1/op"), new MockHttpServletResponse(), new MockFilterChain());

        AuditRecord record = store.findByRequestId("INTX-abc").orElseThrow();
        assertEquals(RequestStatus.SUCCESS, record.status());
        assertEquals("/api/v1/op", record.operation());
        assertNotNull(record.endTime());
    }

    @Test
    void errorStatusEndsAsFailedWithProvidedErrorDetails() throws Exception {
        MockHttpServletRequest request = request("/api/v1/op");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(504);
        request.setAttribute(AuditFilter.ERROR_CODE_ATTRIBUTE, "APPB_TIMEOUT");
        request.setAttribute(AuditFilter.ERROR_MESSAGE_ATTRIBUTE, "App timed out");

        filter.doFilter(request, response, new MockFilterChain());

        AuditRecord record = store.findByRequestId("INTX-abc").orElseThrow();
        assertEquals(RequestStatus.FAILED, record.status());
        assertEquals("APPB_TIMEOUT", record.errorCode());
    }

    @Test
    void uncaughtExceptionEndsAsInternalError() {
        assertThrows(IllegalStateException.class, () -> filter.doFilter(request("/api/v1/op"),
                new MockHttpServletResponse(), new MockFilterChain() {
                    @Override
                    public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                        throw new IllegalStateException("boom");
                    }
                }));

        AuditRecord record = store.findByRequestId("INTX-abc").orElseThrow();
        assertEquals("INTERNAL_ERROR", record.errorCode());
        assertEquals("boom", record.errorMessage());
    }

    @Test
    void actuatorCallsAreNotAudited() throws Exception {
        filter.doFilter(request("/actuator/health"), new MockHttpServletResponse(), new MockFilterChain());

        assertTrue(store.findByRequestId("INTX-abc").isEmpty());
    }
}
