package com.mrsrik.ai_powered_commerce.user.service;

import java.util.Locale;

/**
 * Normalizes emails before they are stored or looked up. Trimming and lower-casing make the case-sensitive unique
 * constraint {@code users_email_key} behave case-insensitively (D-76). {@code Locale.ROOT} avoids locale surprises
 * such as the Turkish dotless i.
 */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    /** Returns the trimmed, lower-cased email, or null when the input is null. */
    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
