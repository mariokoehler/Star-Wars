package de.mkoehler.starwars.net.messages;

/**
 * Broadcast by the server on every simulation tick: the position/orientation
 * of every currently-connected player's ship, every currently-alive
 * projectile, every currently-active asteroid (design.md — asteroids),
 * every currently-active power-up (design.md — power-ups), and every
 * currently-active mine (design.md — mines). Other players' ships and
 * every projectile/asteroid/power-up/mine (including the local player's
 * own shots) are rendered purely from these snapshots, with no client-side
 * prediction (design.md 3.5) — the local player's own ship is the sole
 * exception, predicted locally and reconciled against this.
 */
public class WorldSnapshotMessage {

    private ShipState[] ships;
    private ProjectileState[] projectiles;
    private AsteroidState[] asteroids;
    private PowerUpState[] powerUps;
    private MineState[] mines;

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
     * @param asteroids   every currently-active asteroid's state
     * @param powerUps    every currently-active power-up's state
     * @param mines       every currently-active mine's state
     */
    public WorldSnapshotMessage(ShipState[] ships, ProjectileState[] projectiles, AsteroidState[] asteroids,
                                 PowerUpState[] powerUps, MineState[] mines) {
        this.ships = ships;
        this.projectiles = projectiles;
        this.asteroids = asteroids;
        this.powerUps = powerUps;
        this.mines = mines;
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

    /**
     * Returns every currently-active asteroid's state.
     *
     * @return the asteroid states in this snapshot
     */
    public AsteroidState[] getAsteroids() {
        return asteroids;
    }

    /**
     * Returns every currently-active power-up's state.
     *
     * @return the power-up states in this snapshot
     */
    public PowerUpState[] getPowerUps() {
        return powerUps;
    }

    /**
     * Returns every currently-active mine's state.
     *
     * @return the mine states in this snapshot
     */
    public MineState[] getMines() {
        return mines;
    }
}
