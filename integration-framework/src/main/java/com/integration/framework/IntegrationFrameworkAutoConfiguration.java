package com.integration.framework;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@AutoConfiguration
@EnableConfigurationProperties(IntegrationProperties.class)
public class IntegrationFrameworkAutoConfiguration {
}
