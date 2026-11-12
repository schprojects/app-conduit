package com.integration.framework.route;

import org.apache.camel.Exchange;
import org.slf4j.MDC;

import static com.integration.framework.tracking.RequestLifecycleFilter.OPERATION_KEY;
import static com.integration.framework.tracking.RequestLifecycleFilter.REQUEST_ID_HEADER;
import static com.integration.framework.tracking.RequestLifecycleFilter.REQUEST_ID_KEY;
import static com.integration.framework.tracking.RequestLifecycleFilter.UNKNOWN_OPERATION;

/** Request context for routes: makes the request id available to routes and records the operation name. */
public class RequestContextInterceptor {

    /** Entry of a request: the id becomes an exchange property; the caller's own credentials and id never travel on. */
    public void captureRequest(Exchange exchange) {
        exchange.setProperty(REQUEST_ID_KEY, MDC.get(REQUEST_ID_KEY));
        exchange.getMessage().removeHeader("Authorization");
        exchange.getMessage().removeHeader(REQUEST_ID_HEADER);
    }

    /** The first call into {@code direct:<operationId>} (a name without a dot) names the operation for the audit lines. */
    public void markOperation(Exchange exchange) {
        String endpoint = exchange.getProperty(Exchange.INTERCEPTED_ENDPOINT, String.class);
        if (endpoint != null && endpoint.startsWith("direct://") && UNKNOWN_OPERATION.equals(MDC.get(OPERATION_KEY))) {
            String name = endpoint.substring("direct://".length());
            if (!name.contains(".")) {
                MDC.put(OPERATION_KEY, name);
            }
        }
    }
}
