package de.mkoehler.starwars.server.accounts;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Pure password hashing/verification for {@link PlayerAccount} (design.md
 * 3.6) - SHA-256 over the password concatenated with a per-account random
 * salt. Good enough for this project's threat model (a hobby dedicated
 * server, not a target worth pulling in bcrypt/Argon2 for), but the raw
 * password must never be stored or logged regardless.
 * <p>
 * Stateless and static; every value is hex-encoded so it round-trips cleanly
 * through {@link PlayerAccount}'s plain Jackson-serialized {@code String}
 * fields.
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "SHA-256";
    private static final int SALT_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    /**
     * Generates a new random salt for a freshly-created account.
     *
     * @return a random salt, hex-encoded
     */
    public static String generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return HexFormat.of().formatHex(salt);
    }

    /**
     * Hashes a password with the given salt.
     *
     * @param password the plain-text password
     * @param saltHex  the salt to hash it with, hex-encoded (see {@link #generateSalt()})
     * @return the resulting hash, hex-encoded
     */
    public static String hash(String password, String saltHex) {
        MessageDigest digest = newDigest();
        digest.update(HexFormat.of().parseHex(saltHex));
        digest.update(password.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Checks a plain-text password against a previously computed hash,
     * using a constant-time comparison ({@link MessageDigest#isEqual}) so
     * timing can't leak how much of the hash matched.
     *
     * @param password         the plain-text password to check
     * @param saltHex          the salt the expected hash was computed with
     * @param expectedHashHex  the expected hash, hex-encoded
     * @return {@code true} if the password hashes to the expected value
     */
    public static boolean matches(String password, String saltHex, String expectedHashHex) {
        byte[] actual = HexFormat.of().parseHex(hash(password, saltHex));
        byte[] expected = HexFormat.of().parseHex(expectedHashHex);
        return MessageDigest.isEqual(actual, expected);
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandatory JCA algorithm on every conforming JVM - this can't happen.
            throw new IllegalStateException(ALGORITHM + " unexpectedly unavailable", e);
        }
    }
}
