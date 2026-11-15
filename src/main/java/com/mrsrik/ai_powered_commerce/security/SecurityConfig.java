package com.mrsrik.ai_powered_commerce.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import com.mrsrik.ai_powered_commerce.user.repository.UserRepository;

/**
 * Security chain for user traffic (everything except {@code /internal/**},
 * which
 * has its own higher-priority chain). Stateless JWT resource server; the route
 * rules are the first authorization layer, method security the second.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Explicit bean so web slices, which do not scan configuration classes, never
     * see it.
     */
    @Bean
    LocalUserJwtAuthenticationConverter localUserJwtAuthenticationConverter(UserRepository userRepository) {
        return new LocalUserJwtAuthenticationConverter(userRepository);
    }

    @Bean
    @Order(2)
    SecurityFilterChain userSecurityFilterChain(HttpSecurity http,
            LocalUserJwtAuthenticationConverter converter,
            RestAuthenticationEntryPoint entryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            RestAuthenticationFailureHandler failureHandler) throws Exception {
        http.csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**", "/api/categories", "/api/categories/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .anyRequest().hasAnyRole("CUSTOMER", "ADMIN"))
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .withObjectPostProcessor(new ObjectPostProcessor<BearerTokenAuthenticationFilter>() {
                            @Override
                            public <F extends BearerTokenAuthenticationFilter> F postProcess(F filter) {
                                // The default handler rethrows AuthenticationServiceException (a 500).
                                filter.setAuthenticationFailureHandler(failureHandler);
                                return filter;
                            }
                        }))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }
}
