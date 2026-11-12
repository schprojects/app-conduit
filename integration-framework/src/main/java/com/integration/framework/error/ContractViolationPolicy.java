package com.integration.framework.error;

import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.support.RoutePolicySupport;

/** Route policy that gives a contract violation (plain-text 400 written by Camel) the standard error body. */
public class ContractViolationPolicy extends RoutePolicySupport {

    private final ErrorResponseProcessor errorResponse;

    public ContractViolationPolicy(ErrorResponseProcessor errorResponse) {
        this.errorResponse = errorResponse;
    }

    @Override
    public void onExchangeDone(Route route, Exchange exchange) {
        errorResponse.contractViolation(exchange);
    }
}
