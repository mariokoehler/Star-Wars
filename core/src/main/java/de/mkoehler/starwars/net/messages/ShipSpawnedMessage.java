package de.mkoehler.starwars.net.messages;

/**
 * Sent by the server to a client whenever its ship (re)spawns — right after
 * handshake acceptance for the initial spawn, and again after a respawn
 * delay following a {@link ShipDestroyedMessage} for that same player. Both
 * cases are handled identically by the receiving client: assign/reassign the
 * player id (only actually changes on initial spawn) and (re)create the ship
 * at the given position.
 */
public class ShipSpawnedMessage {

    private int playerId;
    private float spawnX;
    private float spawnY;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public ShipSpawnedMessage() {
    }

    /**
     * Creates a ship-spawned message.
     *
     * @param playerId the id of the player this ship belongs to
     * @param spawnX   the ship's spawn position, in meters
     * @param spawnY   the ship's spawn position, in meters
     */
    public ShipSpawnedMessage(int playerId, float spawnX, float spawnY) {
        this.playerId = playerId;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
    }

    /**
     * Returns the id of the player this ship belongs to.
     *
     * @return the owning player's id
     */
    public int getPlayerId() {
        return playerId;
    }

    /**
     * Returns the ship's spawn X position.
     *
     * @return the spawn position, in meters
     */
    public float getSpawnX() {
        return spawnX;
    }

    /**
     * Returns the ship's spawn Y position.
     *
     * @return the spawn position, in meters
     */
    public float getSpawnY() {
        return spawnY;
    }
}
