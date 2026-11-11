package com.integration.app.somedata;

import com.integration.app.appb.AppBClient;
import com.integration.app.appb.SomeData;
import org.springframework.stereotype.Service;

/** Business Service of the sample integration: the only caller of the APP-B client. */
@Service
public class SomeDataService {

    private final AppBClient appB;

    public SomeDataService(AppBClient appB) {
        this.appB = appB;
    }

    public SomeData getSomeData() {
        return appB.getSomeData();
    }
}
