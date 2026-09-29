package com.cohortlens.core;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Replaces a student id with a keyed hash (HMAC SHA 256). The same id always maps to the
 * same key for a given secret, so the student can be followed across terms, but the
 * original id cannot be recovered without the secret. Keep the secret out of source control.
 */
public final class Pseudonymizer {
    private static final String ALGORITHM = "HmacSHA256";
    private static final int MIN_SECRET_LENGTH = 16;
    private final SecretKeySpec key;

    public Pseudonymizer(String secret) {
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalArgumentException(
                    "Pseudonymization secret must be at least " + MIN_SECRET_LENGTH + " characters");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String pseudonymize(String studentId) {
        if (studentId == null || studentId.isBlank()) {
            throw new IllegalArgumentException("student id is required");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal(studentId.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) { // 16 hex characters is plenty to avoid collisions at this scale
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HMAC not available", e);
        }
    }
}
