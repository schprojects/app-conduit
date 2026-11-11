package com.integration.app.appb;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Connection settings for APP-B. The timeout is configurable per application (default 10 seconds). */
@ConfigurationProperties(prefix = "appb")
public record AppBProperties(String baseUrl, String username, String password, Duration timeout) {

    public AppBProperties {
        if (baseUrl == null || baseUrl.isBlank() || username == null || username.isBlank()
                || password == null || password.isBlank()) {
            throw new IllegalArgumentException("appb.base-url, appb.username and appb.password must be set");
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(10);
        }
    }
}
