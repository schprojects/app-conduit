package com.integration.framework.tracking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * The two audit lines of a request on the logger {@code audit}: {@code request.started} and {@code request.completed}.
 * requestId and operation go through the log context so they are written exactly like on every other line.
 */
public class AuditLog {

    public static final String OPERATION_KEY = "operation";
    public static final String UNKNOWN_OPERATION = "UNKNOWN";

    private static final Logger audit = LoggerFactory.getLogger("audit");

    public void started(String requestId, String operation) {
        write(requestId, operation, "request.started", "STARTED", null, 0, null);
    }

    public void completed(String requestId, String operation, int httpStatus, long durationMs, String errorCode) {
        write(requestId, operation, "request.completed", httpStatus < 400 ? "SUCCESS" : "FAILED", httpStatus, durationMs,
                errorCode);
    }

    private void write(String requestId, String operation, String message, String status, Integer httpStatus,
                       long durationMs, String errorCode) {
        String previousId = MDC.get(RequestIdFilter.REQUEST_ID_KEY);
        String previousOperation = MDC.get(OPERATION_KEY);
        MDC.put(RequestIdFilter.REQUEST_ID_KEY, requestId);
        MDC.put(OPERATION_KEY, operation);
        try {
            var line = audit.atInfo().setMessage(message).addKeyValue("status", status);
            if (httpStatus != null) {
                line = line.addKeyValue("httpStatus", String.valueOf(httpStatus))
                        .addKeyValue("durationMs", String.valueOf(durationMs));
            }
            if (errorCode != null) {
                line = line.addKeyValue("errorCode", errorCode);
            }
            line.log();
        } finally {
            restore(RequestIdFilter.REQUEST_ID_KEY, previousId);
            restore(OPERATION_KEY, previousOperation);
        }
    }

    private static void restore(String key, String previous) {
        if (previous == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, previous);
        }
    }
}
