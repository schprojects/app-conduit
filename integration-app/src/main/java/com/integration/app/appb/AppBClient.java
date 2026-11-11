package com.integration.app.appb;

/**
 * The client abstraction layer for APP-B: the only way the integration application talks to APP-B.
 * Failures are reported as IntegrationException (APPB_TIMEOUT, APPB_UNAVAILABLE).
 */
public interface AppBClient {

    SomeData getSomeData();
}
