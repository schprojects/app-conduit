package com.integration.app.appb;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/** A tiny in-process APP-B for tests: can answer normally, slowly, or with an error. */
public class FakeAppB implements AutoCloseable {

    private final HttpServer server;
    private volatile long delayMs;
    private volatile int status = 200;
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    private final AtomicReference<String> lastRequestId = new AtomicReference<>();

    public FakeAppB() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/api/data", exchange -> {
            lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastRequestId.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] body = "{\"data\":\"hello from APP-B\",\"receivedRequestId\":\"x\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, status == 200 ? body.length : -1);
            if (status == 200) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public void respondOk() {
        delayMs = 0;
        status = 200;
    }

    public void respondSlowly(long delayMs) {
        this.delayMs = delayMs;
        this.status = 200;
    }

    public void respondWithStatus(int status) {
        this.delayMs = 0;
        this.status = status;
    }

    public String lastAuthorization() {
        return lastAuthorization.get();
    }

    public String lastRequestId() {
        return lastRequestId.get();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
