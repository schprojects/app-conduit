package com.integration.framework.security;

import com.integration.framework.audit.AuditFilter;
import com.integration.framework.tracking.RequestTrackingFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Authenticates inbound calls. /actuator/health is open (container probes); other /actuator/** needs the
 * operations account; everything else needs the inbound account. Failures return 401 AUTH_FAILED.
 */
public class SecurityFilter extends OncePerRequestFilter {

    private final Authenticator authenticator;

    public SecurityFilter(Authenticator authenticator) {
        this.authenticator = authenticator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (isHealth(path) || authenticator.authenticate(request.getHeader(HttpHeaders.AUTHORIZATION), levelFor(path))) {
            chain.doFilter(request, response);
            return;
        }
        reject(request, response);
    }

    private static boolean isHealth(String path) {
        return path.equals("/actuator/health") || path.startsWith("/actuator/health/");
    }

    private static AccessLevel levelFor(String path) {
        return path.startsWith("/actuator") ? AccessLevel.OPERATIONS : AccessLevel.INBOUND;
    }

    private static void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setAttribute(AuditFilter.ERROR_CODE_ATTRIBUTE, "AUTH_FAILED");
        request.setAttribute(AuditFilter.ERROR_MESSAGE_ATTRIBUTE, "Authentication failed");
        String requestId = (String) request.getAttribute(RequestTrackingFilter.REQUEST_ID_KEY);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"integration\"");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getOutputStream().write(("{\"requestId\":\"" + requestId
                + "\",\"status\":\"FAILED\",\"errorCode\":\"AUTH_FAILED\",\"message\":\"Authentication failed\"}")
                .getBytes(StandardCharsets.UTF_8));
    }
}
