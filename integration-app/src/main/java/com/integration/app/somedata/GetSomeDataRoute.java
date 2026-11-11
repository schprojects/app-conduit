package com.integration.app.somedata;

import com.integration.framework.route.IntegrationRouteBuilder;
import org.springframework.stereotype.Component;

/** Sample route: calls the Business Service only, never APP-B directly. */
@Component
public class GetSomeDataRoute extends IntegrationRouteBuilder {

    public static final String ENDPOINT = "direct:getSomeData";

    @Override
    protected void routes() {
        from(ENDPOINT).routeId("getSomeData").bean(SomeDataService.class, "getSomeData");
    }
}
