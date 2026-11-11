package com.integration.framework.error;

/** Standard error response body. status is always FAILED; the HTTP status code tells the kind of failure. */
public record ErrorResponse(String requestId, String status, String errorCode, String message) {

    public static ErrorResponse failed(String requestId, String errorCode, String message) {
        return new ErrorResponse(requestId, "FAILED", errorCode, message);
    }
}
