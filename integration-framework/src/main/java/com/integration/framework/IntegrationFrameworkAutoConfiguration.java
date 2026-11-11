package com.integration.framework;

import com.integration.framework.tracking.RequestIdGenerator;
import com.integration.framework.tracking.RequestTrackingFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@AutoConfiguration
@EnableConfigurationProperties(IntegrationProperties.class)
public class IntegrationFrameworkAutoConfiguration {

    @Bean
    RequestIdGenerator requestIdGenerator(IntegrationProperties properties) {
        return new RequestIdGenerator(properties.appShortName());
    }

    @Bean
    FilterRegistrationBean<RequestTrackingFilter> requestTrackingFilter(RequestIdGenerator generator) {
        FilterRegistrationBean<RequestTrackingFilter> registration =
                new FilterRegistrationBean<>(new RequestTrackingFilter(generator));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
