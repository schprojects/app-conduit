package com.appconduit.framework.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.appconduit.framework.tracking.AuditLog;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

import static com.appconduit.framework.tracking.RequestIdFilter.REQUEST_ID_KEY;

/**
 * Writes the standard error response for failures that happen outside a Camel route: the Spring Security entry point
 * (401) and Spring MVC failures (unmatched path, container errors). Classification is done by {@link ErrorMapper}.
 */
@RestControllerAdvice
public class ErrorResponseWriter implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger("integration.error");

    private final JsonMapper mapper;
    private final AuditLog audit;

    public ErrorResponseWriter(JsonMapper mapper, AuditLog audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        audit(request, ErrorMapper.AUTH_FAILED);
        response.setStatus(ErrorMapper.AUTH_FAILED.status().value());
        response.setContentType("application/json");
        response.getWriter().write(mapper.writeValueAsString(body(request, ErrorMapper.AUTH_FAILED)));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handle(Exception e, HttpServletRequest request) {
        ErrorMapper.Failure failure = ErrorMapper.map(e, null);
        if (failure == ErrorMapper.INTERNAL_ERROR) {
            log.error("Unexpected failure", e);
        }
        audit(request, failure);
        return ResponseEntity.status(failure.status()).body(body(request, failure));
    }

    /** A request that never reached Camel still gets both audit lines (none for actuator calls). */
    private void audit(HttpServletRequest request, ErrorMapper.Failure failure) {
        if (!request.getRequestURI().startsWith("/actuator")) {
            String requestId = (String) request.getAttribute(REQUEST_ID_KEY);
            audit.started(requestId, AuditLog.UNKNOWN_OPERATION);
            audit.completed(requestId, AuditLog.UNKNOWN_OPERATION, failure.status().value(), 0, failure.errorCode());
        }
    }

    private static ErrorResponse body(HttpServletRequest request, ErrorMapper.Failure failure) {
        String requestId = (String) request.getAttribute(REQUEST_ID_KEY);
        return ErrorResponse.failed(requestId, failure.errorCode(), failure.message());
    }
}
