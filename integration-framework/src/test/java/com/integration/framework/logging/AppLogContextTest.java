package com.integration.framework.logging;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AppLogContextTest {

    @Test
    void setsAndClearsAppKey() {
        try (AppLogContext ignored = AppLogContext.enter("APPB")) {
            assertEquals("appb", MDC.get(AppLogContext.KEY));
        }
        assertNull(MDC.get(AppLogContext.KEY));
    }

    @Test
    void nestedContextRestoresPrevious() {
        try (AppLogContext outer = AppLogContext.enter("appb")) {
            try (AppLogContext inner = AppLogContext.enter("appc")) {
                assertEquals("appc", MDC.get(AppLogContext.KEY));
            }
            assertEquals("appb", MDC.get(AppLogContext.KEY));
        }
    }
}
