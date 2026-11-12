package com.integration.app.characterization;

import com.integration.app.TestHttp;
import com.integration.app.appb.FakeAppB;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Observable behaviour of getSomeData (docs: specs/requirements.md, compatibility rule). Uses only HTTP and the
 * fake APP-B, so it must stay green through the refactoring. The only line that changes is the APP-B URL property.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "appb.timeout=2s")
class GetSomeDataCharacterizationTest {

    private static final String REQUEST_ID_FORMAT = "INTX-[A-Za-z0-9]{12}";
    private static final FakeAppB APP_B = new FakeAppB();

    @DynamicPropertySource
    static void appB(DynamicPropertyRegistry registry) {
        registry.add("appb.base-url", APP_B::baseUrl);
    }

    @AfterAll
    static void stop() {
        APP_B.close();
    }

    @Value("${local.server.port}")
    private int port;

    @BeforeEach
    void healthyAppB() {
        APP_B.respondOk();
    }

    private HttpResponse<String> get(String path, String authorization, String callerRequestId) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        if (callerRequestId != null) {
            request.header("X-Request-Id", callerRequestId);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void successReturnsAppBDataAndRequestId() throws Exception {
        HttpResponse<String> response = get("/api/v1/getSomeData", TestHttp.INBOUND, null);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"data\":\"hello from APP-B\""));
        assertTrue(response.headers().firstValue("X-Request-Id").orElseThrow().matches(REQUEST_ID_FORMAT));
    }

    @Test
    void outboundCallCarriesTheSameRequestIdAndOwnCredentials() throws Exception {
        HttpResponse<String> response = get("/api/v1/getSomeData", TestHttp.INBOUND, null);

        assertEquals(response.headers().firstValue("X-Request-Id").orElseThrow(), APP_B.lastRequestId());
        assertNotEquals(TestHttp.INBOUND, APP_B.lastAuthorization());
        assertEquals(TestHttp.basic("appb-user", "appb-pw"), APP_B.lastAuthorization());
    }

    @Test
    void callerSuppliedRequestIdIsIgnoredAndNotForwarded() throws Exception {
        HttpResponse<String> response = get("/api/v1/getSomeData", TestHttp.INBOUND, "EVIL-123456789012");

        String requestId = response.headers().firstValue("X-Request-Id").orElseThrow();
        assertNotEquals("EVIL-123456789012", requestId);
        assertEquals(requestId, APP_B.lastRequestId());
    }

    @Test
    void missingCredentialsReturn401WithStandardBody() throws Exception {
        HttpResponse<String> response = get("/api/v1/getSomeData", null, null);

        assertEquals(401, response.statusCode());
        assertTrue(response.body().contains("\"errorCode\":\"AUTH_FAILED\""));
        assertTrue(response.body().contains("\"status\":\"FAILED\""));
        assertTrue(response.headers().firstValue("X-Request-Id").orElseThrow().matches(REQUEST_ID_FORMAT));
    }

    @Test
    void appBTimeoutReturns504() throws Exception {
        APP_B.respondSlowly(4000);

        HttpResponse<String> response = get("/api/v1/getSomeData", TestHttp.INBOUND, null);

        assertEquals(504, response.statusCode());
        assertTrue(response.body().contains("\"errorCode\":\"APPB_TIMEOUT\""));
        assertTrue(response.body().contains("\"status\":\"FAILED\""));
        assertTrue(response.headers().firstValue("X-Request-Id").isPresent());
    }

    @Test
    void appBErrorReturns502() throws Exception {
        APP_B.respondWithStatus(500);

        HttpResponse<String> response = get("/api/v1/getSomeData", TestHttp.INBOUND, null);

        assertEquals(502, response.statusCode());
        assertTrue(response.body().contains("\"errorCode\":\"APPB_UNAVAILABLE\""));
        assertFalse(response.body().contains("hello"));
    }

    @Test
    void errorBodyCarriesTheSameRequestIdAsTheHeader() throws Exception {
        APP_B.respondWithStatus(500);

        HttpResponse<String> response = get("/api/v1/getSomeData", TestHttp.INBOUND, null);

        String requestId = response.headers().firstValue("X-Request-Id").orElseThrow();
        assertTrue(response.body().contains("\"requestId\":\"" + requestId + "\""));
    }
}
