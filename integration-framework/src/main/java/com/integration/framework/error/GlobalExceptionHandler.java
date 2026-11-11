package com.integration.framework.error;

import com.integration.framework.audit.AuditFilter;
import com.integration.framework.tracking.RequestTrackingFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** The single place where failures become the standard error response (and the audit failure details). */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IntegrationException.class)
    ResponseEntity<ErrorResponse> integrationFailure(IntegrationException e, HttpServletRequest request) {
        return respond(request, e.httpStatus(), e.errorCode(), e.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> unknownOperation(HttpServletRequest request) {
        return respond(request, HttpStatus.NOT_FOUND, "UNKNOWN_OPERATION", "Unknown operation");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, HttpRequestMethodNotSupportedException.class})
    ResponseEntity<ErrorResponse> invalidRequest(HttpServletRequest request) {
        return respond(request, HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> internalError(Exception e, HttpServletRequest request) {
        IntegrationException cause = findIntegrationException(e);
        if (cause != null) {
            return integrationFailure(cause, request);
        }
        log.error("Unexpected failure", e);
        return respond(request, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal error");
    }

    /** Camel wraps route failures; the original IntegrationException is somewhere in the cause chain. */
    private static IntegrationException findIntegrationException(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof IntegrationException ie) {
                return ie;
            }
        }
        return null;
    }

    private ResponseEntity<ErrorResponse> respond(HttpServletRequest request, HttpStatus status, String errorCode,
                                                  String message) {
        request.setAttribute(AuditFilter.ERROR_CODE_ATTRIBUTE, errorCode);
        request.setAttribute(AuditFilter.ERROR_MESSAGE_ATTRIBUTE, message);
        String requestId = (String) request.getAttribute(RequestTrackingFilter.REQUEST_ID_KEY);
        return ResponseEntity.status(status).body(ErrorResponse.failed(requestId, errorCode, message));
    }
}
