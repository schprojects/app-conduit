package com.integration.app;

import org.springframework.boot.test.context.SpringBootTest;

/** The operational fallback: virtual threads off, a bounded pool sized for the load. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.threads.virtual.enabled=false",
        "spring.task.execution.pool.core-size=25",
        "spring.task.execution.pool.max-size=25",
        "spring.task.execution.pool.queue-capacity=100"})
class ParallelRequestsBoundedPoolTest extends ParallelRequestsBase {
}
