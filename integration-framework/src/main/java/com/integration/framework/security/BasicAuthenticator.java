package com.integration.framework.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/** HTTP Basic userid-password authentication against the configured accounts. */
public class BasicAuthenticator implements Authenticator {

    private static final String PREFIX = "Basic ";

    private final SecurityProperties properties;

    public BasicAuthenticator(SecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean authenticate(String authorizationHeader, AccessLevel level) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(PREFIX)) {
            return false;
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(authorizationHeader.substring(PREFIX.length())),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return false;
        }
        Account expected = level == AccessLevel.INBOUND ? properties.inbound() : properties.operations();
        return same(decoded, expected.username() + ":" + expected.password());
    }

    private static boolean same(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
