package com.mrsrik.ai_powered_commerce.auth.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.mrsrik.ai_powered_commerce.auth.dto.AuthTokenResponse;
import com.mrsrik.ai_powered_commerce.common.error.Auth0BadCredentialsException;
import com.mrsrik.ai_powered_commerce.common.error.Auth0ConflictException;
import com.mrsrik.ai_powered_commerce.common.error.Auth0UnavailableException;
import com.mrsrik.ai_powered_commerce.security.AuthProperties;

/**
 * HTTP client proxy to Auth0 for signup and token issuance.
 * Ensures credentials and secrets are never logged.
 */
@Service
public class Auth0Client {

    private static final Logger log = LoggerFactory.getLogger(Auth0Client.class);

    private final RestClient restClient;
    private final AuthProperties authProperties;

    public Auth0Client(@Qualifier("auth0RestClient") RestClient restClient, AuthProperties authProperties) {
        this.restClient = restClient;
        this.authProperties = authProperties;
    }

    /**
     * Creates a new user in Auth0 database connection.
     * Calls POST /dbconnections/signup.
     */
    public Auth0SignupResponse createUser(String email, String password, String name) {
        Auth0SignupRequest request = new Auth0SignupRequest(
                authProperties.clientId(),
                email,
                password,
                authProperties.connection(),
                name
        );

        try {
            log.debug("Initiating Auth0 user signup for email={}", email);
            return restClient.post()
                    .uri("/dbconnections/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Auth0SignupResponse.class);
        } catch (HttpClientErrorException.Conflict e) {
            log.warn("Auth0 signup conflict (409) for email={}", email);
            throw new Auth0ConflictException();
        } catch (HttpClientErrorException.BadRequest e) {
            String responseBody = e.getResponseBodyAsString();
            log.warn("Auth0 signup bad request (400) for email={}: {}", email, responseBody);
            if (responseBody.contains("user_exists") || responseBody.contains("already exists")) {
                throw new Auth0ConflictException();
            }
            throw new Auth0BadCredentialsException();
        } catch (HttpServerErrorException | ResourceAccessException e) {
            log.error("Auth0 service unavailable during signup for email={}", email, e);
            throw new Auth0UnavailableException(e);
        } catch (Exception e) {
            log.error("Unexpected error during Auth0 signup for email={}", email, e);
            throw new Auth0UnavailableException(e);
        }
    }

    /**
     * Authenticates user via Resource Owner Password Grant.
     * Calls POST /oauth/token with JSON body:
     * { client_id, username, password, grant_type, client_secret, audience }
     */
    public AuthTokenResponse getToken(String email, String password) {
        Auth0TokenRequest request = new Auth0TokenRequest(
                authProperties.clientId(),
                email,
                password,
                "password",
                authProperties.clientSecret(),
                authProperties.audience()
        );

        try {
            log.debug("Initiating Auth0 token request for email={}", email);
            return restClient.post()
                    .uri("/oauth/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AuthTokenResponse.class);
        } catch (HttpClientErrorException e) {
            log.warn("Auth0 token request failed with status={} for email={}", e.getStatusCode(), email);
            throw new Auth0BadCredentialsException();
        } catch (HttpServerErrorException | ResourceAccessException e) {
            log.error("Auth0 service unavailable during token request for email={}", email, e);
            throw new Auth0UnavailableException(e);
        } catch (Exception e) {
            log.error("Unexpected error during Auth0 token request for email={}", email, e);
            throw new Auth0UnavailableException(e);
        }
    }
}
