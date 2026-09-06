package de.mkoehler.starwars.server.accounts;

/**
 * A persisted player account (design.md 3.6): a unique login, a hashed and
 * salted password, a separate (not-necessarily-unique) display name, and
 * accumulated XP.
 * <p>
 * Plain mutable bean (public no-arg constructor, getters and setters) so
 * Jackson can (de)serialize it with no extra configuration, same convention
 * as this project's other Jackson beans (e.g. {@code ShipTypeConfig}).
 * <p>
 * The raw password is never stored - only {@link #getPasswordHash()} (a
 * SHA-256 digest of the password concatenated with {@link #getPasswordSalt()},
 * see {@link PasswordHasher}) ever reaches disk.
 */
public class PlayerAccount {

    private String login;
    private String passwordHash;
    private String passwordSalt;
    private String displayName;
    private int xp;

    /**
     * No-arg constructor required by Jackson for deserialization.
     */
    public PlayerAccount() {
    }

    /**
     * Creates a player account.
     *
     * @param login        the unique login name
     * @param passwordHash the salted password hash (never the raw password)
     * @param passwordSalt the per-account random salt the hash was computed with
     * @param displayName  the name other players see in-game
     * @param xp           total accumulated XP
     */
    public PlayerAccount(String login, String passwordHash, String passwordSalt, String displayName, int xp) {
        this.login = login;
        this.passwordHash = passwordHash;
        this.passwordSalt = passwordSalt;
        this.displayName = displayName;
        this.xp = xp;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPasswordSalt() {
        return passwordSalt;
    }

    public void setPasswordSalt(String passwordSalt) {
        this.passwordSalt = passwordSalt;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public int getXp() {
        return xp;
    }

    public void setXp(int xp) {
        this.xp = xp;
    }
}
