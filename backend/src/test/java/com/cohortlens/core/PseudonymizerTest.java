package com.cohortlens.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PseudonymizerTest {
    private static final String SECRET = "a-test-secret-with-enough-length";

    @Test
    void sameIdAndSecretGiveSameKey() {
        Pseudonymizer p = new Pseudonymizer(SECRET);
        assertEquals(p.pseudonymize("S1001"), p.pseudonymize("S1001"));
    }

    @Test
    void whitespaceAroundIdIsIgnored() {
        Pseudonymizer p = new Pseudonymizer(SECRET);
        assertEquals(p.pseudonymize("S1001"), p.pseudonymize("  S1001 "));
    }

    @Test
    void differentIdsGiveDifferentKeys() {
        Pseudonymizer p = new Pseudonymizer(SECRET);
        assertNotEquals(p.pseudonymize("S1001"), p.pseudonymize("S1002"));
    }

    @Test
    void differentSecretsGiveDifferentKeys() {
        assertNotEquals(new Pseudonymizer(SECRET).pseudonymize("S1001"),
                new Pseudonymizer("another-secret-of-enough-length").pseudonymize("S1001"));
    }

    @Test
    void keyIsShortHexAndDoesNotContainTheId() {
        String key = new Pseudonymizer(SECRET).pseudonymize("S1001");
        assertEquals(16, key.length());
        assertFalse(key.contains("S1001"));
    }

    @Test
    void shortSecretIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Pseudonymizer("short"));
    }

    @Test
    void blankIdIsRejected() {
        Pseudonymizer p = new Pseudonymizer(SECRET);
        assertThrows(IllegalArgumentException.class, () -> p.pseudonymize("  "));
    }
}
