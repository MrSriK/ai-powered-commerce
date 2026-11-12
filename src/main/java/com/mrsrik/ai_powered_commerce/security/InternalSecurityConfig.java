package com.mrsrik.ai_powered_commerce.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Filter chain for {@code /internal/**} (Requirements 12.10 to 12.12). It has no
 * {@code oauth2ResourceServer}, so a user JWT is never parsed here, and it is the only chain
 * that contains the API-key filter.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InternalApiKeyProperties.class)
public class InternalSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain internalSecurityFilterChain(HttpSecurity http, InternalApiKeyProperties properties,
            RestAuthenticationEntryPoint entryPoint, RestAccessDeniedHandler accessDeniedHandler)
            throws Exception {
        InternalApiKeyFilter keyFilter = new InternalApiKeyFilter(properties.apiKey(), entryPoint);
        http.securityMatcher("/internal/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().hasRole("INTERNAL"))
                .addFilterBefore(keyFilter, AuthorizationFilter.class)
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }
}
