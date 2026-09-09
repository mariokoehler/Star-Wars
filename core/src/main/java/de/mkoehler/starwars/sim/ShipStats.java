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
     * Returns this ship type's pixels-per-meter — see
     * {@link ShipTypeConfig#getPixelsPerMeter()} for the full explanation.
     *
     * @return this ship type's pixels-per-meter
     */
    public float getPixelsPerMeter() {
        return config.getPixelsPerMeter();
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
     * Returns the exponent applied to the Engines power multiplier before
     * it scales turn torque.
     *
     * @return the turn-response exponent, see {@link ShipTypeConfig#getEngineTurnResponseExponent()}
     */
    public float getEngineTurnResponseExponent() {
        return config.getEngineTurnResponseExponent();
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

    /**
     * Returns the account XP cost to unlock this ship type.
     *
     * @return the XP cost to unlock this ship type, see {@link ShipTypeConfig#getUnlockCostXp()}
     */
    public int getUnlockCostXp() {
        return config.getUnlockCostXp();
    }

    /**
     * Returns whether this ship type's omnidirectional base radar is enabled.
     *
     * @return {@code true} if enabled, see {@link ShipTypeConfig#isRadarBaseEnabled()}
     */
    public boolean isRadarBaseEnabled() {
        return config.isRadarBaseEnabled();
    }

    /**
     * Returns the base radar's omnidirectional detection range.
     *
     * @return the range, in meters, see {@link ShipTypeConfig#getRadarBaseRangeMeters()}
     */
    public float getRadarBaseRangeMeters() {
        return config.getRadarBaseRangeMeters();
    }

    /**
     * Returns whether this ship type's forward-facing cone radar is enabled.
     *
     * @return {@code true} if enabled, see {@link ShipTypeConfig#isRadarConeEnabled()}
     */
    public boolean isRadarConeEnabled() {
        return config.isRadarConeEnabled();
    }

    /**
     * Returns the cone radar's detection range.
     *
     * @return the range, in meters, see {@link ShipTypeConfig#getRadarConeRangeMeters()}
     */
    public float getRadarConeRangeMeters() {
        return config.getRadarConeRangeMeters();
    }

    /**
     * Returns the cone radar's half-angle.
     *
     * @return the half-angle, in degrees, see {@link ShipTypeConfig#getRadarConeHalfAngleDegrees()}
     */
    public float getRadarConeHalfAngleDegrees() {
        return config.getRadarConeHalfAngleDegrees();
    }

    /**
     * Returns whether this ship type's active pulse radar is enabled.
     *
     * @return {@code true} if enabled, see {@link ShipTypeConfig#isRadarPulseEnabled()}
     */
    public boolean isRadarPulseEnabled() {
        return config.isRadarPulseEnabled();
    }

    /**
     * Returns the pulse's omnidirectional detection range while active.
     *
     * @return the range, in meters, see {@link ShipTypeConfig#getRadarPulseRangeMeters()}
     */
    public float getRadarPulseRangeMeters() {
        return config.getRadarPulseRangeMeters();
    }

    /**
     * Returns how long after triggering the pulse before it can be triggered again.
     *
     * @return the cooldown, in seconds, see {@link ShipTypeConfig#getRadarPulseCooldownSeconds()}
     */
    public float getRadarPulseCooldownSeconds() {
        return config.getRadarPulseCooldownSeconds();
    }

    /**
     * Returns how long the pulse's own detection (and the pulsing ship's
     * unconditional visibility to others) lasts after triggering.
     *
     * @return the duration, in seconds, see {@link ShipTypeConfig#getRadarPulseRevealDurationSeconds()}
     */
    public float getRadarPulseRevealDurationSeconds() {
        return config.getRadarPulseRevealDurationSeconds();
    }

    /**
     * Returns this ship type's largest enabled radar range — the distance
     * the minimap's scope's outer edge represents (design.md 2.14's
     * rendering addendum: each ship type's scope is scaled to its own
     * equipment rather than one fixed distance for every ship, so a ship
     * with fewer mechanisms enabled still uses the whole scope). {@code 0}
     * if this ship type somehow has no radar mechanism enabled at all (not
     * expected in practice — every current ship type has at least the base
     * radar).
     *
     * @return the largest enabled range, in meters
     */
    public float getRadarMaxRangeMeters() {
        float max = 0f;
        if (isRadarBaseEnabled()) {
            max = Math.max(max, getRadarBaseRangeMeters());
        }
        if (isRadarConeEnabled()) {
            max = Math.max(max, getRadarConeRangeMeters());
        }
        if (isRadarPulseEnabled()) {
            max = Math.max(max, getRadarPulseRangeMeters());
        }
        return max;
    }

    /**
     * Returns whether this ship type can fire missiles at all.
     *
     * @return {@code true} if missiles are enabled, see {@link ShipTypeConfig#isMissileEnabled()}
     */
    public boolean isMissileEnabled() {
        return config.isMissileEnabled();
    }

    /**
     * Returns how many missiles this ship type spawns with.
     *
     * @return the starting missile count, see {@link ShipTypeConfig#getMissileStartingCount()}
     */
    public int getMissileStartingCount() {
        return config.getMissileStartingCount();
    }

    /**
     * Returns how long an uninterrupted cone-radar lock takes to acquire.
     *
     * @return the lock-acquisition duration, in seconds, see {@link ShipTypeConfig#getMissileLockDurationSeconds()}
     */
    public float getMissileLockDurationSeconds() {
        return config.getMissileLockDurationSeconds();
    }

    /**
     * Returns this ship type's engine particle effect resource name, if one
     * is configured (design.md — engine particle effects).
     *
     * @return the effect's resource name, or empty if this ship type has none
     * configured yet, see {@link ShipTypeConfig#getEngineParticleEffect()}
     */
    public Optional<String> getEngineParticleEffect() {
        String name = config.getEngineParticleEffect();
        return (name == null || name.isBlank()) ? Optional.empty() : Optional.of(name);
    }
}
