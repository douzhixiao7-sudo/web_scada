package com.example.scada.auth;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.springframework.stereotype.Service;

@Service
public class PasswordService {
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    public String hash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, ITERATIONS, KEY_LENGTH);
        return "pbkdf2$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(hash);
    }

    public boolean matches(String password, String stored) {
        if (password == null || stored == null || !stored.startsWith("pbkdf2$")) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4) {
            return false;
        }
        int iterations = Integer.parseInt(parts[1]);
        byte[] salt = Base64.getDecoder().decode(parts[2].getBytes(StandardCharsets.UTF_8));
        byte[] expected = Base64.getDecoder().decode(parts[3].getBytes(StandardCharsets.UTF_8));
        byte[] actual = pbkdf2(password, salt, iterations, expected.length * 8);
        return constantTimeEquals(expected, actual);
    }

    private byte[] pbkdf2(String password, byte[] salt, int iterations, int keyLength) {
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, keyLength);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("Cannot hash password", ex);
        }
    }

    private boolean constantTimeEquals(byte[] expected, byte[] actual) {
        if (expected.length != actual.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < expected.length; i++) {
            diff |= expected[i] ^ actual[i];
        }
        return diff == 0;
    }
}
