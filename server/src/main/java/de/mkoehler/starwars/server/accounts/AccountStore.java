package de.mkoehler.starwars.server.accounts;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * File-backed store of every {@link PlayerAccount} (design.md 3.6): a single
 * JSON file mapping login name to account.
 * <p>
 * Every mutation ({@link #login}, {@link #addXp}) only ever touches the
 * in-memory {@link #accountsByLogin} map, under {@code synchronized} - no
 * disk I/O happens on the calling thread. A background task instead flushes
 * the accumulated state to disk on a fixed ~{@value #FLUSH_INTERVAL_SECONDS}
 * -second cadence (skipped entirely if nothing changed since the last
 * flush), and periodically writes a rolling, retention-bounded backup
 * alongside it (see {@link #FLUSHES_PER_BACKUP}/{@link #BACKUPS_TO_KEEP}) -
 * so a kill landing mid-fight, or a login, never blocks on disk, and a bad
 * state still has recent snapshots to roll back to. Only the brief
 * "copy the current state" step needs the lock; the actual write happens
 * off it, entirely on the background thread. A JVM shutdown hook
 * ({@link #close()}) flushes once more, synchronously, so a normal
 * stop/restart (SIGTERM) doesn't lose whatever changed since the last
 * scheduled flush - only a hard kill or crash can still do that, an
 * accepted trade-off of not writing through on every change.
 * <p>
 * Implements design.md 3.6's account creation flow: {@link #login} with a
 * login name that doesn't exist yet auto-creates the account from the
 * supplied password/display name; an existing login requires the password
 * to match, otherwise the attempt is rejected. Deliberately has no
 * dependency on libGDX (plain {@link Path} I/O), so it's directly
 * unit-testable without a running application.
 */
public class AccountStore {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final TypeReference<LinkedHashMap<String, PlayerAccount>> ACCOUNTS_TYPE = new TypeReference<>() {
    };
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** How often the background task flushes accumulated changes to disk. */
    private static final long FLUSH_INTERVAL_SECONDS = 60;
    /** One backup every this many flushes - roughly every 30 minutes, at the default flush interval. */
    private static final int FLUSHES_PER_BACKUP = 30;
    /** Rolling backups kept before the oldest is pruned - roughly a day's worth, at the default cadence. */
    private static final int BACKUPS_TO_KEEP = 48;

    private final Path storageFile;
    private final Path backupDir;
    private final Map<String, PlayerAccount> accountsByLogin;
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final AtomicInteger flushesSinceLastBackup = new AtomicInteger(0);
    private final ScheduledExecutorService scheduler;
    private final Thread shutdownHook;

    /**
     * Opens (or, if it doesn't exist yet, prepares to create on first flush)
     * the account store backed by the given file, and starts its background
     * flush task.
     *
     * @param storageFile the JSON file to load from / persist to
     */
    public AccountStore(Path storageFile) {
        this.storageFile = storageFile;
        this.backupDir = storageFile.resolveSibling("backups");
        this.accountsByLogin = load(storageFile);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "account-store-flush");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::flushAndMaybeBackup,
            FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
        // A plain JVM shutdown hook, not a libGDX lifecycle callback - this class has no libGDX
        // dependency, and a hook here is a stronger guarantee anyway: it fires for any normal
        // JVM exit (including SIGTERM, what `docker stop` sends), independent of whether the
        // surrounding application's own shutdown path reliably reaches this class.
        this.shutdownHook = new Thread(this::close, "account-store-shutdown-flush");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    private static Map<String, PlayerAccount> load(Path storageFile) {
        if (!Files.exists(storageFile)) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(storageFile.toFile(), ACCOUNTS_TYPE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read account store at " + storageFile, e);
        }
    }

    /**
     * Looks up an account by login name, without authenticating.
     *
     * @param login the login name to look up
     * @return the account, if one exists
     */
    public synchronized Optional<PlayerAccount> findByLogin(String login) {
        return Optional.ofNullable(accountsByLogin.get(login));
    }

    /**
     * Authenticates (or, for a new login, creates) an account - design.md
     * 3.6's account creation flow. Synchronized against concurrent logins
     * (KryoNet may invoke this from its network thread for multiple
     * simultaneously-connecting clients) so two connections racing on the
     * same brand-new login can't both "win" account creation.
     * <p>
     * On every successful login (new or existing), the supplied display
     * name is (re)written onto the account - a player can change how they
     * present in-game simply by typing a different one next time, without a
     * separate "edit profile" flow.
     *
     * @param login       the login name
     * @param password    the plain-text password to create/verify with
     * @param displayName the name to show this player as in-game
     * @return the login result
     */
    public synchronized AuthResult login(String login, String password, String displayName) {
        if (login == null || login.isBlank()) {
            return AuthResult.failure("Login is required.");
        }
        if (password == null || password.isEmpty()) {
            return AuthResult.failure("Password is required.");
        }

        PlayerAccount existing = accountsByLogin.get(login);
        if (existing == null) {
            String salt = PasswordHasher.generateSalt();
            String hash = PasswordHasher.hash(password, salt);
            PlayerAccount created = new PlayerAccount(login, hash, salt, displayName, 0, 0, 0);
            accountsByLogin.put(login, created);
            dirty.set(true);
            return AuthResult.success(created, "Welcome, " + displayName + "! Account created.");
        }

        if (!PasswordHasher.matches(password, existing.getPasswordSalt(), existing.getPasswordHash())) {
            return AuthResult.failure("Incorrect password for '" + login + "'.");
        }
        if (!Objects.equals(existing.getDisplayName(), displayName)) {
            existing.setDisplayName(displayName);
            dirty.set(true);
        }
        return AuthResult.success(existing, "Welcome back, " + displayName + ".");
    }

    /**
     * Adds XP to an existing account's running total (design.md — kill XP).
     * Does nothing if the login doesn't exist - shouldn't happen in
     * practice (only ever called for a player who has already logged in
     * this session), but there's no reason to throw over it either.
     *
     * @param login  the account's login name
     * @param amount the XP to add
     */
    public synchronized void addXp(String login, int amount) {
        PlayerAccount account = accountsByLogin.get(login);
        if (account == null) {
            return;
        }
        account.setXp(account.getXp() + amount);
        dirty.set(true);
    }

    /**
     * Increments an existing account's lifetime kill count by one
     * (design.md 2.11's addendum - kills/deaths are persisted the same way
     * as XP, specifically so they survive a combat death, which
     * disconnects the client). Does nothing if the login doesn't exist,
     * same reasoning as {@link #addXp}.
     *
     * @param login the killer's account login name
     */
    public synchronized void addKill(String login) {
        PlayerAccount account = accountsByLogin.get(login);
        if (account == null) {
            return;
        }
        account.setKills(account.getKills() + 1);
        dirty.set(true);
    }

    /**
     * Increments an existing account's lifetime death count by one, see
     * {@link #addKill}.
     *
     * @param login the victim's account login name
     */
    public synchronized void addDeath(String login) {
        PlayerAccount account = accountsByLogin.get(login);
        if (account == null) {
            return;
        }
        account.setDeaths(account.getDeaths() + 1);
        dirty.set(true);
    }

    /**
     * Forces an immediate, synchronous write of the current in-memory state
     * to disk, bypassing the ~{@value #FLUSH_INTERVAL_SECONDS}-second
     * background cadence - a no-op if nothing has changed since the last
     * flush. Used on shutdown and directly by tests, so tests don't need to
     * wait on wall-clock timing to observe a write.
     */
    public void flush() {
        Map<String, PlayerAccount> snapshot = snapshot();
        if (snapshot != null) {
            writeToDisk(storageFile, snapshot);
        }
    }

    /**
     * The background task's actual work: flush, then - every
     * {@value #FLUSHES_PER_BACKUP} flushes - a rolling backup. Guards
     * against a single failed run (e.g. a transient disk error) silently
     * cancelling every future scheduled run, which is what an uncaught
     * exception out of a {@link ScheduledExecutorService} task does.
     */
    private void flushAndMaybeBackup() {
        try {
            Map<String, PlayerAccount> snapshot = snapshot();
            if (snapshot == null) {
                return;
            }
            writeToDisk(storageFile, snapshot);
            if (flushesSinceLastBackup.incrementAndGet() >= FLUSHES_PER_BACKUP) {
                flushesSinceLastBackup.set(0);
                writeBackup(snapshot);
            }
        } catch (RuntimeException e) {
            System.err.println("AccountStore: background flush failed, will retry next cycle: " + e.getMessage());
        }
    }

    /**
     * Takes a deep, consistent copy of every account under the lock - the
     * only part of a flush that needs it, since {@link PlayerAccount} is a
     * mutable bean and the live objects could otherwise be mutated by a
     * concurrent {@link #login}/{@link #addXp} while this thread serializes
     * them - or returns {@code null} if nothing has changed since the
     * previous flush, so an idle server doesn't rewrite an unchanged file
     * forever.
     *
     * @return an independent snapshot of every account, or {@code null}
     */
    private synchronized Map<String, PlayerAccount> snapshot() {
        if (!dirty.compareAndSet(true, false)) {
            return null;
        }
        Map<String, PlayerAccount> copy = new LinkedHashMap<>();
        for (Map.Entry<String, PlayerAccount> entry : accountsByLogin.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().copy());
        }
        return copy;
    }

    private static void writeToDisk(Path file, Map<String, PlayerAccount> data) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            MAPPER.writeValue(file.toFile(), data);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write account store at " + file, e);
        }
    }

    private void writeBackup(Map<String, PlayerAccount> snapshot) {
        try {
            Files.createDirectories(backupDir);
            String timestamp = BACKUP_TIMESTAMP.format(LocalDateTime.now());
            writeToDisk(backupDir.resolve("accounts-" + timestamp + ".json"), snapshot);
            pruneOldBackups();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create account store backup in " + backupDir, e);
        }
    }

    /**
     * Deletes the oldest backups beyond {@value #BACKUPS_TO_KEEP} -
     * filenames sort chronologically. Package-private, not private: lets
     * {@code AccountStoreTest} exercise the retention rule directly against
     * a seeded set of fake backup files, rather than needing to wait real
     * wall-clock time for {@link #FLUSHES_PER_BACKUP} real backups to pile
     * up naturally.
     */
    void pruneOldBackups() throws IOException {
        try (Stream<Path> files = Files.list(backupDir)) {
            List<Path> backups = files
                .filter(path -> path.getFileName().toString().startsWith("accounts-"))
                .sorted()
                .toList();
            for (int i = 0; i < backups.size() - BACKUPS_TO_KEEP; i++) {
                Files.deleteIfExists(backups.get(i));
            }
        }
    }

    /**
     * Stops the background flush task and performs one final, synchronous
     * flush - registered as a JVM shutdown hook in the constructor, and
     * safe to call again directly (e.g. from a test's teardown) without
     * double-flushing, since {@link #flush()} is itself a no-op once
     * nothing is left dirty.
     */
    public void close() {
        scheduler.shutdown();
        flush();
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // Already mid-shutdown (this being called AS the hook, or another one) - can't
            // unregister a hook at that point, and there's no need to.
        }
    }
}
