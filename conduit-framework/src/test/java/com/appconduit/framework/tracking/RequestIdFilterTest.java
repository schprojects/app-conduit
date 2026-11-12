package com.appconduit.framework.tracking;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter(new RequestIdGenerator("INTX"));

    @Test
    void generatesTheIdIgnoresTheCallersAndKeepsItOnTheRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/getSomeData");
        request.addHeader("X-Request-Id", "EVIL-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String id = response.getHeader("X-Request-Id");
        assertTrue(id.matches("INTX-[A-Za-z0-9]{12}"));
        assertNotEquals("EVIL-123", id);
        assertEquals(id, request.getAttribute("requestId"));
    }
}
