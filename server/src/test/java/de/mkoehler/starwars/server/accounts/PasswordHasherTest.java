package de.mkoehler.starwars.server.accounts;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    @Test
    void matchingPasswordAndSaltVerifiesTrue() {
        String salt = PasswordHasher.generateSalt();
        String hash = PasswordHasher.hash("correct horse", salt);

        assertTrue(PasswordHasher.matches("correct horse", salt, hash));
    }

    @Test
    void wrongPasswordVerifiesFalse() {
        String salt = PasswordHasher.generateSalt();
        String hash = PasswordHasher.hash("correct horse", salt);

        assertFalse(PasswordHasher.matches("wrong password", salt, hash));
    }

    @Test
    void sameSaltDifferentPasswordsProduceDifferentHashes() {
        String salt = PasswordHasher.generateSalt();

        assertNotEquals(PasswordHasher.hash("password one", salt), PasswordHasher.hash("password two", salt));
    }

    @Test
    void samePasswordDifferentSaltsProduceDifferentHashes() {
        String saltA = PasswordHasher.generateSalt();
        String saltB = PasswordHasher.generateSalt();

        assertNotEquals(PasswordHasher.hash("same password", saltA), PasswordHasher.hash("same password", saltB));
    }

    @Test
    void generateSaltProducesDistinctValues() {
        assertNotEquals(PasswordHasher.generateSalt(), PasswordHasher.generateSalt());
    }

    @Test
    void hashIsDeterministicForSamePasswordAndSalt() {
        String salt = PasswordHasher.generateSalt();

        assertEquals(PasswordHasher.hash("password", salt), PasswordHasher.hash("password", salt));
    }
}
