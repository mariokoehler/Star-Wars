package de.mkoehler.starwars.net.messages;

/**
 * Broadcast by the server on a fixed cadence (design.md 2.11 —
 * {@code GameNetworkServer.SCOREBOARD_BROADCAST_INTERVAL_SECONDS}, not every
 * tick like {@link WorldSnapshotMessage}: this data isn't render-critical, it
 * only needs to be fresh enough for a player holding TAB to see roughly
 * current standings): one {@link PlayerScoreEntry} per currently-connected
 * player (whether or not they've spawned a ship yet), covering every player
 * connected to the server, not just those in weapons range.
 */
public class ScoreboardMessage {

    private PlayerScoreEntry[] entries;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public ScoreboardMessage() {
    }

    /**
     * Creates a scoreboard message.
     *
     * @param entries one entry per currently-connected player
     */
    public ScoreboardMessage(PlayerScoreEntry[] entries) {
        this.entries = entries;
    }

    /**
     * Returns one entry per currently-connected player.
     *
     * @return the score entries in this message
     */
    public PlayerScoreEntry[] getEntries() {
        return entries;
    }
}
