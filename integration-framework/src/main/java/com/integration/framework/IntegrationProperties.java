package com.integration.framework;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.regex.Pattern;

/**
 * Framework configuration. appShortName identifies the integration application in request ids and logs;
 * security holds the inbound account (API callers) and the operations account (actuator info and metrics).
 */
@ConfigurationProperties(prefix = "integration")
public record IntegrationProperties(String appShortName, Security security) {

    private static final Pattern ALPHANUMERIC = Pattern.compile("[A-Za-z0-9]+");

    public IntegrationProperties {
        if (appShortName == null || !ALPHANUMERIC.matcher(appShortName).matches()) {
            throw new IllegalArgumentException(
                    "integration.app-short-name is required and must be alphanumeric (no hyphens)");
        }
        if (security == null) {
            throw new IllegalArgumentException("integration.security.* credentials are required");
        }
    }

    public record Security(Account inbound, Account operations) {

        public Security {
            if (inbound == null || operations == null) {
                throw new IllegalArgumentException(
                        "integration.security.inbound.* and integration.security.operations.* are required");
            }
        }
    }

    public record Account(String user, String password) {

        public Account {
            if (user == null || user.isBlank() || password == null || password.isBlank()) {
                throw new IllegalArgumentException("security accounts need a user and a password");
            }
        }
    }
}
