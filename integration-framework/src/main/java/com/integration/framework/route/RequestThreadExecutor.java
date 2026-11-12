package com.integration.framework.route;

import org.springframework.core.task.SimpleAsyncTaskExecutor;

/**
 * Runs Camel platform-http requests on the servlet thread instead of a pool thread. Routes then execute synchronously on
 * the request thread (FR-12), so the thread-bound log context (request id, operation) stays valid and the lifecycle
 * filter sees the final response status.
 */
public class RequestThreadExecutor extends SimpleAsyncTaskExecutor {

    @Override
    public void execute(Runnable task) {
        task.run();
    }
}
