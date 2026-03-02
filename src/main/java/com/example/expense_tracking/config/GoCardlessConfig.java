package com.example.expense_tracking.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@ConfigurationProperties(prefix = "gocardless")
@Data
public class GoCardlessConfig {
    private String secretId;
    private String secretKey;
    private String baseUrl;
    private String redirectUrl;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
