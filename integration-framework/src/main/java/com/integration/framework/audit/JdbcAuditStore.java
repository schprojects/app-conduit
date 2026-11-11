package com.integration.framework.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/** AuditStore on plain JDBC (H2 initially); no JPA/Hibernate. */
public class JdbcAuditStore implements AuditStore {

    static final String INTERRUPTED_CODE = "INTERNAL_ERROR";
    static final String INTERRUPTED_MESSAGE = "interrupted by restart";

    private final JdbcTemplate jdbc;

    public JdbcAuditStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void initSchema() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS audit_record (
                  request_id VARCHAR(64) PRIMARY KEY,
                  service_name VARCHAR(64) NOT NULL,
                  operation VARCHAR(255),
                  status VARCHAR(16) NOT NULL,
                  start_time TIMESTAMP(3) NOT NULL,
                  end_time TIMESTAMP(3),
                  error_code VARCHAR(64),
                  error_message VARCHAR(1000)
                )""");
    }

    @Override
    public void recordStart(String requestId, String serviceName, String operation, Instant startTime) {
        jdbc.update("INSERT INTO audit_record (request_id, service_name, operation, status, start_time) VALUES (?,?,?,?,?)",
                requestId, serviceName, operation, RequestStatus.STARTED.name(), timestamp(startTime));
    }

    @Override
    public void recordEnd(String requestId, RequestStatus status, Instant endTime, String errorCode, String errorMessage) {
        jdbc.update("UPDATE audit_record SET status=?, end_time=?, error_code=?, error_message=? WHERE request_id=?",
                status.name(), timestamp(endTime), errorCode, truncate(errorMessage), requestId);
    }

    @Override
    public Optional<AuditRecord> findByRequestId(String requestId) {
        return jdbc.query("SELECT * FROM audit_record WHERE request_id=?", MAPPER, requestId).stream().findFirst();
    }

    @Override
    public int failInterrupted(Instant endTime) {
        return jdbc.update("UPDATE audit_record SET status=?, end_time=?, error_code=?, error_message=? WHERE status=?",
                RequestStatus.FAILED.name(), timestamp(endTime), INTERRUPTED_CODE, INTERRUPTED_MESSAGE,
                RequestStatus.STARTED.name());
    }

    private static final RowMapper<AuditRecord> MAPPER = (rs, i) -> new AuditRecord(
            rs.getString("request_id"),
            rs.getString("service_name"),
            rs.getString("operation"),
            RequestStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("start_time").toInstant(),
            rs.getTimestamp("end_time") == null ? null : rs.getTimestamp("end_time").toInstant(),
            rs.getString("error_code"),
            rs.getString("error_message"));

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant.truncatedTo(ChronoUnit.MILLIS));
    }

    private static String truncate(String message) {
        return message == null || message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
