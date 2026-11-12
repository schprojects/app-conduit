package com.appconduit.sample.twotransport;

import com.appconduit.framework.error.IntegrationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/** Test stand-in for a Java bean over a vendor SDK (SDK style): returns fixed JSON, fails with IntegrationException. */
@Component("appxSdkBomService")
public class AppxSdkBomService {

    private volatile Fault fault = Fault.NONE;
    private final AtomicInteger calls = new AtomicInteger();

    public enum Fault { NONE, TIMEOUT, UNAVAILABLE }

    public void fail(Fault fault) {
        this.fault = fault;
    }

    public int calls() {
        return calls.get();
    }

    public void reset() {
        calls.set(0);
    }

    public String getBom() {
        calls.incrementAndGet();
        return switch (fault) {
            case TIMEOUT -> throw new IntegrationException("APPX_TIMEOUT", HttpStatus.GATEWAY_TIMEOUT, "APPX timed out");
            case UNAVAILABLE -> throw new IntegrationException("APPX_UNAVAILABLE", HttpStatus.BAD_GATEWAY, "APPX unavailable");
            case NONE -> TwoTransportBase.BOM;
        };
    }
}
