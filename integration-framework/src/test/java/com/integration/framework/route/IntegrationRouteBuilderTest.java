package com.integration.framework.route;

import com.integration.framework.error.IntegrationException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IntegrationRouteBuilderTest {

    @Test
    void routeFailuresPropagateUnchangedToTheCaller() throws Exception {
        try (DefaultCamelContext camel = new DefaultCamelContext()) {
            camel.addRoutes(new IntegrationRouteBuilder() {
                @Override
                protected void routes() {
                    from("direct:fail").process(e -> {
                        throw IntegrationException.timeout("APPB", null);
                    });
                }
            });
            camel.start();

            CamelExecutionException e = assertThrows(CamelExecutionException.class,
                    () -> camel.createProducerTemplate().requestBody("direct:fail", "x"));

            assertEquals("APPB_TIMEOUT", ((IntegrationException) e.getCause()).errorCode());
        }
    }
}
