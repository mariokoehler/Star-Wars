package de.mkoehler.starwars.sim;

import de.mkoehler.starwars.sim.metadata.JsonResourceLoader;
import de.mkoehler.starwars.sim.metadata.ShipSpriteMetadata;
import de.mkoehler.starwars.sim.metadata.ShipSpriteMetadataLoader;

/**
 * The missile's runtime stats: its {@link MissileConfig} (required —
 * balance numbers, loaded from {@code projectiles/missile.stats.json}) plus
 * its {@link ShipSpriteMetadata} (hitbox polygon and its single
 * {@code ENGINE} attachment point, loaded from
 * {@code projectiles/missile.meta.json}). Same required-config/optional-
 * sprite-metadata split as {@link ShipStats}, but a single cached instance
 * rather than a per-type map — there's only one missile type.
 */
public final class MissileStats {

    /**
     * The (only) missile's stats, loaded on class init.
     */
    public static final MissileStats INSTANCE = new MissileStats();

    private final MissileConfig config;
    private final ShipSpriteMetadata spriteMetadata;

    private MissileStats() {
        this.config = JsonResourceLoader.loadFromClasspath("projectiles/missile.stats.json", MissileConfig.class)
            .orElseThrow(() -> new IllegalStateException("Missing required projectiles/missile.stats.json"));
        this.spriteMetadata = ShipSpriteMetadataLoader.loadFromClasspath("projectiles/missile.meta.json")
            .orElseThrow(() -> new IllegalStateException("Missing required projectiles/missile.meta.json"));
    }

    /**
     * Returns the missile's pixels-per-meter.
     *
     * @return the missile's pixels-per-meter, see {@link MissileConfig#getPixelsPerMeter()}
     */
    public float getPixelsPerMeter() {
        return config.getPixelsPerMeter();
    }

    /**
     * Returns the constant forward thrust force applied in flight.
     *
     * @return the thrust force, in newtons, see {@link MissileConfig#getThrustForce()}
     */
    public float getThrustForce() {
        return config.getThrustForce();
    }

    /**
     * Returns the maximum steering torque.
     *
     * @return the turn torque, in newton-meters, see {@link MissileConfig#getTurnTorque()}
     */
    public float getTurnTorque() {
        return config.getTurnTorque();
    }

    /**
     * Returns the damage dealt on a direct hit.
     *
     * @return the damage, see {@link MissileConfig#getDamage()}
     */
    public float getDamage() {
        return config.getDamage();
    }

    /**
     * Returns how many equal sub-hits a missile's damage is split into on impact.
     *
     * @return the sub-hit count, see {@link MissileConfig#getDamageChunkCount()}
     */
    public int getDamageChunkCount() {
        return config.getDamageChunkCount();
    }

    /**
     * Returns how long a fired missile's fuel lasts.
     *
     * @return the flight duration, in seconds, see {@link MissileConfig#getFlightSeconds()}
     */
    public float getFlightSeconds() {
        return config.getFlightSeconds();
    }

    /**
     * Returns the missile's authored sprite metadata (hitbox polygon and
     * {@code ENGINE} attachment point).
     *
     * @return the sprite metadata
     */
    public ShipSpriteMetadata getSpriteMetadata() {
        return spriteMetadata;
    }
}
