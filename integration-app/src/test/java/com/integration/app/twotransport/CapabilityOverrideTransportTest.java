package com.integration.app.twotransport;

import org.springframework.boot.test.context.SpringBootTest;

/** The system default is rest, one capability overrides it to sdk: same answers, no route change. */
@SpringBootTest(properties = {"integration.connectors.appx.transport=rest", "integration.connectors.appx.getBom.transport=sdk"})
class CapabilityOverrideTransportTest extends TwoTransportBase {

    @Override
    boolean sdkConfigured() {
        return true;
    }
}
