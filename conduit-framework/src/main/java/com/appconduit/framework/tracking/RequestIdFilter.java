package com.appconduit.framework.tracking;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * First filter of every request: creates the request id (a caller supplied id is ignored), returns it in the
 * X-Request-Id header and keeps it as a request attribute for Camel and for the failure writer. It holds no thread state:
 * logging context and audit lines are Camel's business (see RequestContextInterceptor and RequestAuditNotifier).
 */
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    /** Name of the request attribute and of the exchange property that carry the request id. */
    public static final String REQUEST_ID_KEY = "requestId";

    private final RequestIdGenerator generator;

    public RequestIdFilter(RequestIdGenerator generator) {
        this.generator = generator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = generator.next();
        request.setAttribute(REQUEST_ID_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        chain.doFilter(request, response);
    }
}
