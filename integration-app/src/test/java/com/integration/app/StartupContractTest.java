package com.integration.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AC-14: the application refuses to start when a contract operation has no operation route. */
class StartupContractTest {

    @Test
    void startupFailsWhenAnOperationHasNoRoute() {
        Throwable failure = assertThrows(Exception.class, () -> new SpringApplicationBuilder(IntegrationApplication.class)
                .run("--server.port=0", "--camel.main.routes-include-pattern=classpath:camel/rest.yaml"));

        Throwable root = failure;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        assertTrue(root.getMessage().contains("unmapped operations"), root.getMessage());
    }
}
