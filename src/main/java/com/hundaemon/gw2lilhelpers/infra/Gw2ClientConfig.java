package com.hundaemon.gw2lilhelpers.infra;

import com.hundaemon.gw2lilhelpers.api.client.ApiClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Gw2ClientConfig {

    @ConfigurationProperties("gw2api")
    @Bean
    ApiClientProperties gw2ApiClientProperties() {
        return new ApiClientProperties();
    }

    @Bean
    ApiClient gw2ApiClient(final ApiClientProperties properties) {
        final ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(properties.getBaseUrl());
        return apiClient;
    }
}
