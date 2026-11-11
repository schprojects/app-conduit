package com.integration.framework.logging;

import org.slf4j.MDC;

/**
 * Routes log lines to an application's own log file (logs/&lt;app&gt;.log).
 * Client layers and routes wrap their work: {@code try (AppLogContext ctx = AppLogContext.enter("appb")) { ... }}.
 * Lines logged outside any context go to logs/framework.log.
 */
public final class AppLogContext implements AutoCloseable {

    public static final String KEY = "app";

    private final String previous;

    private AppLogContext(String previous) {
        this.previous = previous;
    }

    public static AppLogContext enter(String app) {
        AppLogContext context = new AppLogContext(MDC.get(KEY));
        MDC.put(KEY, app.toLowerCase());
        return context;
    }

    @Override
    public void close() {
        if (previous == null) {
            MDC.remove(KEY);
        } else {
            MDC.put(KEY, previous);
        }
    }
}
