package com.integration.app;

import com.integration.framework.error.IntegrationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoints that fail in controlled ways. */
@RestController
class FailingTestController {

    @GetMapping("/api/v1/test/timeout")
    String timeout() {
        throw IntegrationException.timeout("APPB", null);
    }

    @GetMapping("/api/v1/test/boom")
    String boom() {
        throw new IllegalStateException("secret detail");
    }
}
