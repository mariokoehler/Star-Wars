package de.mkoehler.starwars.net.messages;

/**
 * Broadcast by the server when a player disconnects, so clients can remove
 * that player's ship immediately instead of it merely going stale.
 */
public class PlayerLeftMessage {

    private int playerId;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public PlayerLeftMessage() {
    }

    /**
     * Creates a player-left message.
     *
     * @param playerId the id of the player who disconnected
     */
    public PlayerLeftMessage(int playerId) {
        this.playerId = playerId;
    }

    /**
     * Returns the id of the player who disconnected.
     *
     * @return the disconnected player's id
     */
    public int getPlayerId() {
        return playerId;
    }
}
