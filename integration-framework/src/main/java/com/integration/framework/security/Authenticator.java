package com.integration.framework.security;

/** The authentication mechanism is hidden behind this abstraction (userid-password now, JWT bearer later). */
public interface Authenticator {

    boolean authenticate(String authorizationHeader, AccessLevel level);
}
