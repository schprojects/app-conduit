package com.integration.app;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoints (test sources are not production code): one that works, one that fails unexpectedly. */
@RestController
class FailingTestController {

    @GetMapping("/api/v1/test/ok")
    String ok() {
        return "ok";
    }

    @GetMapping("/api/v1/test/boom")
    String boom() {
        throw new IllegalStateException("secret detail");
    }
}
