package com.mrsrik.ai_powered_commerce.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Rejects a JWT whose {@code sub} is null or blank; Spring turns the failure into a {@code BadJwtException}
 * whose message contains {@link #IDENTITY_UNRESOLVABLE_MARKER}, which the auth failure classifier matches.
 */
public final class SubjectValidator implements OAuth2TokenValidator<Jwt> {

    /** Error code of the failure (not copied into the exception message, hence the marker text). */
    public static final String ERROR_CODE = "identity_unresolvable";

    /** Fixed description used by the classifier to recognise this failure in the exception message. */
    public static final String IDENTITY_UNRESOLVABLE_MARKER = "The token does not contain a usable subject";

    private static final OAuth2Error SUBJECT_UNUSABLE = new OAuth2Error(ERROR_CODE, IDENTITY_UNRESOLVABLE_MARKER, null);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            return OAuth2TokenValidatorResult.failure(SUBJECT_UNUSABLE);
        }
        return OAuth2TokenValidatorResult.success();
    }
}
