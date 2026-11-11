package com.integration.app;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

/** Test helper: HTTP GET with optional Basic credentials (test accounts from src/test/resources). */
final class TestHttp {

    static final String INBOUND = basic("inbound", "inbound-pw");
    static final String OPERATIONS = basic("ops", "ops-pw");

    private TestHttp() {
    }

    static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes());
    }

    static HttpResponse<String> get(int port, String path, String authorization) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
