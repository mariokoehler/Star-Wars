package de.mkoehler.starwars.server.accounts;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * File-backed store of every {@link PlayerAccount} (design.md 3.6): a single
 * JSON file mapping login name to account, rewritten in full on every
 * mutation - simple to reason about and easy to back up/inspect by hand at
 * this project's scale, revisit only if that stops being true.
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

    private final Path storageFile;
    private final Map<String, PlayerAccount> accountsByLogin;

    /**
     * Opens (or, if it doesn't exist yet, prepares to create on first write)
     * the account store backed by the given file.
     *
     * @param storageFile the JSON file to load from / persist to
     */
    public AccountStore(Path storageFile) {
        this.storageFile = storageFile;
        this.accountsByLogin = load(storageFile);
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

    private synchronized void save() {
        try {
            if (storageFile.getParent() != null) {
                Files.createDirectories(storageFile.getParent());
            }
            MAPPER.writeValue(storageFile.toFile(), accountsByLogin);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write account store at " + storageFile, e);
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
            PlayerAccount created = new PlayerAccount(login, hash, salt, displayName, 0);
            accountsByLogin.put(login, created);
            save();
            return AuthResult.success(created, "Welcome, " + displayName + "! Account created.");
        }

        if (!PasswordHasher.matches(password, existing.getPasswordSalt(), existing.getPasswordHash())) {
            return AuthResult.failure("Incorrect password for '" + login + "'.");
        }
        if (!Objects.equals(existing.getDisplayName(), displayName)) {
            existing.setDisplayName(displayName);
            save();
        }
        return AuthResult.success(existing, "Welcome back, " + displayName + ".");
    }
}
