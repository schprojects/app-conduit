package com.integration.app;

import org.springframework.boot.test.context.SpringBootTest;

/** The default: virtual threads. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ParallelRequestsVirtualThreadsTest extends ParallelRequestsBase {
}
