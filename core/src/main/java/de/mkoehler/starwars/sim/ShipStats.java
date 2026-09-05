package de.mkoehler.starwars.sim;

import de.mkoehler.starwars.sim.metadata.ShipSpriteMetadata;
import de.mkoehler.starwars.sim.metadata.ShipSpriteMetadataLoader;

import java.util.Optional;

/**
 * Per-ship-type tuning values shared between the server (which needs them to
 * build a Box2D body and apply forces) and the client (which needs the same
 * radius to size the drawn sprite consistently with the server's collision
 * shape).
 * <p>
 * A single hardcoded constant for now, standing in for a future data-driven
 * ship roster (see design.md 6's "Ship roster" TODO).
 */
public final class ShipStats {

    /**
     * Stats for the (currently only) X-wing, derived from its 128px atlas
     * frame at {@link PhysicsConstants#PIXELS_PER_METER}. Thrust/torque
     * tuned by feel (2026-09-05) for an agile handling suited to the
     * larger 1920x1080 view (design.md 4.1) — not derived from any
     * in-universe reference, just what played well. Max health is a
     * placeholder pending real weapon-vs-armor balancing.
     */
    public static final ShipStats XWING = new ShipStats("xwing", 2f, 200f, 150f, 100f);

    private final float radiusMeters;
    private final float thrustForce;
    private final float turnTorque;
    private final float maxHealth;
    private final Optional<ShipSpriteMetadata> spriteMetadata;

    private ShipStats(String shipName, float radiusMeters, float thrustForce, float turnTorque, float maxHealth) {
        this.radiusMeters = radiusMeters;
        this.thrustForce = thrustForce;
        this.turnTorque = turnTorque;
        this.maxHealth = maxHealth;
        this.spriteMetadata = ShipSpriteMetadataLoader.loadFromClasspath("shipdata/" + shipName + ".meta.json");
    }

    /**
     * Returns the ship's collision/draw radius.
     *
     * @return the radius, in meters
     */
    public float getRadiusMeters() {
        return radiusMeters;
    }

    /**
     * Returns the force applied while thrusting.
     *
     * @return the thrust force, in newtons
     */
    public float getThrustForce() {
        return thrustForce;
    }

    /**
     * Returns the torque applied while turning.
     *
     * @return the turn torque, in newton-meters
     */
    public float getTurnTorque() {
        return turnTorque;
    }

    /**
     * Returns the ship's maximum health.
     *
     * @return the maximum health
     */
    public float getMaxHealth() {
        return maxHealth;
    }

    /**
     * Returns this ship's authored sprite metadata (hitbox polygon and named
     * attachment points, see design.md 2.4/4.3), if a
     * {@code shipdata/<name>.meta.json} has been created for it via the
     * {@code dev-tools} sprite metadata editor.
     *
     * @return the metadata, or empty if none has been authored yet — callers
     * should fall back to the default circular hitbox / fixed spawn offsets
     */
    public Optional<ShipSpriteMetadata> getSpriteMetadata() {
        return spriteMetadata;
    }
}
