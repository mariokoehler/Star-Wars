package de.mkoehler.starwars.net.messages;

/**
 * Broadcast by the server on every simulation tick: the position/orientation
 * of every currently-connected player's ship, and every currently-alive
 * projectile. Other players' ships and every projectile (including the
 * local player's own shots) are rendered purely from these snapshots, with
 * no client-side prediction (design.md 3.5) — the local player's own ship is
 * the sole exception, predicted locally and reconciled against this.
 */
public class WorldSnapshotMessage {

    private ShipState[] ships;
    private ProjectileState[] projectiles;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public WorldSnapshotMessage() {
    }

    /**
     * Creates a world snapshot.
     *
     * @param ships       every currently-connected player's ship state
     * @param projectiles every currently-alive projectile's state
     */
    public WorldSnapshotMessage(ShipState[] ships, ProjectileState[] projectiles) {
        this.ships = ships;
        this.projectiles = projectiles;
    }

    /**
     * Returns every currently-connected player's ship state.
     *
     * @return the ship states in this snapshot
     */
    public ShipState[] getShips() {
        return ships;
    }

    /**
     * Returns every currently-alive projectile's state.
     *
     * @return the projectile states in this snapshot
     */
    public ProjectileState[] getProjectiles() {
        return projectiles;
    }
}
