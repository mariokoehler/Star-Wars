package de.mkoehler.starwars.server.accounts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountStoreTest {

    @Test
    void newLoginAutoCreatesAccount(@TempDir Path tempDir) {
        AccountStore store = new AccountStore(tempDir.resolve("accounts.json"));

        AuthResult result = store.login("han", "solo123", "Han Solo");

        assertTrue(result.success());
        assertEquals("Han Solo", result.account().getDisplayName());
        assertEquals(0, result.account().getXp());
        assertTrue(store.findByLogin("han").isPresent());
    }

    @Test
    void existingLoginWithCorrectPasswordSucceeds(@TempDir Path tempDir) {
        AccountStore store = new AccountStore(tempDir.resolve("accounts.json"));
        store.login("han", "solo123", "Han Solo");

        AuthResult result = store.login("han", "solo123", "Han Solo");

        assertTrue(result.success());
    }

    @Test
    void existingLoginWithWrongPasswordIsRejected(@TempDir Path tempDir) {
        AccountStore store = new AccountStore(tempDir.resolve("accounts.json"));
        store.login("han", "solo123", "Han Solo");

        AuthResult result = store.login("han", "wrong password", "Han Solo");

        assertFalse(result.success());
        assertTrue(store.findByLogin("han").isPresent());
    }

    @Test
    void loginUpdatesDisplayNameOnChange(@TempDir Path tempDir) {
        AccountStore store = new AccountStore(tempDir.resolve("accounts.json"));
        store.login("han", "solo123", "Han Solo");

        AuthResult result = store.login("han", "solo123", "Captain Solo");

        assertTrue(result.success());
        assertEquals("Captain Solo", result.account().getDisplayName());
        assertEquals("Captain Solo", store.findByLogin("han").orElseThrow().getDisplayName());
    }

    @Test
    void blankLoginIsRejected(@TempDir Path tempDir) {
        AccountStore store = new AccountStore(tempDir.resolve("accounts.json"));

        assertFalse(store.login("", "password", "Nobody").success());
        assertFalse(store.login(null, "password", "Nobody").success());
    }

    @Test
    void emptyPasswordIsRejected(@TempDir Path tempDir) {
        AccountStore store = new AccountStore(tempDir.resolve("accounts.json"));

        assertFalse(store.login("han", "", "Han Solo").success());
    }

    @Test
    void accountSurvivesReloadFromDisk(@TempDir Path tempDir) {
        Path file = tempDir.resolve("accounts.json");
        AccountStore first = new AccountStore(file);
        first.login("han", "solo123", "Han Solo");

        AccountStore reloaded = new AccountStore(file);
        AuthResult result = reloaded.login("han", "solo123", "Han Solo");

        assertTrue(result.success());
        assertTrue(reloaded.findByLogin("han").isPresent());
    }

    @Test
    void reloadRejectsWrongPasswordForPersistedAccount(@TempDir Path tempDir) {
        Path file = tempDir.resolve("accounts.json");
        new AccountStore(file).login("han", "solo123", "Han Solo");

        AccountStore reloaded = new AccountStore(file);

        assertFalse(reloaded.login("han", "wrong password", "Han Solo").success());
    }

    @Test
    void noStoreFileYetMeansNoAccounts(@TempDir Path tempDir) {
        AccountStore store = new AccountStore(tempDir.resolve("does-not-exist-yet.json"));

        assertTrue(store.findByLogin("anyone").isEmpty());
    }
}
