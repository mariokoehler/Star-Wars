package de.mkoehler.starwars.net.messages;

/**
 * Broadcast by the server when a ship's health reaches zero. The destroyed
 * player's own client should stop rendering/controlling that ship until a
 * subsequent {@link ShipSpawnedMessage} respawns it; every other client
 * should just stop drawing it.
 */
public class ShipDestroyedMessage {

    private int playerId;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public ShipDestroyedMessage() {
    }

    /**
     * Creates a ship-destroyed message.
     *
     * @param playerId the id of the player whose ship was destroyed
     */
    public ShipDestroyedMessage(int playerId) {
        this.playerId = playerId;
    }

    /**
     * Returns the id of the player whose ship was destroyed.
     *
     * @return the destroyed ship's owning player id
     */
    public int getPlayerId() {
        return playerId;
    }
}
