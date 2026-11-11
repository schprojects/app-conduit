package com.integration.app;

import com.integration.framework.error.IntegrationException;
import com.integration.framework.logging.AppLogContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoints that fail in controlled ways. */
@RestController
class FailingTestController {

    private static final Logger log = LoggerFactory.getLogger(FailingTestController.class);

    @GetMapping("/api/v1/test/appb-log")
    String appbLog() {
        try (AppLogContext ignored = AppLogContext.enter("appb")) {
            log.info("calling appb");
        }
        return "ok";
    }

    @GetMapping("/api/v1/test/timeout")
    String timeout() {
        throw IntegrationException.timeout("APPB", null);
    }

    @GetMapping("/api/v1/test/boom")
    String boom() {
        throw new IllegalStateException("secret detail");
    }
}
