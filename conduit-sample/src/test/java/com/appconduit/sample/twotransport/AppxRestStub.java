package com.appconduit.sample.twotransport;

import org.apache.camel.http.base.HttpOperationFailedException;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/** Test stand-in for the REST transport: returns the same JSON, fails the way Camel's http component fails. */
@Component("appxRestStub")
public class AppxRestStub {

    private volatile AppxSdkBomService.Fault fault = AppxSdkBomService.Fault.NONE;
    private final AtomicInteger calls = new AtomicInteger();

    public void fail(AppxSdkBomService.Fault fault) {
        this.fault = fault;
    }

    public int calls() {
        return calls.get();
    }

    public void reset() {
        calls.set(0);
    }

    public String getBom() throws Exception {
        calls.incrementAndGet();
        return switch (fault) {
            case TIMEOUT -> throw new SocketTimeoutException("Read timed out");
            case UNAVAILABLE -> throw new HttpOperationFailedException("http://appx/bom", 500, "Server Error", null, null, "secret body");
            case NONE -> TwoTransportBase.BOM;
        };
    }
}
