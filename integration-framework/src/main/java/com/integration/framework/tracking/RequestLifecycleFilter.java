package com.integration.framework.tracking;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * First filter of every request. Creates the request id (a caller supplied id is ignored), puts it in the log context,
 * returns it in the X-Request-Id header and writes the audit lines {@code request.started} and {@code request.completed}
 * on the logger {@code audit} (none for actuator calls). Failures record their errorCode in the log context.
 */
public class RequestLifecycleFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_KEY = "requestId";
    public static final String OPERATION_KEY = "operation";
    public static final String ERROR_CODE_KEY = "errorCode";
    public static final String UNKNOWN_OPERATION = "UNKNOWN";

    private static final Logger audit = LoggerFactory.getLogger("audit");

    private final RequestIdGenerator generator;

    public RequestLifecycleFilter(RequestIdGenerator generator) {
        this.generator = generator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = generator.next();
        request.setAttribute(REQUEST_ID_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put(REQUEST_ID_KEY, requestId);
        MDC.put(OPERATION_KEY, UNKNOWN_OPERATION);
        boolean audited = !request.getRequestURI().startsWith("/actuator");
        long start = System.nanoTime();
        if (audited) {
            logStarted();
        }
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException e) {
            MDC.put(ERROR_CODE_KEY, "INTERNAL_ERROR");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            throw e;
        } finally {
            if (audited) {
                logCompleted(response.getStatus(), (System.nanoTime() - start) / 1_000_000);
            }
            MDC.clear();
        }
    }

    private void logStarted() {
        MDC.put("status", "STARTED");
        audit.info("request.started");
        MDC.remove("status");
    }

    private void logCompleted(int httpStatus, long durationMs) {
        String status = httpStatus < 400 ? "SUCCESS" : "FAILED";
        MDC.put("status", status);
        MDC.put("httpStatus", String.valueOf(httpStatus));
        MDC.put("durationMs", String.valueOf(durationMs));
        audit.info("request.completed");
    }
}
