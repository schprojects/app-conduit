package com.integration.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoggingIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Test
    void appSpecificLinesGoToTheirOwnFileAndFrameworkLinesToTheFrameworkFile() throws Exception {
        HttpResponse<String> response = TestHttp.get(port, "/api/v1/test/appb-log", TestHttp.INBOUND);
        String requestId = response.headers().firstValue("X-Request-Id").orElseThrow();

        // the "request completed" line is written after the response has been sent
        String framework = awaitFrameworkLog("\"requestId\":\"" + requestId + "\",\"service\":\"INTX\",\"status\":\"SUCCESS\"");
        String appb = Files.readString(Path.of("target/test-logs/appb.log"));

        // app file: the app line, tagged with the request id, structured fields present
        String appLine = appb.lines().filter(l -> l.contains("calling appb") && l.contains(requestId)).findFirst().orElseThrow();
        assertTrue(appLine.contains("\"requestId\":\"" + requestId + "\""));
        assertTrue(appLine.contains("\"service\":\"INTX\""));
        assertTrue(appLine.contains("\"operation\":\"appb-log\""));
        assertTrue(appLine.contains("\"status\":\"STARTED\""));
        // framework file: request lifecycle lines for the same request, but not the app line
        assertTrue(framework.lines().anyMatch(l -> l.contains("request completed") && l.contains(requestId)
                && l.contains("\"status\":\"SUCCESS\"")));
        assertFalse(framework.lines().anyMatch(l -> l.contains("calling appb") && l.contains(requestId)));
    }

    private static String awaitFrameworkLog(String expected) throws Exception {
        String content = "";
        for (int i = 0; i < 50; i++) {
            content = Files.readString(Path.of("target/test-logs/framework.log"));
            if (content.contains(expected)) {
                break;
            }
            Thread.sleep(100);
        }
        return content;
    }
}
