package com.integration.framework.security;

import com.integration.framework.tracking.RequestTrackingFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityFilterTest {

    /** Accepts only the level it is told to accept. */
    private static Authenticator accepting(AccessLevel accepted) {
        return (header, level) -> level == accepted;
    }

    private MockHttpServletResponse run(Authenticator authenticator, String uri, MockFilterChain chain)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        request.setAttribute(RequestTrackingFilter.REQUEST_ID_KEY, "INTX-abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        new SecurityFilter(authenticator).doFilter(request, response, chain);
        return response;
    }

    @Test
    void healthIsOpen() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run((h, l) -> false, "/actuator/health", chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void apiNeedsInboundAccount() throws Exception {
        MockFilterChain ok = new MockFilterChain();
        run(accepting(AccessLevel.INBOUND), "/api/v1/op", ok);
        assertNotNull(ok.getRequest());

        MockFilterChain denied = new MockFilterChain();
        MockHttpServletResponse response = run(accepting(AccessLevel.OPERATIONS), "/api/v1/op", denied);
        assertNull(denied.getRequest());
        assertEquals(401, response.getStatus());
    }

    @Test
    void metricsAndInfoNeedOperationsAccount() throws Exception {
        for (String uri : new String[]{"/actuator/metrics", "/actuator/info"}) {
            MockFilterChain ok = new MockFilterChain();
            run(accepting(AccessLevel.OPERATIONS), uri, ok);
            assertNotNull(ok.getRequest());

            MockFilterChain denied = new MockFilterChain();
            assertEquals(401, run(accepting(AccessLevel.INBOUND), uri, denied).getStatus());
            assertNull(denied.getRequest());
        }
    }

    @Test
    void rejectionIsStandardErrorBody() throws Exception {
        MockHttpServletResponse response = run((h, l) -> false, "/api/v1/op", new MockFilterChain());

        assertEquals(401, response.getStatus());
        String body = response.getContentAsString();
        assertTrue(body.contains("\"requestId\":\"INTX-abc\""));
        assertTrue(body.contains("\"status\":\"FAILED\""));
        assertTrue(body.contains("\"errorCode\":\"AUTH_FAILED\""));
    }
}
