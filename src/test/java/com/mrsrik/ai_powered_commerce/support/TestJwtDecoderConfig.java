package com.mrsrik.ai_powered_commerce.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.mrsrik.ai_powered_commerce.security.JwtDecoderConfig;

/**
 * Replaces the Auth0-backed decoder with one that verifies tokens signed by {@link TestTokens}.
 * It uses the production validator composition, so timestamps, issuer, audience and subject
 * rules run exactly as in production. {@code @Primary} wins over the production
 * {@code jwtDecoder} bean, so no bean overriding is needed.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestJwtDecoderConfig {

    @Bean
    @Primary
    JwtDecoder testJwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(TestTokens.publicKey()).build();
        decoder.setJwtValidator(JwtDecoderConfig.tokenValidator(TestTokens.ISSUER, TestTokens.AUDIENCE));
        return decoder;
    }
}
