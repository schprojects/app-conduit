package com.appconduit.sample.twotransport;

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-20 and AC-21: the capability endpoint direct:appx.getBom answers identically over either transport, and failures of
 * either transport map to the same APPX_ codes. Subclasses only choose the transport configuration; the operation route
 * (direct:getPartBom) and these assertions never change.
 */
abstract class TwoTransportBase {

    /** Which transport the configuration of the subclass selects. */
    abstract boolean sdkConfigured();

    static final String BOM = "{\"partId\":\"P-1\",\"children\":[\"P-2\",\"P-3\"]}";

    @Autowired
    private ProducerTemplate template;
    @Autowired
    private AppxSdkBomService sdk;
    @Autowired
    private AppxRestStub rest;

    @BeforeEach
    void resetCounters() {
        sdk.reset();
        rest.reset();
    }

    @AfterEach
    void healthy() {
        sdk.fail(AppxSdkBomService.Fault.NONE);
        rest.fail(AppxSdkBomService.Fault.NONE);
    }

    private Exchange callOperation(AppxSdkBomService.Fault fault) {
        sdk.fail(fault);
        rest.fail(fault);
        return template.request("direct:getPartBom", exchange -> { });
    }

    @Test
    void returnsTheSameBomWhicheverTransportIsConfigured() {
        Exchange result = callOperation(AppxSdkBomService.Fault.NONE);

        assertEquals(BOM, result.getMessage().getBody(String.class));
        assertEquals("APPX", result.getProperty("targetApp"));
    }

    @Test
    void onlyTheConfiguredTransportIsUsed() {
        callOperation(AppxSdkBomService.Fault.NONE);

        assertEquals(sdkConfigured() ? 1 : 0, sdk.calls());
        assertEquals(sdkConfigured() ? 0 : 1, rest.calls());
    }

    @Test
    void aTimeoutMapsToAppxTimeout504() {
        Exchange result = callOperation(AppxSdkBomService.Fault.TIMEOUT);

        assertEquals(504, result.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertTrue(result.getMessage().getBody(String.class).contains("\"errorCode\":\"APPX_TIMEOUT\""));
        assertTrue(result.getMessage().getBody(String.class).contains("\"message\":\"APPX timed out\""));
    }

    @Test
    void anErrorMapsToAppxUnavailable502WithoutTheTargetBody() {
        Exchange result = callOperation(AppxSdkBomService.Fault.UNAVAILABLE);

        assertEquals(502, result.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertTrue(result.getMessage().getBody(String.class).contains("\"errorCode\":\"APPX_UNAVAILABLE\""));
        assertTrue(result.getMessage().getBody(String.class).contains("\"message\":\"APPX unavailable\""));
        assertTrue(!result.getMessage().getBody(String.class).contains("secret body"));
    }
}
