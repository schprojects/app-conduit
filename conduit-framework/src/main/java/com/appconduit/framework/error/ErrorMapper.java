package com.appconduit.framework.error;

import org.apache.camel.http.base.HttpOperationFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.net.SocketTimeoutException;

/** The one place where a failure is classified: exception (and the target system, if any) to HTTP status and errorCode. */
public final class ErrorMapper {

    public record Failure(HttpStatus status, String errorCode, String message) {
    }

    public static final Failure AUTH_FAILED =
            new Failure(HttpStatus.UNAUTHORIZED, "AUTH_FAILED", "Authentication failed");
    public static final Failure INTERNAL_ERROR =
            new Failure(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal error");
    private static final Failure UNKNOWN_OPERATION =
            new Failure(HttpStatus.NOT_FOUND, "UNKNOWN_OPERATION", "Unknown operation");
    public static final Failure INVALID_REQUEST =
            new Failure(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request");

    private ErrorMapper() {
    }

    /** targetApp is the uppercase system short name set by the connector (null when no target was involved). */
    public static Failure map(Throwable error, String targetApp) {
        for (Throwable t = error; t != null && t.getCause() != t; t = t.getCause()) {
            Failure failure = classify(t, targetApp);
            if (failure != null) {
                return failure;
            }
        }
        return INTERNAL_ERROR;
    }

    private static Failure classify(Throwable t, String targetApp) {
        if (t instanceof IntegrationException e) {
            return new Failure(e.httpStatus(), e.errorCode(), e.getMessage());
        }
        if (t instanceof NoResourceFoundException) {
            return UNKNOWN_OPERATION;
        }
        if (t instanceof HttpMessageNotReadableException || t instanceof MissingServletRequestParameterException
                || t instanceof MethodArgumentTypeMismatchException
                || t instanceof HttpRequestMethodNotSupportedException) {
            return INVALID_REQUEST;
        }
        if (targetApp == null) {
            return null;
        }
        if (t instanceof HttpOperationFailedException e) {
            return e.getStatusCode() == 404
                    ? new Failure(HttpStatus.NOT_FOUND, targetApp + "_NOT_FOUND", "Resource not found")
                    : unavailable(targetApp);
        }
        if (t instanceof SocketTimeoutException) {
            return new Failure(HttpStatus.GATEWAY_TIMEOUT, targetApp + "_TIMEOUT", targetApp + " timed out");
        }
        if (t instanceof IOException) {
            return unavailable(targetApp);
        }
        return null;
    }

    private static Failure unavailable(String targetApp) {
        return new Failure(HttpStatus.BAD_GATEWAY, targetApp + "_UNAVAILABLE", targetApp + " unavailable");
    }
}
