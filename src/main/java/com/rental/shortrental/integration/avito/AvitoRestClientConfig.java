package com.rental.shortrental.integration.avito;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Shared HTTP client for Avito API calls — avoids indefinite hangs on slow networks.
 */
@Configuration
public class AvitoRestClientConfig {

    @Bean
    @Qualifier("avitoRestClient")
    public RestClient avitoRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(15));
        factory.setReadTimeout(Duration.ofSeconds(45));
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
