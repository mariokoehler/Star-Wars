package de.mkoehler.starwars.sim;

import de.mkoehler.starwars.sim.metadata.JsonResourceLoader;
import de.mkoehler.starwars.sim.metadata.ShipSpriteMetadata;
import de.mkoehler.starwars.sim.metadata.ShipSpriteMetadataLoader;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * A ship type's runtime stats: its {@link ShipTypeConfig} (required —
 * balance numbers and HUD layout, loaded from
 * {@code shipdata/<name>.stats.json}) plus its {@link ShipSpriteMetadata}
 * (optional — hitbox polygon and attachment points, loaded from
 * {@code shipdata/<name>.meta.json} if the {@code dev-tools} sprite
 * metadata editor has authored one yet). Shared between the server (which
 * needs them to build a Box2D body, apply forces, and size shield/hull
 * pools) and the client (which needs the same numbers to size the drawn
 * sprite and HUD consistently with the server).
 * <p>
 * One instance per {@link ShipType}, cached in {@link #forType}. A single
 * ship type exists so far — {@link #XWING} is kept as a convenience
 * constant for the many call sites that don't yet need to look a ship's
 * type up dynamically (design.md 6's "Ship roster" TODO is still open).
 */
public final class ShipStats {

    private static final Map<ShipType, ShipStats> BY_TYPE = new EnumMap<>(ShipType.class);

    /**
     * Stats for the (currently only) X-wing. Thrust/torque tuned by feel
     * (2026-09-05) for an agile handling suited to the larger 1920x1080
     * view (design.md 4.1) — not derived from any in-universe reference,
     * just what played well; don't recalculate them toward a formula
     * without asking. Hull/shield numbers are untuned placeholders pending
     * a real balancing pass.
     */
    public static final ShipStats XWING = forType(ShipType.XWING);

    private final ShipType type;
    private final ShipTypeConfig config;
    private final Optional<ShipSpriteMetadata> spriteMetadata;

    private ShipStats(ShipType type) {
        this.type = type;
        String resourceName = type.getResourceName();
        this.config = JsonResourceLoader.loadFromClasspath("shipdata/" + resourceName + ".stats.json", ShipTypeConfig.class)
            .orElseThrow(() -> new IllegalStateException(
                "Missing required shipdata/" + resourceName + ".stats.json for ship type " + type));
        this.spriteMetadata = ShipSpriteMetadataLoader.loadFromClasspath("shipdata/" + resourceName + ".meta.json");
    }

    /**
     * Returns the (cached) stats for a ship type, loading them on first use.
     *
     * @param type the ship type
     * @return that type's stats
     */
    public static ShipStats forType(ShipType type) {
        return BY_TYPE.computeIfAbsent(type, ShipStats::new);
    }

    /**
     * Returns this ship's type.
     *
     * @return the ship type
     */
    public ShipType getType() {
        return type;
    }

    /**
     * Returns the ship's collision/draw radius.
     *
     * @return the radius, in meters
     */
    public float getRadiusMeters() {
        return config.getRadiusMeters();
    }

    /**
     * Returns the force applied while thrusting.
     *
     * @return the thrust force, in newtons
     */
    public float getThrustForce() {
        return config.getThrustForce();
    }

    /**
     * Returns the torque applied while turning.
     *
     * @return the turn torque, in newton-meters
     */
    public float getTurnTorque() {
        return config.getTurnTorque();
    }

    /**
     * Returns the ship's maximum hull health.
     *
     * @return the maximum hull health
     */
    public float getMaxHealth() {
        return config.getHullMaxHealth();
    }

    /**
     * Returns the ship's maximum shield capacity.
     *
     * @return the maximum shield capacity
     */
    public float getShieldMaxCapacity() {
        return config.getShieldMaxCapacity();
    }

    /**
     * Returns how fast the ship's shield recharges.
     *
     * @return the recharge rate, in shield points/second
     */
    public float getShieldRechargePerSecond() {
        return config.getShieldRechargePerSecond();
    }

    /**
     * Returns the top of the visible-pixel range (from the top of the
     * image) of this ship type's HUD shield overlay.
     *
     * @return the top pixel row, see {@link ShipTypeConfig}
     */
    public float getHudShieldClipTopPixel() {
        return config.getHudShieldClipTopPixel();
    }

    /**
     * Returns the bottom of the visible-pixel range of this ship type's HUD
     * shield overlay.
     *
     * @return the bottom pixel row, see {@link ShipTypeConfig}
     */
    public float getHudShieldClipBottomPixel() {
        return config.getHudShieldClipBottomPixel();
    }

    /**
     * Returns the top of the visible-pixel range of this ship type's HUD
     * hull overlay.
     *
     * @return the top pixel row, see {@link ShipTypeConfig}
     */
    public float getHudHullClipTopPixel() {
        return config.getHudHullClipTopPixel();
    }

    /**
     * Returns the bottom of the visible-pixel range of this ship type's HUD
     * hull overlay.
     *
     * @return the bottom pixel row, see {@link ShipTypeConfig}
     */
    public float getHudHullClipBottomPixel() {
        return config.getHudHullClipBottomPixel();
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
