package com.appconduit.framework.error;

import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.support.RoutePolicySupport;

/**
 * Route policy run when a route finishes: gives a contract violation (plain-text 400 written by Camel) the standard error
 * body and keeps the request id from being written twice on the response.
 */
public class ContractViolationPolicy extends RoutePolicySupport {

    private final ErrorResponseProcessor errorResponse;

    public ContractViolationPolicy(ErrorResponseProcessor errorResponse) {
        this.errorResponse = errorResponse;
    }

    @Override
    public void onExchangeDone(Route route, Exchange exchange) {
        errorResponse.contractViolation(exchange);
        // The id filter already put X-Request-Id on the HTTP response; a copy in the Camel message would be written again.
        exchange.getMessage().removeHeader("X-Request-Id");
    }
}
