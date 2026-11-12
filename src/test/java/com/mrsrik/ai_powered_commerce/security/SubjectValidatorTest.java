package com.mrsrik.ai_powered_commerce.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

class SubjectValidatorTest {

    private final SubjectValidator validator = new SubjectValidator();

    private static Jwt jwtWithSubject(String subject) {
        return Jwt.withTokenValue("token").header("alg", "RS256").claims(c -> {
            c.put("iss", "https://issuer.example");
            if (subject != null) {
                c.put("sub", subject);
            }
        }).build();
    }

    @Test
    void succeedsWhenSubjectPresent() {
        assertThat(validator.validate(jwtWithSubject("auth0|abc")).hasErrors()).isFalse();
    }

    @Test
    void failsWhenSubjectIsNull() {
        assertIdentityUnresolvable(validator.validate(jwtWithSubject(null)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t\n"})
    void failsWhenSubjectIsBlank(String subject) {
        assertIdentityUnresolvable(validator.validate(jwtWithSubject(subject)));
    }

    @Test
    void constantsAreStable() {
        assertThat(SubjectValidator.ERROR_CODE).isEqualTo("identity_unresolvable");
        assertThat(SubjectValidator.IDENTITY_UNRESOLVABLE_MARKER).isNotBlank();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void markerSurvivesIntoBadJwtExceptionMessage(String subject) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) pair.getPublic()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(validator)));

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer("https://issuer.example")
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build();
        SignedJWT signed = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        signed.sign(new RSASSASigner(pair.getPrivate()));

        BadJwtException thrown = org.junit.jupiter.api.Assertions.assertThrows(BadJwtException.class,
                () -> decoder.decode(signed.serialize()));
        System.out.println("BadJwtException message: " + thrown.getMessage());
        assertThat(thrown.getMessage()).contains(SubjectValidator.IDENTITY_UNRESOLVABLE_MARKER);
    }

    private static void assertIdentityUnresolvable(OAuth2TokenValidatorResult result) {
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).hasSize(1);
        OAuth2Error error = result.getErrors().iterator().next();
        assertThat(error.getErrorCode()).isEqualTo("identity_unresolvable");
        assertThat(error.getDescription()).isEqualTo(SubjectValidator.IDENTITY_UNRESOLVABLE_MARKER);
    }
}
