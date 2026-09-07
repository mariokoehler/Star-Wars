package de.mkoehler.starwars.server.accounts;

import de.mkoehler.starwars.sim.ShipType;

import java.util.HashSet;
import java.util.Set;

/**
 * A persisted player account (design.md 3.6): a unique login, a hashed and
 * salted password, a separate (not-necessarily-unique) display name,
 * accumulated XP/kills/deaths, and the set of ship types unlocked (design.md
 * - ship unlocks).
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
 * <p>
 * {@link #unlockedShips} never contains {@link ShipType#SNOWSPEEDER} - it's
 * always flyable regardless of what's in the set (see
 * {@link de.mkoehler.starwars.sim.ShipUnlocks#isUnlocked}), so it never
 * needs to be recorded here at all. Every constructor/setter that touches
 * this field defensively copies it into a fresh {@link HashSet}, since
 * unlike every other field here it's mutable - {@link #copy()} in
 * particular relies on this to give {@link AccountStore}'s background flush
 * thread a genuinely independent snapshot, not a reference into the live,
 * concurrently-mutable set.
 */
public class PlayerAccount {

    private String login;
    private String passwordHash;
    private String passwordSalt;
    private String displayName;
    private int xp;
    private int kills;
    private int deaths;
    private Set<ShipType> unlockedShips = new HashSet<>();

    /**
     * No-arg constructor required by Jackson for deserialization.
     */
    public PlayerAccount() {
    }

    /**
     * Creates a player account.
     *
     * @param login         the unique login name
     * @param passwordHash  the salted password hash (never the raw password)
     * @param passwordSalt  the per-account random salt the hash was computed with
     * @param displayName   the name other players see in-game
     * @param xp            total accumulated XP
     * @param kills         total accumulated kills
     * @param deaths        total accumulated deaths
     * @param unlockedShips the ship types unlocked so far (copied defensively, see the class Javadoc)
     */
    public PlayerAccount(String login, String passwordHash, String passwordSalt, String displayName, int xp,
                          int kills, int deaths, Set<ShipType> unlockedShips) {
        this.login = login;
        this.passwordHash = passwordHash;
        this.passwordSalt = passwordSalt;
        this.displayName = displayName;
        this.xp = xp;
        this.kills = kills;
        this.deaths = deaths;
        this.unlockedShips = new HashSet<>(unlockedShips);
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

    public Set<ShipType> getUnlockedShips() {
        return unlockedShips;
    }

    public void setUnlockedShips(Set<ShipType> unlockedShips) {
        this.unlockedShips = new HashSet<>(unlockedShips);
    }

    /**
     * Adds one ship type to this account's unlocked set - a no-op if it's
     * already there. Callers are responsible for having already validated
     * the unlock is affordable ({@link de.mkoehler.starwars.sim.ShipUnlocks#availableXp});
     * this method itself just mutates the set unconditionally.
     *
     * @param shipType the ship type to unlock
     */
    public void unlockShip(ShipType shipType) {
        unlockedShips.add(shipType);
    }

    /**
     * Returns an independent copy of this account - safe to hand to
     * {@link AccountStore}'s background flush thread without racing further
     * mutations made to the original. Every field except
     * {@link #unlockedShips} is an immutable type, so a plain field-by-field
     * copy already handles those; {@link #unlockedShips} itself is deep-
     * copied by the constructor it's passed through (see the class Javadoc).
     *
     * @return an independent copy of this account
     */
    public PlayerAccount copy() {
        return new PlayerAccount(login, passwordHash, passwordSalt, displayName, xp, kills, deaths, unlockedShips);
    }
}
