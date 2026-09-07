package de.mkoehler.starwars.server.accounts;

/**
 * A persisted player account (design.md 3.6): a unique login, a hashed and
 * salted password, a separate (not-necessarily-unique) display name, and
 * accumulated XP/kills/deaths.
 * <p>
 * Plain mutable bean (public no-arg constructor, getters and setters) so
 * Jackson can (de)serialize it with no extra configuration, same convention
 * as this project's other Jackson beans (e.g. {@code ShipTypeConfig}).
 * <p>
 * The raw password is never stored - only {@link #getPasswordHash()} (a
 * SHA-256 digest of the password concatenated with {@link #getPasswordSalt()},
 * see {@link PasswordHasher}) ever reaches disk.
 * <p>
 * {@link #kills}/{@link #deaths} are lifetime totals (design.md 2.11's
 * addendum) - originally tracked as in-memory, per-connection "session"
 * stats, but that meant they reset every time a player died, since a
 * combat death disconnects the client (design.md 5.1's Death Screen).
 * Persisting them here, the same way {@link #xp} already was, fixed that
 * for free and needed no new infrastructure.
 */
public class PlayerAccount {

    private String login;
    private String passwordHash;
    private String passwordSalt;
    private String displayName;
    private int xp;
    private int kills;
    private int deaths;

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
     * @param kills        total accumulated kills
     * @param deaths       total accumulated deaths
     */
    public PlayerAccount(String login, String passwordHash, String passwordSalt, String displayName, int xp,
                          int kills, int deaths) {
        this.login = login;
        this.passwordHash = passwordHash;
        this.passwordSalt = passwordSalt;
        this.displayName = displayName;
        this.xp = xp;
        this.kills = kills;
        this.deaths = deaths;
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

    public int getKills() {
        return kills;
    }

    public void setKills(int kills) {
        this.kills = kills;
    }

    public int getDeaths() {
        return deaths;
    }

    public void setDeaths(int deaths) {
        this.deaths = deaths;
    }

    /**
     * Returns an independent copy of this account - safe to hand to
     * {@link AccountStore}'s background flush thread without racing further
     * mutations made to the original (every field here is an immutable
     * type, so a plain field-by-field copy is already a full deep copy).
     *
     * @return an independent copy of this account
     */
    public PlayerAccount copy() {
        return new PlayerAccount(login, passwordHash, passwordSalt, displayName, xp, kills, deaths);
    }
}
