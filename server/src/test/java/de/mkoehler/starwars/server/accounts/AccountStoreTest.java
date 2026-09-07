package de.mkoehler.starwars.server.accounts;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link AccountStore}, including its deferred-write behavior:
 * {@link AccountStore#login}/{@link AccountStore#addXp} only ever mutate the
 * in-memory state, so a persisted-to-disk assertion needs an explicit
 * {@link AccountStore#flush()} first, not just a mutation - see
 * {@link #mutationsAreNotOnDiskUntilFlushed}.
 */
class AccountStoreTest {

    // Every store this test class creates, closed in tearDown() - AccountStore starts a
    // background scheduler thread and registers a JVM shutdown hook per instance, both of
    // which would otherwise leak across the whole test run.
    private final List<AccountStore> stores = new ArrayList<>();

    private AccountStore createStore(Path file) {
        AccountStore store = new AccountStore(file);
        stores.add(store);
        return store;
    }

    @AfterEach
    void closeStores() {
        stores.forEach(AccountStore::close);
        stores.clear();
    }

    @Test
    void newLoginAutoCreatesAccount(@TempDir Path tempDir) {
        AccountStore store = createStore(tempDir.resolve("accounts.json"));

        AuthResult result = store.login("han", "solo123", "Han Solo");

        assertTrue(result.success());
        assertEquals("Han Solo", result.account().getDisplayName());
        assertEquals(0, result.account().getXp());
        assertTrue(store.findByLogin("han").isPresent());
    }

    @Test
    void existingLoginWithCorrectPasswordSucceeds(@TempDir Path tempDir) {
        AccountStore store = createStore(tempDir.resolve("accounts.json"));
        store.login("han", "solo123", "Han Solo");

        AuthResult result = store.login("han", "solo123", "Han Solo");

        assertTrue(result.success());
    }

    @Test
    void existingLoginWithWrongPasswordIsRejected(@TempDir Path tempDir) {
        AccountStore store = createStore(tempDir.resolve("accounts.json"));
        store.login("han", "solo123", "Han Solo");

        AuthResult result = store.login("han", "wrong password", "Han Solo");

        assertFalse(result.success());
        assertTrue(store.findByLogin("han").isPresent());
    }

    @Test
    void loginUpdatesDisplayNameOnChange(@TempDir Path tempDir) {
        AccountStore store = createStore(tempDir.resolve("accounts.json"));
        store.login("han", "solo123", "Han Solo");

        AuthResult result = store.login("han", "solo123", "Captain Solo");

        assertTrue(result.success());
        assertEquals("Captain Solo", result.account().getDisplayName());
        assertEquals("Captain Solo", store.findByLogin("han").orElseThrow().getDisplayName());
    }

    @Test
    void blankLoginIsRejected(@TempDir Path tempDir) {
        AccountStore store = createStore(tempDir.resolve("accounts.json"));

        assertFalse(store.login("", "password", "Nobody").success());
        assertFalse(store.login(null, "password", "Nobody").success());
    }

    @Test
    void emptyPasswordIsRejected(@TempDir Path tempDir) {
        AccountStore store = createStore(tempDir.resolve("accounts.json"));

        assertFalse(store.login("han", "", "Han Solo").success());
    }

    @Test
    void accountSurvivesReloadFromDisk(@TempDir Path tempDir) {
        Path file = tempDir.resolve("accounts.json");
        AccountStore first = createStore(file);
        first.login("han", "solo123", "Han Solo");
        first.flush();

        AccountStore reloaded = createStore(file);
        AuthResult result = reloaded.login("han", "solo123", "Han Solo");

        assertTrue(result.success());
        assertTrue(reloaded.findByLogin("han").isPresent());
    }

    @Test
    void reloadRejectsWrongPasswordForPersistedAccount(@TempDir Path tempDir) {
        Path file = tempDir.resolve("accounts.json");
        AccountStore first = createStore(file);
        first.login("han", "solo123", "Han Solo");
        first.flush();

        AccountStore reloaded = createStore(file);

        assertFalse(reloaded.login("han", "wrong password", "Han Solo").success());
    }

    @Test
    void noStoreFileYetMeansNoAccounts(@TempDir Path tempDir) {
        AccountStore store = createStore(tempDir.resolve("does-not-exist-yet.json"));

        assertTrue(store.findByLogin("anyone").isEmpty());
    }

    @Test
    void addXpIncreasesTheRunningTotal(@TempDir Path tempDir) {
        AccountStore store = createStore(tempDir.resolve("accounts.json"));
        store.login("han", "solo123", "Han Solo");

        store.addXp("han", 160);
        store.addXp("han", 3);

        assertEquals(163, store.findByLogin("han").orElseThrow().getXp());
    }

    @Test
    void addXpForAnUnknownLoginDoesNothing(@TempDir Path tempDir) {
        AccountStore store = createStore(tempDir.resolve("accounts.json"));

        store.addXp("nobody", 100);

        assertTrue(store.findByLogin("nobody").isEmpty());
    }

    @Test
    void addXpSurvivesReloadFromDisk(@TempDir Path tempDir) {
        Path file = tempDir.resolve("accounts.json");
        AccountStore first = createStore(file);
        first.login("han", "solo123", "Han Solo");
        first.addXp("han", 40);
        first.flush();

        AccountStore reloaded = createStore(file);

        assertEquals(40, reloaded.findByLogin("han").orElseThrow().getXp());
    }

    @Test
    void addKillIncreasesTheRunningTotal(@TempDir Path tempDir) {
        AccountStore store = createStore(tempDir.resolve("accounts.json"));
        store.login("han", "solo123", "Han Solo");

        store.addKill("han");
        store.addKill("han");

        assertEquals(2, store.findByLogin("han").orElseThrow().getKills());
    }

    @Test
    void addDeathIncreasesTheRunningTotal(@TempDir Path tempDir) {
        AccountStore store = createStore(tempDir.resolve("accounts.json"));
        store.login("han", "solo123", "Han Solo");

        store.addDeath("han");

        assertEquals(1, store.findByLogin("han").orElseThrow().getDeaths());
    }

    @Test
    void addKillAndAddDeathForAnUnknownLoginDoNothing(@TempDir Path tempDir) {
        AccountStore store = createStore(tempDir.resolve("accounts.json"));

        store.addKill("nobody");
        store.addDeath("nobody");

        assertTrue(store.findByLogin("nobody").isEmpty());
    }

    @Test
    void killsAndDeathsSurviveReloadFromDisk(@TempDir Path tempDir) {
        Path file = tempDir.resolve("accounts.json");
        AccountStore first = createStore(file);
        first.login("han", "solo123", "Han Solo");
        first.addKill("han");
        first.addKill("han");
        first.addDeath("han");
        first.flush();

        AccountStore reloaded = createStore(file);

        PlayerAccount account = reloaded.findByLogin("han").orElseThrow();
        assertEquals(2, account.getKills());
        assertEquals(1, account.getDeaths());
    }

    @Test
    void mutationsAreNotOnDiskUntilFlushed(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("accounts.json");
        AccountStore store = createStore(file);

        store.login("han", "solo123", "Han Solo");
        assertFalse(Files.exists(file), "login() must not write through to disk on its own");

        store.flush();
        assertTrue(Files.exists(file), "flush() must write out whatever changed since the last flush");
    }

    @Test
    void flushWithNothingChangedDoesNotRewriteTheFile(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("accounts.json");
        AccountStore store = createStore(file);
        store.login("han", "solo123", "Han Solo");
        store.flush();
        long firstWriteTime = Files.getLastModifiedTime(file).toMillis();

        store.flush(); // nothing changed since the flush above

        assertEquals(firstWriteTime, Files.getLastModifiedTime(file).toMillis());
    }

    @Test
    void closeFlushesOnceMoreAndIsSafeToCallTwice(@TempDir Path tempDir) {
        Path file = tempDir.resolve("accounts.json");
        AccountStore store = createStore(file);
        store.login("han", "solo123", "Han Solo");

        store.close();
        store.close(); // must not throw, e.g. from double-removing the shutdown hook

        AccountStore reloaded = createStore(file);
        assertTrue(reloaded.findByLogin("han").isPresent());
    }

    @Test
    void pruneOldBackupsKeepsOnlyTheNewestOnes(@TempDir Path tempDir) throws IOException {
        AccountStore store = createStore(tempDir.resolve("accounts.json"));
        Path backupDir = tempDir.resolve("backups");
        Files.createDirectories(backupDir);
        // Zero-padded so lexicographic sort matches intended chronological order, same as the
        // real yyyyMMdd-HHmmss timestamps do - named this way (rather than actually flushing
        // real backups 30 minutes apart) so the test doesn't depend on real wall-clock time.
        for (int i = 0; i < 60; i++) {
            Files.writeString(backupDir.resolve(String.format("accounts-%03d.json", i)), "{}");
        }

        store.pruneOldBackups();

        try (Stream<Path> remaining = Files.list(backupDir)) {
            List<String> names = remaining.map(path -> path.getFileName().toString()).sorted().toList();
            assertEquals(48, names.size());
            assertEquals("accounts-012.json", names.get(0), "the oldest 12 of 60 should have been pruned");
            assertEquals("accounts-059.json", names.get(names.size() - 1));
        }
    }
}
