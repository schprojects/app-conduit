package com.integration.framework.error;

import com.integration.framework.audit.AuditFilter;
import com.integration.framework.tracking.RequestTrackingFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestTrackingFilter.REQUEST_ID_KEY, "INTX-abc");
        return request;
    }

    @Test
    void timeoutBecomes504WithStandardBody() {
        MockHttpServletRequest request = request();

        ResponseEntity<ErrorResponse> response =
                handler.integrationFailure(IntegrationException.timeout("APPB", null), request);

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, response.getStatusCode());
        assertEquals(new ErrorResponse("INTX-abc", "FAILED", "APPB_TIMEOUT", "APPB timed out"), response.getBody());
        assertEquals("APPB_TIMEOUT", request.getAttribute(AuditFilter.ERROR_CODE_ATTRIBUTE));
    }

    @Test
    void unavailableBecomes502() {
        ResponseEntity<ErrorResponse> response =
                handler.integrationFailure(IntegrationException.unavailable("APPB", null), request());

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("APPB_UNAVAILABLE", response.getBody().errorCode());
    }

    @Test
    void unknownResourceBecomes404UnknownOperation() {
        ResponseEntity<ErrorResponse> response = handler.unknownOperation(request());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("UNKNOWN_OPERATION", response.getBody().errorCode());
    }

    @Test
    void invalidRequestBecomes400() {
        ResponseEntity<ErrorResponse> response = handler.invalidRequest(request());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("INVALID_REQUEST", response.getBody().errorCode());
    }

    @Test
    void unexpectedFailureBecomes500WithoutLeakingDetails() {
        ResponseEntity<ErrorResponse> response = handler.internalError(new IllegalStateException("secret"), request());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().errorCode());
        assertEquals("Internal error", response.getBody().message());
    }

    @Test
    void wrappedIntegrationExceptionIsUnwrapped() {
        Exception wrapped = new RuntimeException("camel wrapper", IntegrationException.timeout("APPB", null));

        ResponseEntity<ErrorResponse> response = handler.internalError(wrapped, request());

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, response.getStatusCode());
        assertEquals("APPB_TIMEOUT", response.getBody().errorCode());
    }
}
