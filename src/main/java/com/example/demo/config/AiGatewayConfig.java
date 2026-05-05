package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
public class AiGatewayConfig {

    @Bean
    RestClient aiGatewayRestClient(AiGatewayProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getReadTimeoutMs());

        RestClient.Builder builder = RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .requestFactory(requestFactory);

        if (StringUtils.hasText(properties.getServiceToken())) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getServiceToken().trim());
        }

        return builder.build();
    }
}


