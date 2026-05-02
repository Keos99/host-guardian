package com.example.guardian.service;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Component
public class HttpHealthChecker {

    private final RestTemplateBuilder restTemplateBuilder;

    public HttpHealthChecker(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplateBuilder = restTemplateBuilder;
    }

    public boolean isHealthy(String url, Duration timeout) {
        try {
            RestTemplate restTemplate = restTemplateBuilder
                    .setConnectTimeout(timeout)
                    .setReadTimeout(timeout)
                    .build();

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }
}
