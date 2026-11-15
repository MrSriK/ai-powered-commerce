package com.mrsrik.ai_powered_commerce.common.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * OpenAPI 3.0 configuration with JWT bearer authentication, internal API key authentication,
 * and springdoc group definitions (Requirements 9.1, 9.3, 5.8).
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
    public static final String INTERNAL_API_KEY = "internalApiKey";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI-Powered Commerce API")
                        .version("1.0.0")
                        .description("REST API for AI-Powered Commerce platform"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .name(BEARER_AUTH)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT token issued by Auth0"))
                        .addSecuritySchemes(INTERNAL_API_KEY, new SecurityScheme()
                                .name("X-Internal-Api-Key")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("Internal shared secret for machine-to-machine scheduler operations")));
    }

    @Bean
    public GroupedOpenApi allGroup() {
        return GroupedOpenApi.builder()
                .group("all")
                .displayName("All Endpoints")
                .pathsToMatch("/api/**", "/internal/**")
                .build();
    }

    @Bean
    public GroupedOpenApi apiGroup() {
        return GroupedOpenApi.builder()
                .group("api")
                .displayName("Public & Customer/Admin API")
                .pathsToMatch("/api/**")
                .build();
    }

    @Bean
    public GroupedOpenApi internalGroup() {
        return GroupedOpenApi.builder()
                .group("internal")
                .displayName("Internal Scheduler API")
                .pathsToMatch("/internal/**")
                .build();
    }
}
