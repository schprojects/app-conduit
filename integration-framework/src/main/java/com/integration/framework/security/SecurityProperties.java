package com.integration.framework.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** inbound: callers of the integration application; operations: actuator (info, metrics). */
@ConfigurationProperties(prefix = "integration.security")
public record SecurityProperties(Account inbound, Account operations) {

    public SecurityProperties {
        if (inbound == null || operations == null) {
            throw new IllegalArgumentException(
                    "integration.security.inbound and integration.security.operations accounts are required "
                            + "(see README: configuration)");
        }
    }
}
