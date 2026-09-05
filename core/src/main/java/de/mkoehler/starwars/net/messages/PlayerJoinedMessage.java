package de.mkoehler.starwars.net.messages;

/**
 * Sent by the server to a client right after its handshake is accepted,
 * assigning it a player id and telling it where its ship spawned.
 */
public class PlayerJoinedMessage {

    private int playerId;
    private float spawnX;
    private float spawnY;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public PlayerJoinedMessage() {
    }

    /**
     * Creates a player-joined message.
     *
     * @param playerId the id assigned to the receiving client's player/ship
     * @param spawnX   the ship's spawn position, in meters
     * @param spawnY   the ship's spawn position, in meters
     */
    public PlayerJoinedMessage(int playerId, float spawnX, float spawnY) {
        this.playerId = playerId;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
    }

    /**
     * Returns the id assigned to the receiving client's player/ship.
     *
     * @return the assigned player id
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
