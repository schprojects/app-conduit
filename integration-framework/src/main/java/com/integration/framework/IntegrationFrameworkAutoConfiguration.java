package com.integration.framework;

import com.integration.framework.error.ContractViolationPolicy;
import com.integration.framework.error.ErrorResponseProcessor;
import com.integration.framework.error.ErrorResponseWriter;
import com.integration.framework.route.IntegrationErrors;
import com.integration.framework.route.RequestContextInterceptor;
import com.integration.framework.route.RequestThreadExecutor;
import com.integration.framework.security.SecurityConfig;
import com.integration.framework.tracking.RequestIdGenerator;
import com.integration.framework.tracking.RequestLifecycleFilter;
import org.apache.camel.spi.RoutePolicyFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import tools.jackson.databind.json.JsonMapper;

@AutoConfiguration(beforeName = {
        "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration"})
@EnableConfigurationProperties(IntegrationProperties.class)
@Import(SecurityConfig.class)
public class IntegrationFrameworkAutoConfiguration {

    @Bean
    RequestIdGenerator requestIdGenerator(IntegrationProperties properties) {
        return new RequestIdGenerator(properties.appShortName());
    }

    /** Outermost filter: runs before Spring Security, so rejected calls also get an id and audit lines. */
    @Bean
    FilterRegistrationBean<RequestLifecycleFilter> requestLifecycleFilter(RequestIdGenerator generator) {
        FilterRegistrationBean<RequestLifecycleFilter> registration =
                new FilterRegistrationBean<>(new RequestLifecycleFilter(generator));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /** Picked by Camel's platform-http engine ahead of the pooled executors (see RequestThreadExecutor). */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    RequestThreadExecutor requestThreadExecutor() {
        return new RequestThreadExecutor();
    }

    @Bean
    ErrorResponseWriter errorResponseWriter(JsonMapper mapper) {
        return new ErrorResponseWriter(mapper);
    }

    @Bean
    ErrorResponseProcessor errorResponseProcessor(JsonMapper mapper) {
        return new ErrorResponseProcessor(mapper);
    }

    @Bean
    RoutePolicyFactory contractViolationPolicy(ErrorResponseProcessor errorResponse) {
        return (camelContext, routeId, route) -> new ContractViolationPolicy(errorResponse);
    }

    @Bean
    RequestContextInterceptor requestContextInterceptor() {
        return new RequestContextInterceptor();
    }

    @Bean
    IntegrationErrors integrationErrors(RequestContextInterceptor context, ErrorResponseProcessor errorResponse) {
        return new IntegrationErrors(context, errorResponse);
    }
}
