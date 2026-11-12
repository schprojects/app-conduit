package com.appconduit.sample;

import com.appconduit.sample.appb.FakeAppB;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NFR-13: many slow requests at once complete in about one slow time, requests do not mix up their ids, and every request has
 * exactly one started and one completed line with its own operation.
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ParallelRequestsTest {

    private static final int REQUESTS = 50;
    private static final int DELAY_MS = 1000;
    private static FakeAppB APP_B;

    @BeforeAll
    static void start() {
        APP_B = new FakeAppB();
    }

    @DynamicPropertySource
    static void appB(DynamicPropertyRegistry registry) {
        registry.add("integration.connectors.appb.base-url", () -> APP_B.baseUrl());
        registry.add("integration.connectors.appb.timeout-ms", () -> "5000");
    }

    @AfterAll
    static void stop() {
        APP_B.close();
    }

    @Value("${local.server.port}")
    private int port;

    @Test
    void manySlowRequestsRunConcurrentlyWithTheirOwnIds(CapturedOutput output) throws Exception {
        APP_B.respondSlowly(DELAY_MS);
        ExecutorService clients = Executors.newFixedThreadPool(REQUESTS);
        long start = System.nanoTime();
        List<CompletableFuture<HttpResponse<String>>> calls = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            calls.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return TestHttp.get(port, "/api/v1/getSomeData", TestHttp.INBOUND);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }, clients));
        }
        Set<String> ids = new HashSet<>();
        for (CompletableFuture<HttpResponse<String>> call : calls) {
            HttpResponse<String> response = call.get();
            assertEquals(200, response.statusCode());
            ids.add(response.headers().firstValue("X-Request-Id").orElseThrow());
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        clients.shutdown();

        assertEquals(REQUESTS, ids.size());
        assertTrue(elapsedMs < 4 * DELAY_MS, REQUESTS + " parallel 1 s requests took " + elapsedMs + " ms");
        for (String id : ids) {
            List<Map<String, Object>> lines = AuditLines.await(output, "audit", id, 2);
            assertEquals(2, lines.size(), id);
            assertEquals("getSomeData", lines.get(1).get("operation"));
            assertEquals("200", lines.get(1).get("httpStatus"));
            assertEquals(id, lines.get(1).get("requestId"));
        }
    }
}
