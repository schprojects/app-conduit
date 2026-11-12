package com.integration.app.twotransport;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "integration.connectors.appx.transport=rest")
class RestTransportTest extends TwoTransportBase {

    @Override
    boolean sdkConfigured() {
        return false;
    }
}
