package com.integration.framework.audit;

import java.time.Instant;
import java.util.Optional;

/** Persistence abstraction for request lifecycle information. */
public interface AuditStore {

    void recordStart(String requestId, String serviceName, String operation, Instant startTime);

    void recordEnd(String requestId, RequestStatus status, Instant endTime, String errorCode, String errorMessage);

    Optional<AuditRecord> findByRequestId(String requestId);

    /** Marks records still STARTED (from a previous run) as FAILED / INTERNAL_ERROR; returns how many. */
    int failInterrupted(Instant endTime);
}
