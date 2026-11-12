package com.appconduit.framework.tracking;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestIdGeneratorTest {

    private final RequestIdGenerator generator = new RequestIdGenerator("INTX");

    @Test
    void formatIsShortNameDashTwelveAlphanumerics() {
        assertTrue(generator.next().matches("INTX-[A-Za-z0-9]{12}"));
    }

    @Test
    void idsAreUnique() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(generator.next());
        }
        assertEquals(10_000, ids.size());
    }
}
