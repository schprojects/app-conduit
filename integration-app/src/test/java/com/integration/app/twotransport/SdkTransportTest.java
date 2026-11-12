package com.integration.app.twotransport;

import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest(properties = "integration.connectors.appx.transport=sdk")
class SdkTransportTest extends TwoTransportBase {

    @Override
    boolean sdkConfigured() {
        return true;
    }
}
