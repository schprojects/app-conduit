package com.integration.framework.error;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import static com.integration.framework.route.RequestContextInterceptor.ERROR_CODE_PROPERTY;
import static com.integration.framework.route.RequestContextInterceptor.HTTP_ENTRY_PROPERTY;
import static com.integration.framework.route.RequestContextInterceptor.SERVLET_REQUEST_HEADER;
import static com.integration.framework.tracking.RequestIdFilter.REQUEST_ID_HEADER;
import static com.integration.framework.tracking.RequestIdFilter.REQUEST_ID_KEY;

/** The shared Camel error rule: turns any failure in a route into the standard error response. */
public class ErrorResponseProcessor implements Processor {

    public static final String TARGET_APP_PROPERTY = "targetApp";

    private static final Logger log = LoggerFactory.getLogger("integration.error");

    private final JsonMapper mapper;

    public ErrorResponseProcessor(JsonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void process(Exchange exchange) {
        Throwable error = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
        ErrorMapper.Failure failure = ErrorMapper.map(error, exchange.getProperty(TARGET_APP_PROPERTY, String.class));
        if (failure == ErrorMapper.INTERNAL_ERROR) {
            log.error("Unexpected failure", error);
        } else {
            log.warn("Request failed: {} ({})", failure.errorCode(), error == null ? "" : error.getMessage());
        }
        write(exchange, failure);
    }

    /**
     * Camel itself rejects a request that violates the contract with a plain-text 400, stops the route and raises no
     * exception, so the shared error rule never sees it. Called by {@link ContractViolationPolicy} before the response
     * is sent, this turns that answer into the standard INVALID_REQUEST body.
     */
    public void contractViolation(Exchange exchange) {
        Message message = exchange.getMessage();
        String contentType = message.getHeader(Exchange.CONTENT_TYPE, String.class);
        boolean json = contentType != null && contentType.contains("json");
        if (!json && Integer.valueOf(400).equals(message.getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class))) {
            exchange.setProperty(HTTP_ENTRY_PROPERTY, true);
            write(exchange, ErrorMapper.INVALID_REQUEST);
        }
    }

    private void write(Exchange exchange, ErrorMapper.Failure failure) {
        exchange.setProperty(ERROR_CODE_PROPERTY, failure.errorCode());
        String requestId = exchange.getProperty(REQUEST_ID_KEY, String.class);
        if (requestId == null) {
            HttpServletRequest request = exchange.getMessage().getHeader(SERVLET_REQUEST_HEADER, HttpServletRequest.class);
            requestId = request == null ? null : (String) request.getAttribute(REQUEST_ID_KEY);
            exchange.setProperty(REQUEST_ID_KEY, requestId);
        }

        Message message = exchange.getMessage();
        message.removeHeaders("*");
        message.setHeader(Exchange.HTTP_RESPONSE_CODE, failure.status().value());
        message.setHeader(Exchange.CONTENT_TYPE, "application/json");
        message.setHeader(REQUEST_ID_HEADER, requestId);
        message.setBody(mapper.writeValueAsString(ErrorResponse.failed(requestId, failure.errorCode(), failure.message())));
    }
}
