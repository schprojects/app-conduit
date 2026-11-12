package com.appconduit.framework.tracking;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.camel.Exchange;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;

import static com.appconduit.framework.route.RequestContextInterceptor.ERROR_CODE_PROPERTY;
import static com.appconduit.framework.route.RequestContextInterceptor.OPERATION_PROPERTY;
import static com.appconduit.framework.route.RequestContextInterceptor.SERVLET_REQUEST_HEADER;
import static com.appconduit.framework.route.RequestContextInterceptor.HTTP_ENTRY_PROPERTY;
import static com.appconduit.framework.route.RequestContextInterceptor.STARTED_PROPERTY;

/**
 * Writes {@code request.completed} when Camel finishes an exchange that came in over HTTP. Camel events carry the
 * exchange, so this does not depend on which thread ran the route. A request that Camel rejected before its routes ran
 * (contract violation) has no started line yet; it is written here.
 */
public class RequestAuditNotifier extends EventNotifierSupport {

    private final AuditLog audit;

    public RequestAuditNotifier(AuditLog audit) {
        this.audit = audit;
    }

    @Override
    public boolean isEnabled(CamelEvent event) {
        return event instanceof CamelEvent.ExchangeCompletedEvent || event instanceof CamelEvent.ExchangeFailedEvent;
    }

    @Override
    public void notify(CamelEvent event) {
        Exchange exchange = ((CamelEvent.ExchangeEvent) event).getExchange();
        // Connectors strip the message headers, so the exchange properties are the reliable source; the servlet request
        // header is only there for a request Camel rejected before its routes ran.
        boolean started = exchange.getProperty(STARTED_PROPERTY) != null;
        HttpServletRequest request = exchange.getMessage().getHeader(SERVLET_REQUEST_HEADER, HttpServletRequest.class);
        boolean httpEntry = started || exchange.getProperty(HTTP_ENTRY_PROPERTY) != null
                || (request != null && !request.getRequestURI().startsWith("/actuator"));
        if (!httpEntry) {
            return;
        }
        String requestId = exchange.getProperty(RequestIdFilter.REQUEST_ID_KEY, String.class);
        if (requestId == null && request != null) {
            requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_KEY);
        }
        String operation = exchange.getProperty(OPERATION_PROPERTY, AuditLog.UNKNOWN_OPERATION, String.class);
        if (!started) {
            audit.started(requestId, operation);
        }
        Integer code = exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
        int httpStatus = code != null ? code : exchange.getException() != null ? 500 : 200;
        String errorCode = exchange.getProperty(ERROR_CODE_PROPERTY, String.class);
        if (errorCode == null && exchange.getException() != null) {
            errorCode = "INTERNAL_ERROR";
        }
        audit.completed(requestId, operation, httpStatus, exchange.getClock().elapsed(), errorCode);
    }
}
