package com.integration.app;

import org.springframework.boot.test.system.CapturedOutput;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Test helper: the JSON log lines of one logger found in captured console output. */
final class AuditLines {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private AuditLines() {
    }

    static List<Map<String, Object>> of(String output, String logger) {
        return Arrays.stream(output.split("\n"))
                .filter(line -> line.contains("{\"@timestamp\"") && line.contains("\"logger_name\":\"" + logger + "\""))
                .map(line -> MAPPER.readValue(line.substring(line.indexOf("{\"@timestamp\"")), new TypeReference<Map<String, Object>>() {
                }))
                .toList();
    }

    static List<Map<String, Object>> forRequest(String output, String logger, String requestId) {
        return of(output, logger).stream().filter(line -> requestId.equals(line.get("requestId"))).toList();
    }

    /** The completed line is written after the response is sent, so wait until the expected lines have appeared. */
    static List<Map<String, Object>> await(CapturedOutput output, String logger, String requestId, int expected)
            throws InterruptedException {
        for (int i = 0; i < 40; i++) {
            List<Map<String, Object>> lines = forRequest(output.getOut(), logger, requestId);
            if (lines.size() >= expected) {
                return lines;
            }
            Thread.sleep(50);
        }
        return forRequest(output.getOut(), logger, requestId);
    }
}
