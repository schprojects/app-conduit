package com.integration.framework.error;

import org.apache.camel.http.base.HttpOperationFailedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorMapperTest {

    private static HttpOperationFailedException httpFailure(int status) {
        return new HttpOperationFailedException("http://x", status, "status", null, null, "secret target body");
    }

    private static void assertFailure(ErrorMapper.Failure failure, HttpStatus status, String errorCode) {
        assertEquals(status, failure.status());
        assertEquals(errorCode, failure.errorCode());
    }

    @Test
    void targetAnswering404IsNotFound() {
        assertFailure(ErrorMapper.map(httpFailure(404), "APPB"), HttpStatus.NOT_FOUND, "APPB_NOT_FOUND");
    }

    @Test
    void targetAnsweringAnyOtherErrorIsUnavailable() {
        assertFailure(ErrorMapper.map(httpFailure(500), "APPB"), HttpStatus.BAD_GATEWAY, "APPB_UNAVAILABLE");
        assertFailure(ErrorMapper.map(httpFailure(401), "APPX"), HttpStatus.BAD_GATEWAY, "APPX_UNAVAILABLE");
    }

    @Test
    void timeoutIs504BeforeBeingTreatedAsAnIoError() {
        assertFailure(ErrorMapper.map(new SocketTimeoutException("slow"), "APPB"),
                HttpStatus.GATEWAY_TIMEOUT, "APPB_TIMEOUT");
    }

    @Test
    void unreachableTargetIsUnavailable() {
        assertFailure(ErrorMapper.map(new ConnectException("refused"), "APPB"),
                HttpStatus.BAD_GATEWAY, "APPB_UNAVAILABLE");
        assertFailure(ErrorMapper.map(new IOException("reset"), "APPB"), HttpStatus.BAD_GATEWAY, "APPB_UNAVAILABLE");
    }

    @Test
    void wrappedCausesAreUnwrapped() {
        assertFailure(ErrorMapper.map(new RuntimeException("wrapped", new SocketTimeoutException()), "APPB"),
                HttpStatus.GATEWAY_TIMEOUT, "APPB_TIMEOUT");
    }

    @Test
    void integrationExceptionCarriesItsOwnCodeAndStatus() {
        IntegrationException e = new IntegrationException("APPX_TIMEOUT", HttpStatus.GATEWAY_TIMEOUT, "slow");
        assertFailure(ErrorMapper.map(new RuntimeException(e), null), HttpStatus.GATEWAY_TIMEOUT, "APPX_TIMEOUT");
    }

    @Test
    void targetFailureWithoutTargetAppIsInternalError() {
        assertFailure(ErrorMapper.map(httpFailure(500), null), HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
        assertFailure(ErrorMapper.map(new IllegalStateException("x"), "APPB"),
                HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
        assertFailure(ErrorMapper.map(null, null), HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
    }

    @Test
    void messagesAreGenericAndNeverCarryTheTargetBody() {
        assertEquals("Resource not found", ErrorMapper.map(httpFailure(404), "APPB").message());
        assertEquals("APPB unavailable", ErrorMapper.map(httpFailure(500), "APPB").message());
        assertEquals("APPB timed out", ErrorMapper.map(new SocketTimeoutException(), "APPB").message());
    }

    @Test
    void springWebFailuresMapToContractErrors() throws Exception {
        assertFailure(ErrorMapper.map(new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "/x", "x"), null),
                HttpStatus.NOT_FOUND, "UNKNOWN_OPERATION");
        assertFailure(ErrorMapper.map(new HttpRequestMethodNotSupportedException("POST"), null),
                HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    }
}
