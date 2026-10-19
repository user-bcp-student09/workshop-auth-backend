package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@ApplicationScoped
public class PasswordHasher {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String HASH_VERSION = "pbkdf2-sha256";
    private static final int ITERATIONS = 600_000;
    private static final int MINIMUM_ITERATIONS = 100_000;
    private static final int MAXIMUM_ITERATIONS = 1_000_000;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int HASH_LENGTH_BITS = 256;
    private static final int HASH_LENGTH_BYTES = HASH_LENGTH_BITS / Byte.SIZE;

    private final SecureRandom secureRandom = new SecureRandom();

    public String hash(String password) {
        if (password == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        secureRandom.nextBytes(salt);
        byte[] derivedKey = derive(password, salt, ITERATIONS);
        return HASH_VERSION + "$" + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(derivedKey);
    }

    public boolean verify(String password, String encodedHash) {
        if (password == null || encodedHash == null) {
            return false;
        }
        String[] parts = encodedHash.split("\\$", -1);
        if (parts.length != 4 || !HASH_VERSION.equals(parts[0])) {
            return false;
        }

        int iterations;
        byte[] salt;
        byte[] expectedHash;
        try {
            iterations = Integer.parseInt(parts[1]);
            salt = Base64.getDecoder().decode(parts[2]);
            expectedHash = Base64.getDecoder().decode(parts[3]);
        } catch (IllegalArgumentException error) {
            return false;
        }
        if (iterations < MINIMUM_ITERATIONS || iterations > MAXIMUM_ITERATIONS
                || salt.length != SALT_LENGTH_BYTES || expectedHash.length != HASH_LENGTH_BYTES) {
            return false;
        }

        byte[] actualHash = derive(password, salt, iterations);
        return MessageDigest.isEqual(expectedHash, actualHash);
    }

    private byte[] derive(String password, byte[] salt, int iterations) {
        PBEKeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_LENGTH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(keySpec).getEncoded();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Unable to calculate password hash", error);
        } finally {
            keySpec.clearPassword();
        }
    }
}
