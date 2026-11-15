package com.mrsrik.ai_powered_commerce.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrsrik.ai_powered_commerce.support.AbstractWebIT;

/**
 * Smoke tests for OpenAPI 3.0 documentation and Swagger UI (Requirements 9.1, 9.2, 9.3, 9.4, 5.8).
 */
class OpenApiSmokeIT extends AbstractWebIT {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("OpenAPI JSON and Swagger UI endpoints are reachable without a token")
    void openApiAndSwaggerUiAreReachableWithoutToken() throws Exception {
        // /v3/api-docs reachable without authentication
        mockMvc.perform(get("/v3/api-docs")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists());

        // Group definitions reachable without authentication
        mockMvc.perform(get("/v3/api-docs/all").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs/api").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs/internal").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // /swagger-ui.html reachable without authentication (may be 200 or 302 redirect)
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());

        // /swagger-ui/index.html reachable without authentication
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("OpenAPI document configures bearerAuth and internalApiKey security schemes")
    void openApiConfiguresSecuritySchemes() throws Exception {
        String responseContent = mockMvc.perform(get("/v3/api-docs")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(responseContent);

        // Bearer Auth Scheme
        JsonNode bearerAuthScheme = root.path("components").path("securitySchemes").path("bearerAuth");
        assertThat(bearerAuthScheme.isMissingNode()).isFalse();
        assertThat(bearerAuthScheme.path("type").asText()).isEqualTo("http");
        assertThat(bearerAuthScheme.path("scheme").asText()).isEqualTo("bearer");
        assertThat(bearerAuthScheme.path("bearerFormat").asText()).isEqualTo("JWT");

        // Internal API Key Scheme
        JsonNode apiKeyScheme = root.path("components").path("securitySchemes").path("internalApiKey");
        assertThat(apiKeyScheme.isMissingNode()).isFalse();
        assertThat(apiKeyScheme.path("type").asText()).isEqualTo("apiKey");
        assertThat(apiKeyScheme.path("name").asText()).isEqualTo("X-Internal-Api-Key");
        assertThat(apiKeyScheme.path("in").asText()).isEqualTo("header");

        // Global security requirement
        JsonNode securityArray = root.path("security");
        assertThat(securityArray.isArray()).isTrue();
        boolean hasBearerAuth = false;
        for (JsonNode sec : securityArray) {
            if (sec.has("bearerAuth")) {
                hasBearerAuth = true;
                break;
            }
        }
        assertThat(hasBearerAuth).isTrue();
    }

    @Test
    @DisplayName("Public 'api' group includes all /api/** paths and excludes /internal paths")
    void publicApiGroupIncludesApiPathsAndExcludesInternalPaths() throws Exception {
        String responseContent = mockMvc.perform(get("/v3/api-docs/api")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(responseContent);
        JsonNode paths = root.path("paths");
        assertThat(paths.isMissingNode()).isFalse();

        // Verify key /api/** paths are present
        List<String> expectedApiPaths = List.of(
                "/api/products",
                "/api/products/{id}",
                "/api/categories",
                "/api/categories/{id}",
                "/api/cart",
                "/api/cart/items",
                "/api/cart/items/{itemId}",
                "/api/orders",
                "/api/orders/{id}",
                "/api/orders/{id}/status",
                "/api/orders/{id}/cancel",
                "/api/auth/signup",
                "/api/auth/login",
                "/api/users/me",
                "/api/ai/conversations",
                "/api/ai/conversations/{id}/messages"
        );

        for (String expectedPath : expectedApiPaths) {
            assertThat(paths.has(expectedPath))
                    .withFailMessage("Expected path %s was not found in OpenAPI paths", expectedPath)
                    .isTrue();
        }

        // Verify old /api/users/register is absent
        assertThat(paths.has("/api/users/register"))
                .withFailMessage("Legacy path /api/users/register must not be present in OpenAPI paths")
                .isFalse();

        // Verify no path starts with /internal in the public 'api' group
        Iterator<String> fieldNames = paths.fieldNames();
        while (fieldNames.hasNext()) {
            String path = fieldNames.next();
            assertThat(path)
                    .withFailMessage("Internal path %s must not be in the public 'api' group", path)
                    .doesNotStartWith("/internal");
        }
    }

    @Test
    @DisplayName("Internal group includes /internal/orders/advance and excludes /api paths")
    void internalGroupIncludesInternalOrderEndpoint() throws Exception {
        String responseContent = mockMvc.perform(get("/v3/api-docs/internal")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(responseContent);
        JsonNode paths = root.path("paths");
        assertThat(paths.isMissingNode()).isFalse();

        // /internal/orders/advance is present
        assertThat(paths.has("/internal/orders/advance")).isTrue();

        // /api paths are not in the internal group
        assertThat(paths.has("/api/products")).isFalse();
        assertThat(paths.has("/api/orders")).isFalse();

        // Verify operation has security requirement on internalApiKey
        JsonNode advanceOperation = paths.path("/internal/orders/advance").path("post");
        assertThat(advanceOperation.isMissingNode()).isFalse();
        JsonNode securityNode = advanceOperation.path("security");
        boolean hasInternalApiKey = false;
        for (JsonNode sec : securityNode) {
            if (sec.has("internalApiKey")) {
                hasInternalApiKey = true;
                break;
            }
        }
        assertThat(hasInternalApiKey).isTrue();
    }

    @Test
    @DisplayName("'all' group includes both /api/** endpoints and /internal endpoints")
    void allGroupIncludesAllEndpoints() throws Exception {
        String responseContent = mockMvc.perform(get("/v3/api-docs/all")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(responseContent);
        JsonNode paths = root.path("paths");
        assertThat(paths.isMissingNode()).isFalse();

        assertThat(paths.has("/api/products")).isTrue();
        assertThat(paths.has("/api/orders")).isTrue();
        assertThat(paths.has("/internal/orders/advance")).isTrue();
    }

    @Test
    @DisplayName("Public catalog GET operations override global security with empty requirements")
    void publicCatalogOperationsOverrideSecurityWithEmptyRequirements() throws Exception {
        String responseContent = mockMvc.perform(get("/v3/api-docs/api")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(responseContent);
        JsonNode paths = root.path("paths");

        // /api/products GET
        JsonNode listProductsGet = paths.path("/api/products").path("get");
        assertThat(listProductsGet.has("security")).isTrue();
        assertThat(listProductsGet.path("security").isEmpty()).isTrue();

        // /api/products/{id} GET
        JsonNode getProductGet = paths.path("/api/products/{id}").path("get");
        assertThat(getProductGet.has("security")).isTrue();
        assertThat(getProductGet.path("security").isEmpty()).isTrue();

        // /api/categories GET
        JsonNode listCategoriesGet = paths.path("/api/categories").path("get");
        assertThat(listCategoriesGet.has("security")).isTrue();
        assertThat(listCategoriesGet.path("security").isEmpty()).isTrue();

        // /api/auth/signup POST
        JsonNode signupPost = paths.path("/api/auth/signup").path("post");
        assertThat(signupPost.has("security")).isTrue();
        assertThat(signupPost.path("security").isEmpty()).isTrue();

        // /api/auth/login POST
        JsonNode loginPost = paths.path("/api/auth/login").path("post");
        assertThat(loginPost.has("security")).isTrue();
        assertThat(loginPost.path("security").isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Admin operations state the required ADMIN role in their description")
    void adminOperationsStateRequiredRoleInDescription() throws Exception {
        String responseContent = mockMvc.perform(get("/v3/api-docs/api")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(responseContent);
        JsonNode paths = root.path("paths");

        // Product admin endpoints
        assertThat(paths.path("/api/products").path("post").path("description").asText())
                .contains("Requires ADMIN role");
        assertThat(paths.path("/api/products/{id}").path("put").path("description").asText())
                .contains("Requires ADMIN role");
        assertThat(paths.path("/api/products/{id}").path("delete").path("description").asText())
                .contains("Requires ADMIN role");

        // Category admin endpoints
        assertThat(paths.path("/api/categories").path("post").path("description").asText())
                .contains("Requires ADMIN role");
        assertThat(paths.path("/api/categories/{id}").path("put").path("description").asText())
                .contains("Requires ADMIN role");
        assertThat(paths.path("/api/categories/{id}").path("delete").path("description").asText())
                .contains("Requires ADMIN role");

        // Order admin endpoint
        assertThat(paths.path("/api/orders/{id}/status").path("patch").path("description").asText())
                .contains("Requires ADMIN role");
    }

    @Test
    @DisplayName("Protected endpoint called without a token returns 401 before controller runs")
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("AUTHENTICATION_REQUIRED")));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("AUTHENTICATION_REQUIRED")));

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("AUTHENTICATION_REQUIRED")));

        mockMvc.perform(get("/api/ai/conversations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("AUTHENTICATION_REQUIRED")));
    }
}
