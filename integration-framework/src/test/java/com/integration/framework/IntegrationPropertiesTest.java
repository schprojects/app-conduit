package com.integration.framework;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IntegrationPropertiesTest {

    @Test
    void acceptsAlphanumericShortName() {
        assertEquals("INTX", new IntegrationProperties("INTX").appShortName());
    }

    @Test
    void rejectsShortNameWithHyphen() {
        assertThrows(IllegalArgumentException.class, () -> new IntegrationProperties("APP-A"));
    }

    @Test
    void rejectsMissingShortName() {
        assertThrows(IllegalArgumentException.class, () -> new IntegrationProperties(null));
    }
}
