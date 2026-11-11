package com.integration.framework.route;

import org.apache.camel.builder.RouteBuilder;

/**
 * Base class for integration routes. Routes do not handle errors themselves: failures propagate to the caller and
 * are turned into the standard error response (and the audit failure) by the framework's single error handler.
 * Routes call Business Services only, never the called applications directly.
 */
public abstract class IntegrationRouteBuilder extends RouteBuilder {

    @Override
    public final void configure() {
        errorHandler(noErrorHandler());
        routes();
    }

    protected abstract void routes();
}
