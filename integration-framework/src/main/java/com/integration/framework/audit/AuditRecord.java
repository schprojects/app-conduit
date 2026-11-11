package com.integration.framework.audit;

import java.time.Instant;

/** Request lifecycle information. endTime, errorCode and errorMessage are null while the request is in flight. */
public record AuditRecord(
        String requestId,
        String serviceName,
        String operation,
        RequestStatus status,
        Instant startTime,
        Instant endTime,
        String errorCode,
        String errorMessage) {
}
