package com.appconduit.framework.route;

import com.appconduit.framework.tracking.AuditLog;
import com.appconduit.framework.tracking.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.camel.Exchange;

/**
 * Request context for routes. The values live on the exchange as properties (not in thread state); Camel's MDC service
 * shows requestId and operation on every log line written while the exchange is processed.
 */
public class RequestContextInterceptor {

    public static final String SERVLET_REQUEST_HEADER = "CamelHttpServletRequest";
    public static final String OPERATION_PROPERTY = "operation";
    public static final String ERROR_CODE_PROPERTY = "errorCode";
    public static final String STARTED_PROPERTY = "auditStarted";
    /** Marks an exchange that came in over HTTP even when its message headers were stripped. */
    public static final String HTTP_ENTRY_PROPERTY = "httpEntry";

    private final AuditLog audit;

    public RequestContextInterceptor(AuditLog audit) {
        this.audit = audit;
    }

    /**
     * Entry of a request: the id of the servlet request becomes the exchange property requestId, the caller's own
     * credentials and id never travel on, and the started line is written for requests that came in over HTTP.
     */
    public void captureRequest(Exchange exchange) {
        if (exchange.getProperty(STARTED_PROPERTY) != null) {
            return;
        }
        HttpServletRequest request = exchange.getMessage().getHeader(SERVLET_REQUEST_HEADER, HttpServletRequest.class);
        if (exchange.getProperty(RequestIdFilter.REQUEST_ID_KEY) == null && request != null) {
            exchange.setProperty(RequestIdFilter.REQUEST_ID_KEY, request.getAttribute(RequestIdFilter.REQUEST_ID_KEY));
        }
        exchange.setProperty(OPERATION_PROPERTY, exchange.getProperty(OPERATION_PROPERTY, AuditLog.UNKNOWN_OPERATION, String.class));
        exchange.getMessage().removeHeader("Authorization");
        exchange.getMessage().removeHeader(RequestIdFilter.REQUEST_ID_HEADER);
        if (request != null && !request.getRequestURI().startsWith("/actuator")) {
            audit.started(exchange.getProperty(RequestIdFilter.REQUEST_ID_KEY, String.class), AuditLog.UNKNOWN_OPERATION);
            exchange.setProperty(STARTED_PROPERTY, true);
            exchange.setProperty(HTTP_ENTRY_PROPERTY, true);
        }
    }

    /** The first call into {@code direct:<operationId>} (a name without a dot) names the operation. */
    public void markOperation(Exchange exchange) {
        String endpoint = exchange.getProperty(Exchange.INTERCEPTED_ENDPOINT, String.class);
        String known = exchange.getProperty(OPERATION_PROPERTY, AuditLog.UNKNOWN_OPERATION, String.class);
        if (endpoint != null && endpoint.startsWith("direct://") && AuditLog.UNKNOWN_OPERATION.equals(known)) {
            String name = endpoint.substring("direct://".length());
            if (!name.contains(".")) {
                exchange.setProperty(OPERATION_PROPERTY, name);
            }
        }
    }
}
