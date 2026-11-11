package com.integration.app.appb;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppBProperties.class)
class AppBConfiguration {

    @Bean
    AppBClient appBClient(AppBProperties properties) {
        return new RestAppBClient(properties);
    }
}
