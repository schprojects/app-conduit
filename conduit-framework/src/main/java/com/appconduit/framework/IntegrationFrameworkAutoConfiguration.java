package com.appconduit.framework;

import com.appconduit.framework.error.ContractViolationPolicy;
import com.appconduit.framework.error.ErrorResponseProcessor;
import com.appconduit.framework.error.ErrorResponseWriter;
import com.appconduit.framework.route.IntegrationErrors;
import com.appconduit.framework.route.RequestContextInterceptor;
import com.appconduit.framework.security.SecurityConfig;
import com.appconduit.framework.tracking.AuditLog;
import com.appconduit.framework.tracking.RequestAuditNotifier;
import com.appconduit.framework.tracking.RequestIdFilter;
import com.appconduit.framework.tracking.RequestIdGenerator;
import org.apache.camel.CamelContext;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.spring.boot.CamelContextConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
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

    /** Outermost filter: runs before Spring Security, so every response (also a 401) carries the id. */
    @Bean
    FilterRegistrationBean<RequestIdFilter> requestIdFilter(RequestIdGenerator generator) {
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(new RequestIdFilter(generator));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    AuditLog auditLog() {
        return new AuditLog();
    }

    /** Registers the audit notifier with Camel's management strategy (Camel Spring Boot's own extension point). */
    @Bean
    CamelContextConfiguration requestAuditRegistration(AuditLog audit) {
        return new CamelContextConfiguration() {
            @Override
            public void beforeApplicationStart(CamelContext context) {
                context.getManagementStrategy().addEventNotifier(new RequestAuditNotifier(audit));
            }

            @Override
            public void afterApplicationStart(CamelContext context) {
            }
        };
    }

    @Bean
    ErrorResponseWriter errorResponseWriter(JsonMapper mapper, AuditLog audit) {
        return new ErrorResponseWriter(mapper, audit);
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
    RequestContextInterceptor requestContextInterceptor(AuditLog audit) {
        return new RequestContextInterceptor(audit);
    }

    @Bean
    IntegrationErrors integrationErrors(RequestContextInterceptor context, ErrorResponseProcessor errorResponse) {
        return new IntegrationErrors(context, errorResponse);
    }
}
