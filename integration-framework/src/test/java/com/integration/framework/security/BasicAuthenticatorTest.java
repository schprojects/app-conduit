package com.integration.framework.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicAuthenticatorTest {

    private final BasicAuthenticator authenticator = new BasicAuthenticator(
            new SecurityProperties(new Account("in", "in-pw"), new Account("ops", "ops-pw")));

    private static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes());
    }

    @Test
    void acceptsCorrectAccountPerLevel() {
        assertTrue(authenticator.authenticate(basic("in", "in-pw"), AccessLevel.INBOUND));
        assertTrue(authenticator.authenticate(basic("ops", "ops-pw"), AccessLevel.OPERATIONS));
    }

    @Test
    void accountsAreNotInterchangeable() {
        assertFalse(authenticator.authenticate(basic("ops", "ops-pw"), AccessLevel.INBOUND));
        assertFalse(authenticator.authenticate(basic("in", "in-pw"), AccessLevel.OPERATIONS));
    }

    @Test
    void rejectsWrongMissingAndMalformedCredentials() {
        assertFalse(authenticator.authenticate(basic("in", "wrong"), AccessLevel.INBOUND));
        assertFalse(authenticator.authenticate(null, AccessLevel.INBOUND));
        assertFalse(authenticator.authenticate("Bearer abc", AccessLevel.INBOUND));
        assertFalse(authenticator.authenticate("Basic !!!not-base64", AccessLevel.INBOUND));
    }

    @Test
    void blankAccountIsRejectedAtStartup() {
        assertThrows(IllegalArgumentException.class, () -> new Account("in", " "));
        assertThrows(IllegalArgumentException.class, () -> new SecurityProperties(null, null));
    }
}
