package com.integration.app.somedata;

import com.integration.app.appb.SomeData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SomeDataServiceTest {

    @Test
    void delegatesToTheAppBClient() {
        SomeDataService service = new SomeDataService(() -> new SomeData("x"));

        assertEquals("x", service.getSomeData().data());
    }
}
