package de.mkoehler.starwars.net.messages;

/**
 * One connected player's row in the {@link ScoreboardMessage} overlay: their
 * display name and total account XP/kills/deaths - all three are lifetime
 * totals, persisted on the account (design.md 2.11's addendum; kills/deaths
 * originally reset on every death since a combat death disconnects the
 * client, before they were moved from in-memory per-connection state to the
 * account, the same way XP already worked).
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
     * @param kills       the player's total accumulated account kills
     * @param deaths      the player's total accumulated account deaths
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
