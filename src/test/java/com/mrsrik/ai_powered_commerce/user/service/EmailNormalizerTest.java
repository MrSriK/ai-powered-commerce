package com.mrsrik.ai_powered_commerce.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class EmailNormalizerTest {

    @Test
    void nullStaysNull() {
        assertNull(EmailNormalizer.normalize(null));
    }

    @Test
    void lowerCasesMixedCase() {
        assertEquals("john.doe@example.test", EmailNormalizer.normalize("John.DOE@Example.TEST"));
    }

    @Test
    void trimsSurroundingSpaces() {
        assertEquals("a@b.test", EmailNormalizer.normalize("  A@B.test \t"));
    }

    @Test
    void ignoresTurkishDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr"));
            assertEquals("i@x.test", EmailNormalizer.normalize("I@X.TEST"));
        } finally {
            Locale.setDefault(original);
        }
    }
}
