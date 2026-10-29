package com.mx.tvmazemiddleware.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.web.client.RestClient;

@Configuration
@EnableRetry
@EnableConfigurationProperties(TvMazeClientProperties.class)
public class RestClientConfig {

    @Bean
    public RestClient tvMazeRestClient(
            @Value("${app.tvmaze.base-url:https://api.tvmaze.com}") String baseUrl,
            TvMazeClientProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setReadTimeout(properties.readTimeout());
        requestFactory.setConnectTimeout(properties.connectTimeout());

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
