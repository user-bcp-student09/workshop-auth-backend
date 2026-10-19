package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {
    private final PasswordHasher passwordHasher = new PasswordHasher();

    @Test
    void saltedHashAcceptsOnlyOriginalPassword() {
        String firstHash = passwordHasher.hash("FictionalStudentPassword-2026");
        String secondHash = passwordHasher.hash("FictionalStudentPassword-2026");

        assertNotEquals(firstHash, secondHash);
        assertTrue(passwordHasher.verify("FictionalStudentPassword-2026", firstHash));
        assertFalse(passwordHasher.verify("incorrect-password", firstHash));
    }

    @Test
    void malformedOrUnsupportedHashIsRejected() {
        assertFalse(passwordHasher.verify("password", "plaintext"));
        assertFalse(passwordHasher.verify("password", "pbkdf2-sha256$999999999$AA==$AA=="));
        assertFalse(passwordHasher.verify("password", "pbkdf2-sha256$600000$%%%$AA=="));
        assertFalse(passwordHasher.verify(null, passwordHasher.hash("password")));
        assertThrows(IllegalArgumentException.class, () -> passwordHasher.hash(null));
    }
}
