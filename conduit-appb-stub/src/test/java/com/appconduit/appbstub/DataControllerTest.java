package com.appconduit.appbstub;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataControllerTest {

    private final DataController controller = new DataController("user", "pw");
    private final String auth = "Basic " + Base64.getEncoder().encodeToString("user:pw".getBytes());

    @Test
    void returnsDataAndEchoesRequestIdWhenAuthenticated() throws Exception {
        var response = controller.data(auth, "INTX-abc", 0, false);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("hello from APP-B", response.getBody().get("data"));
        assertEquals("INTX-abc", response.getBody().get("receivedRequestId"));
    }

    @Test
    void rejectsMissingOrWrongCredentials() throws Exception {
        assertEquals(401, controller.data(null, null, 0, false).getStatusCode().value());
        assertEquals(401, controller.data("Basic eDp5", null, 0, false).getStatusCode().value());
    }

    @Test
    void failSimulatesServerError() throws Exception {
        assertEquals(500, controller.data(auth, null, 0, true).getStatusCode().value());
    }

    @Test
    void refusesToStartWithoutCredentials() {
        assertThrows(IllegalArgumentException.class, () -> new DataController("", ""));
    }
}
