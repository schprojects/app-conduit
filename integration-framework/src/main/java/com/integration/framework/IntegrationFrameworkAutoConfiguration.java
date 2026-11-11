package com.integration.framework;

import com.integration.framework.audit.AuditFilter;
import com.integration.framework.audit.AuditStore;
import com.integration.framework.audit.JdbcAuditStore;
import com.integration.framework.error.GlobalExceptionHandler;
import com.integration.framework.security.Authenticator;
import com.integration.framework.security.BasicAuthenticator;
import com.integration.framework.security.SecurityFilter;
import com.integration.framework.security.SecurityProperties;
import com.integration.framework.tracking.RequestIdGenerator;
import com.integration.framework.tracking.RequestTrackingFilter;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;

@AutoConfiguration(afterName = "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration")
@EnableConfigurationProperties({IntegrationProperties.class, SecurityProperties.class})
public class IntegrationFrameworkAutoConfiguration {

    @Bean
    GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    Clock integrationClock() {
        return Clock.systemUTC();
    }

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

    @Bean(initMethod = "initSchema")
    JdbcAuditStore auditStore(JdbcTemplate jdbcTemplate) {
        return new JdbcAuditStore(jdbcTemplate);
    }

    @Bean
    FilterRegistrationBean<AuditFilter> auditFilter(AuditStore store, IntegrationProperties properties, Clock clock) {
        FilterRegistrationBean<AuditFilter> registration =
                new FilterRegistrationBean<>(new AuditFilter(store, properties.appShortName(), clock));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    @Bean
    Authenticator authenticator(SecurityProperties properties) {
        return new BasicAuthenticator(properties);
    }

    @Bean
    FilterRegistrationBean<SecurityFilter> securityFilter(Authenticator authenticator) {
        FilterRegistrationBean<SecurityFilter> registration =
                new FilterRegistrationBean<>(new SecurityFilter(authenticator));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        return registration;
    }

    /** Startup recovery: records still STARTED from a previous run are failed. */
    @Bean
    ApplicationRunner auditRecovery(AuditStore store, Clock clock) {
        return args -> store.failInterrupted(clock.instant());
    }
}
