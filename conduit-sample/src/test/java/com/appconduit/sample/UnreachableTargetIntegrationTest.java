package com.appconduit.sample;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The test configuration points APP-B at a closed port (localhost:1). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UnreachableTargetIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Test
    void unreachableTargetReturnsUnavailable() throws Exception {
        HttpResponse<String> response = TestHttp.get(port, "/api/v1/getSomeData", TestHttp.INBOUND);

        assertEquals(502, response.statusCode());
        assertTrue(response.body().contains("\"errorCode\":\"APPB_UNAVAILABLE\""));
    }
}
