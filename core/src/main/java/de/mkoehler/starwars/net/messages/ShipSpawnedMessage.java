package de.mkoehler.starwars.net.messages;

import de.mkoehler.starwars.sim.ShipType;

/**
 * Sent by the server to a client whenever its ship (re)spawns — right after
 * handshake acceptance for the initial spawn, and again after a respawn
 * delay following a {@link ShipDestroyedMessage} for that same player. Both
 * cases are handled identically by the receiving client: assign/reassign the
 * player id (only actually changes on initial spawn) and (re)create the ship
 * at the given position, as the given ship type.
 * <p>
 * The ship type is always the one the player originally requested at
 * handshake (design.md 5.1's Ship Selection screen) — it doesn't change
 * across respawns within a match.
 */
public class ShipSpawnedMessage {

    private int playerId;
    private float spawnX;
    private float spawnY;
    private ShipType shipType;

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
     * @param shipType the ship type spawned
     */
    public ShipSpawnedMessage(int playerId, float spawnX, float spawnY, ShipType shipType) {
        this.playerId = playerId;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.shipType = shipType;
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

    /**
     * Returns the ship type spawned.
     *
     * @return the ship type
     */
    public ShipType getShipType() {
        return shipType;
    }
}
