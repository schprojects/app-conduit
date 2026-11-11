package com.integration.framework.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcAuditStoreTest {

    private static final Instant START = Instant.parse("2026-11-11T10:15:30.123Z");
    private static final Instant END = Instant.parse("2026-11-11T10:15:31.456Z");

    private JdbcAuditStore store;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        store = new JdbcAuditStore(new JdbcTemplate(ds));
        store.initSchema();
    }

    @Test
    void recordStartCreatesInFlightRecord() {
        store.recordStart("INTX-1", "INTX", "getSomeData", START);

        AuditRecord record = store.findByRequestId("INTX-1").orElseThrow();
        assertEquals(RequestStatus.STARTED, record.status());
        assertEquals("INTX", record.serviceName());
        assertEquals("getSomeData", record.operation());
        assertEquals(START, record.startTime());
        assertNull(record.endTime());
        assertNull(record.errorCode());
        assertNull(record.errorMessage());
    }

    @Test
    void recordEndCompletesRecordWithError() {
        store.recordStart("INTX-2", "INTX", "op", START);
        store.recordEnd("INTX-2", RequestStatus.FAILED, END, "APPB_TIMEOUT", "App timed out");

        AuditRecord record = store.findByRequestId("INTX-2").orElseThrow();
        assertEquals(RequestStatus.FAILED, record.status());
        assertEquals(END, record.endTime());
        assertEquals("APPB_TIMEOUT", record.errorCode());
        assertEquals("App timed out", record.errorMessage());
    }

    @Test
    void failInterruptedOnlyTouchesStartedRecords() {
        store.recordStart("INTX-3", "INTX", "op", START);
        store.recordStart("INTX-4", "INTX", "op", START);
        store.recordEnd("INTX-4", RequestStatus.SUCCESS, END, null, null);

        assertEquals(1, store.failInterrupted(END));

        AuditRecord interrupted = store.findByRequestId("INTX-3").orElseThrow();
        assertEquals(RequestStatus.FAILED, interrupted.status());
        assertEquals("INTERNAL_ERROR", interrupted.errorCode());
        assertEquals("interrupted by restart", interrupted.errorMessage());
        assertEquals(RequestStatus.SUCCESS, store.findByRequestId("INTX-4").orElseThrow().status());
    }

    @Test
    void unknownRequestIdIsEmpty() {
        assertTrue(store.findByRequestId("nope").isEmpty());
    }
}
