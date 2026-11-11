package com.integration.app.appb;

import com.integration.framework.error.IntegrationException;
import com.integration.framework.logging.AppLogContext;
import com.integration.framework.tracking.RequestTrackingFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/** REST implementation of the APP-B client: sends credentials and X-Request-Id, maps failures. */
public class RestAppBClient implements AppBClient {

    private static final String APP = "APPB";
    private static final Logger log = LoggerFactory.getLogger(RestAppBClient.class);

    private final RestClient restClient;
    private final String authorization;

    public RestAppBClient(AppBProperties properties) {
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
        this.authorization = "Basic " + Base64.getEncoder()
                .encodeToString((properties.username() + ":" + properties.password()).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public SomeData getSomeData() {
        try (AppLogContext ignored = AppLogContext.enter(APP)) {
            return call();
        }
    }

    private SomeData call() {
        log.info("calling APP-B getSomeData");
        try {
            Map<String, Object> response = restClient.get().uri("/api/data")
                    .header("Authorization", authorization)
                    .header(RequestTrackingFilter.REQUEST_ID_HEADER, String.valueOf(MDC.get(RequestTrackingFilter.REQUEST_ID_KEY)))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            log.info("APP-B getSomeData succeeded");
            return new SomeData(String.valueOf(response.get("data")));
        } catch (RestClientException e) {
            log.warn("APP-B getSomeData failed: {}", e.getMessage());
            throw isTimeout(e) ? IntegrationException.timeout(APP, e) : IntegrationException.unavailable(APP, e);
        }
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpTimeoutException || t instanceof java.net.SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
