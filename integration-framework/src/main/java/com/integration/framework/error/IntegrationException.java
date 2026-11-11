package com.integration.framework.error;

import org.springframework.http.HttpStatus;

/**
 * A failure with a standard errorCode and the HTTP status that maps to it.
 * Client layers throw this so the framework can build the standard error response.
 */
public class IntegrationException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    public IntegrationException(String errorCode, HttpStatus httpStatus, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public IntegrationException(String errorCode, HttpStatus httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String errorCode() {
        return errorCode;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    /** Called application timed out: 504 / {@code <TARGETAPP>_TIMEOUT}. */
    public static IntegrationException timeout(String targetApp, Throwable cause) {
        return new IntegrationException(targetApp + "_TIMEOUT", HttpStatus.GATEWAY_TIMEOUT,
                targetApp + " timed out", cause);
    }

    /** Called application returned an error or is unavailable: 502 / {@code <TARGETAPP>_UNAVAILABLE}. */
    public static IntegrationException unavailable(String targetApp, Throwable cause) {
        return new IntegrationException(targetApp + "_UNAVAILABLE", HttpStatus.BAD_GATEWAY,
                targetApp + " is unavailable", cause);
    }

    public static IntegrationException invalidRequest(String message) {
        return new IntegrationException("INVALID_REQUEST", HttpStatus.BAD_REQUEST, message);
    }

    public static IntegrationException unknownOperation(String message) {
        return new IntegrationException("UNKNOWN_OPERATION", HttpStatus.NOT_FOUND, message);
    }
}
