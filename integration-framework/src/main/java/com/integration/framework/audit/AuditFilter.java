package com.integration.framework.audit;

import com.integration.framework.tracking.RequestTrackingFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;

/**
 * Creates the audit record when the inbound call starts and completes it when it ends.
 * Actuator calls are not audited. Failure details can be provided through the request attributes
 * {@link #ERROR_CODE_ATTRIBUTE} and {@link #ERROR_MESSAGE_ATTRIBUTE}.
 */
public class AuditFilter extends OncePerRequestFilter {

    public static final String ERROR_CODE_ATTRIBUTE = "audit.errorCode";
    public static final String ERROR_MESSAGE_ATTRIBUTE = "audit.errorMessage";

    private static final Logger log = LoggerFactory.getLogger(AuditFilter.class);

    private final AuditStore store;
    private final String serviceName;
    private final Clock clock;

    public AuditFilter(AuditStore store, String serviceName, Clock clock) {
        this.store = store;
        this.serviceName = serviceName;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = (String) request.getAttribute(RequestTrackingFilter.REQUEST_ID_KEY);
        MDC.put("service", serviceName);
        MDC.put("operation", request.getRequestURI());
        MDC.put("status", RequestStatus.STARTED.name());
        log.info("request started");
        store.recordStart(requestId, serviceName, request.getRequestURI(), clock.instant());
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException e) {
            complete(requestId, RequestStatus.FAILED, "INTERNAL_ERROR", e.getMessage());
            throw e;
        }
        if (response.getStatus() < 400) {
            complete(requestId, RequestStatus.SUCCESS, null, null);
        } else {
            complete(requestId, RequestStatus.FAILED,
                    attribute(request, ERROR_CODE_ATTRIBUTE, "HTTP_" + response.getStatus()),
                    attribute(request, ERROR_MESSAGE_ATTRIBUTE, "HTTP status " + response.getStatus()));
        }
    }

    private void complete(String requestId, RequestStatus status, String errorCode, String errorMessage) {
        store.recordEnd(requestId, status, clock.instant(), errorCode, errorMessage);
        MDC.put("status", status.name());
        log.info("request completed");
        MDC.remove("status");
        MDC.remove("operation");
        MDC.remove("service");
    }

    private static String attribute(HttpServletRequest request, String name, String fallback) {
        Object value = request.getAttribute(name);
        return value == null ? fallback : value.toString();
    }
}
