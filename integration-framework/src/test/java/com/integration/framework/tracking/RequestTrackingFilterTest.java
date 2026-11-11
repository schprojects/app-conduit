package com.integration.framework.tracking;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestTrackingFilterTest {

    private final RequestTrackingFilter filter = new RequestTrackingFilter(new RequestIdGenerator("INTX"));

    @Test
    void generatesIdSetsHeaderAndLogContextAndClearsItAfterwards() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> idInChain = new AtomicReference<>();

        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                idInChain.set(MDC.get(RequestTrackingFilter.REQUEST_ID_KEY));
            }
        });

        String header = response.getHeader(RequestTrackingFilter.REQUEST_ID_HEADER);
        assertTrue(header.matches("INTX-[A-Za-z0-9]{12}"));
        assertEquals(header, idInChain.get());
        assertNull(MDC.get(RequestTrackingFilter.REQUEST_ID_KEY));
    }

    @Test
    void ignoresCallerSuppliedId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestTrackingFilter.REQUEST_ID_HEADER, "CALLER-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertNotEquals("CALLER-123", response.getHeader(RequestTrackingFilter.REQUEST_ID_HEADER));
    }
}
