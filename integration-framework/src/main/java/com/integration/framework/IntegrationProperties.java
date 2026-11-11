package com.integration.framework;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.regex.Pattern;

/** Framework configuration. appShortName identifies the integration application in request ids and logs. */
@ConfigurationProperties(prefix = "integration")
public record IntegrationProperties(String appShortName) {

    private static final Pattern ALPHANUMERIC = Pattern.compile("[A-Za-z0-9]+");

    public IntegrationProperties {
        if (appShortName == null || !ALPHANUMERIC.matcher(appShortName).matches()) {
            throw new IllegalArgumentException(
                    "integration.app-short-name is required and must be alphanumeric (no hyphens)");
        }
    }
}
