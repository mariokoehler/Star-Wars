package de.mkoehler.starwars.net.messages;

/**
 * One connected player's row in the {@link ScoreboardMessage} overlay: their
 * display name, total account XP, and this-session-only kill/death counts
 * (design.md 2.11 — session stats are never persisted, unlike XP).
 */
public class PlayerScoreEntry {

    private int playerId;
    private String displayName;
    private int xp;
    private int kills;
    private int deaths;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public PlayerScoreEntry() {
    }

    /**
     * Creates a player score entry.
     *
     * @param playerId    the player's id
     * @param displayName the name to show for this player
     * @param xp          the player's total accumulated account XP
     * @param kills       this session's kill count for the player
     * @param deaths      this session's death count for the player
     */
    public PlayerScoreEntry(int playerId, String displayName, int xp, int kills, int deaths) {
        this.playerId = playerId;
        this.displayName = displayName;
        this.xp = xp;
        this.kills = kills;
        this.deaths = deaths;
    }

    public int getPlayerId() {
        return playerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getXp() {
        return xp;
    }

    public int getKills() {
        return kills;
    }

    public int getDeaths() {
        return deaths;
    }
}
