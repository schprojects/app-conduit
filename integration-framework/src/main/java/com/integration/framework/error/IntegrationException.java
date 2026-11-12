package com.integration.framework.error;

import org.springframework.http.HttpStatus;

/**
 * A failure with a standard errorCode and the HTTP status that maps to it.
 * Java beans inside a connector throw this to carry their own code; the framework builds the standard error response.
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
}
