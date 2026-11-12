package com.integration.framework.route;

import com.integration.framework.error.ErrorResponseProcessor;
import org.apache.camel.builder.RouteConfigurationBuilder;

/**
 * Nameless route configuration: every route that names no configuration of its own inherits it.
 * Routes therefore need no error handling of their own.
 */
public class IntegrationErrors extends RouteConfigurationBuilder {

    private final RequestContextInterceptor context;
    private final ErrorResponseProcessor errorResponse;

    public IntegrationErrors(RequestContextInterceptor context, ErrorResponseProcessor errorResponse) {
        this.context = context;
        this.errorResponse = errorResponse;
    }

    @Override
    public void configuration() {
        routeConfiguration().interceptFrom().bean(context, "captureRequest");
        routeConfiguration().interceptSendToEndpoint("direct:*").bean(context, "markOperation");
        routeConfiguration().onException(Exception.class).handled(true).process(errorResponse);
    }
}
