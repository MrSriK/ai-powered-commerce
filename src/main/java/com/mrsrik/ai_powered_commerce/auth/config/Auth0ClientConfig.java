package com.mrsrik.ai_powered_commerce.auth.config;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.mrsrik.ai_powered_commerce.security.AuthProperties;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
public class Auth0ClientConfig {

    @Bean
    public RestClient auth0RestClient(AuthProperties authProperties) {
        String domain = authProperties.domain().trim();
        String baseUrl = domain.startsWith("http://") || domain.startsWith("https://")
                ? domain
                : "https://" + domain;

        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .baseUrl(baseUrl)
                .build();
    }
}
