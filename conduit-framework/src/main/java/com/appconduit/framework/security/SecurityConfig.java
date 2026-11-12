package com.appconduit.framework.security;

import com.appconduit.framework.IntegrationProperties;
import com.appconduit.framework.error.ErrorResponseWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * HTTP Basic with userid-password (revision 2). Two chains: actuator (health open, info and metrics need the operations account)
 * and API (everything else needs the inbound account). Moving to JWT later is a change to these chains only.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    UserDetailsService inboundUsers(IntegrationProperties properties) {
        return users(properties.security().inbound(), "API");
    }

    @Bean
    UserDetailsService operationsUsers(IntegrationProperties properties) {
        return users(properties.security().operations(), "OPERATIONS");
    }

    @Bean
    @Order(1)
    SecurityFilterChain actuatorChain(HttpSecurity http, ErrorResponseWriter entryPoint,
                                      UserDetailsService operationsUsers) throws Exception {
        http.securityMatcher("/actuator/**")
                .authorizeHttpRequests(a -> a.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .userDetailsService(operationsUsers);
        return stateless(http, entryPoint).build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiChain(HttpSecurity http, ErrorResponseWriter entryPoint,
                                 UserDetailsService inboundUsers) throws Exception {
        http.authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .userDetailsService(inboundUsers);
        return stateless(http, entryPoint).build();
    }

    private static HttpSecurity stateless(HttpSecurity http, ErrorResponseWriter entryPoint) {
        return http.httpBasic(b -> b.authenticationEntryPoint(entryPoint))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(c -> c.disable());
    }

    private static UserDetailsService users(IntegrationProperties.Account account, String role) {
        return new InMemoryUserDetailsManager(
                User.withUsername(account.user()).password("{noop}" + account.password()).roles(role).build());
    }
}
