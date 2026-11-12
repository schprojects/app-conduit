package com.appconduit.framework;

import com.appconduit.framework.IntegrationProperties.Account;
import com.appconduit.framework.IntegrationProperties.Security;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IntegrationPropertiesTest {

    private static final Security SECURITY =
            new Security(new Account("in", "pw"), new Account("ops", "pw"));

    @Test
    void acceptsAlphanumericShortName() {
        assertEquals("INTX", new IntegrationProperties("INTX", SECURITY).appShortName());
    }

    @Test
    void rejectsShortNameWithHyphen() {
        assertThrows(IllegalArgumentException.class, () -> new IntegrationProperties("APP-A", SECURITY));
    }

    @Test
    void rejectsMissingShortName() {
        assertThrows(IllegalArgumentException.class, () -> new IntegrationProperties(null, SECURITY));
    }

    @Test
    void rejectsMissingOrBlankCredentials() {
        assertThrows(IllegalArgumentException.class, () -> new IntegrationProperties("INTX", null));
        assertThrows(IllegalArgumentException.class, () -> new Security(new Account("in", "pw"), null));
        assertThrows(IllegalArgumentException.class, () -> new Account("in", ""));
        assertThrows(IllegalArgumentException.class, () -> new Account(null, "pw"));
    }
}
