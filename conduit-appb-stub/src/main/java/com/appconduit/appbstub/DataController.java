package com.appconduit.appbstub;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * APP-B stand-in: userid-password protected. Failures can be simulated for demos:
 * {@code ?delayMs=N} answers slowly (timeout), {@code ?fail=true} answers 500.
 */
@RestController
class DataController {

    private final String expectedAuthorization;

    DataController(@Value("${appb.username}") String username, @Value("${appb.password}") String password) {
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalArgumentException("APPB_USER and APPB_PASSWORD must be set");
        }
        this.expectedAuthorization = "Basic "
                + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/api/data")
    ResponseEntity<Map<String, String>> data(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestParam(defaultValue = "0") long delayMs,
            @RequestParam(defaultValue = "false") boolean fail) throws InterruptedException {
        if (!expectedAuthorization.equals(authorization)) {
            return ResponseEntity.status(401).build();
        }
        if (delayMs > 0) {
            Thread.sleep(delayMs);
        }
        if (fail) {
            return ResponseEntity.status(500).build();
        }
        return ResponseEntity.ok(Map.of("data", "hello from APP-B", "receivedRequestId", String.valueOf(requestId)));
    }
}
