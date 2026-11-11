package com.integration.app.somedata;

import com.integration.app.appb.SomeData;
import org.apache.camel.ProducerTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Inbound REST entry of the sample operation getSomeData (APP-A calls this). */
@RestController
class SomeDataController {

    private final ProducerTemplate camel;

    SomeDataController(ProducerTemplate camel) {
        this.camel = camel;
    }

    @GetMapping("/api/v1/getSomeData")
    SomeData getSomeData() {
        return camel.requestBody(GetSomeDataRoute.ENDPOINT, null, SomeData.class);
    }
}
