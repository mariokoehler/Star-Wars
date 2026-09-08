package de.mkoehler.starwars.sim;

/**
 * A ship type's balance/tuning numbers and HUD layout, loaded from a
 * {@code shipdata/<name>.stats.json} classpath resource (see
 * {@link ShipStats}). Plain Jackson bean (public no-arg constructor +
 * getters/setters), per design.md 3.9's convention for JSON-serialized
 * classes.
 * <p>
 * The four {@code hud*ClipPixel} fields are the vertical pixel range (top
 * and bottom, counted from the top of the image, matching
 * {@link com.badlogic.gdx.graphics.g2d.TextureRegion}'s own pixel
 * convention) within which a ship-status HUD overlay image actually has
 * visible content — everything outside that range is transparent padding
 * kept only so the shield/hull/background HUD images share one canvas size
 * and align with no offset math needed. See
 * {@link de.mkoehler.starwars.render.ShipStatusHud} for how they're used to
 * clip each overlay into a bottom-anchored "fill" gauge. These differ per
 * ship type since each ship's hull silhouette art (and potentially its
 * shield ring) occupies a different vertical extent of that shared canvas.
 */
public class ShipTypeConfig {

    private float radiusMeters;
    private float pixelsPerMeter;
    private float thrustForce;
    private float turnTorque;
    private float engineTurnResponseExponent;
    private float hullMaxHealth;
    private float shieldMaxCapacity;
    private float shieldRechargePerSecond;
    private float hudShieldClipTopPixel;
    private float hudShieldClipBottomPixel;
    private float hudHullClipTopPixel;
    private float hudHullClipBottomPixel;
    private int unlockCostXp;
    private boolean radarBaseEnabled;
    private float radarBaseRangeMeters;
    private boolean radarConeEnabled;
    private float radarConeRangeMeters;
    private float radarConeHalfAngleDegrees;
    private boolean radarPulseEnabled;
    private float radarPulseRangeMeters;
    private float radarPulseCooldownSeconds;
    private float radarPulseRevealDurationSeconds;

    /**
     * Returns the ship's collision/draw radius, used only for the fallback
     * circle hitbox ({@link ShipFactory#createBody}) when no hitbox polygon
     * has been authored — with every current ship type having one, this is
     * effectively dormant, but still set to a sensible value per type.
     *
     * @return the radius, in meters
     */
    public float getRadiusMeters() {
        return radiusMeters;
    }

    public void setRadiusMeters(float radiusMeters) {
        this.radiusMeters = radiusMeters;
    }

    /**
     * Returns this ship type's own pixels-per-meter — the conversion factor
     * between its authored sprite-space pixel coordinates (the
     * {@code dev-tools}-authored hitbox polygon and attachment points, in
     * {@link de.mkoehler.starwars.sim.metadata.PixelPoint}) and Box2D meters,
     * <strong>and</strong> between its source art's actual pixel resolution
     * and its real-world size.
     * <p>
     * Deliberately per-ship rather than the single global
     * {@link PhysicsConstants#PIXELS_PER_METER}: two ships drawn at the same
     * physical size but authored at different source-art resolutions (e.g.
     * a 128px sprite vs. a 256px one picked purely for crisper art, design.md
     * 4.3) must convert their hitbox/attachment pixel coordinates at
     * <em>different</em> rates, or the higher-resolution one ends up with a
     * literally larger hitbox (and, since Box2D derives mass from a fixture's
     * area at a uniform density, more mass) purely as an accident of its
     * source art's resolution — found and fixed 2026-09-06 (design.md 2.7/4.3),
     * having affected every ship imported at 256px (Falcon, Star Destroyer,
     * TIE Interceptor). The rendered sprite size is derived the same way,
     * from the real loaded texture region's pixel dimensions divided by this
     * value (see {@code Client}) — never separately authored — so the visual
     * size and the physical hitbox size can't drift apart from each other.
     *
     * @return this ship type's pixels-per-meter
     */
    public float getPixelsPerMeter() {
        return pixelsPerMeter;
    }

    public void setPixelsPerMeter(float pixelsPerMeter) {
        this.pixelsPerMeter = pixelsPerMeter;
    }

    public float getThrustForce() {
        return thrustForce;
    }

    public void setThrustForce(float thrustForce) {
        this.thrustForce = thrustForce;
    }

    public float getTurnTorque() {
        return turnTorque;
    }

    public void setTurnTorque(float turnTorque) {
        this.turnTorque = turnTorque;
    }

    /**
     * Returns the exponent applied to the Engines power multiplier before
     * it scales turn torque (design.md 2.2's addendum — see
     * {@link de.mkoehler.starwars.sim.TurnResponseCurve}): {@code 1.0}
     * reproduces the plain linear multiplier thrust also uses (unchanged
     * behavior), {@code < 1.0} gives diminishing returns at both extremes —
     * taming how "ridiculously" agile a light ship gets with Engines maxed
     * out, at the cost of also softening how punishing a starved-Engines
     * split is. Only turn torque is affected; thrust always stays linear.
     *
     * @return the turn-response exponent
     */
    public float getEngineTurnResponseExponent() {
        return engineTurnResponseExponent;
    }

    public void setEngineTurnResponseExponent(float engineTurnResponseExponent) {
        this.engineTurnResponseExponent = engineTurnResponseExponent;
    }

    public float getHullMaxHealth() {
        return hullMaxHealth;
    }

    public void setHullMaxHealth(float hullMaxHealth) {
        this.hullMaxHealth = hullMaxHealth;
    }

    public float getShieldMaxCapacity() {
        return shieldMaxCapacity;
    }

    public void setShieldMaxCapacity(float shieldMaxCapacity) {
        this.shieldMaxCapacity = shieldMaxCapacity;
    }

    /**
     * Returns how fast the shield recharges. A flat rate for now, standing
     * in for the future power-distribution-driven rate (design.md 2.2's
     * "Shields" allocation) the same way {@link WeaponStats}' fixed
     * cooldown stands in for the not-yet-built capacitor mechanic.
     *
     * @return the recharge rate, in shield points/second
     */
    public float getShieldRechargePerSecond() {
        return shieldRechargePerSecond;
    }

    public void setShieldRechargePerSecond(float shieldRechargePerSecond) {
        this.shieldRechargePerSecond = shieldRechargePerSecond;
    }

    public float getHudShieldClipTopPixel() {
        return hudShieldClipTopPixel;
    }

    public void setHudShieldClipTopPixel(float hudShieldClipTopPixel) {
        this.hudShieldClipTopPixel = hudShieldClipTopPixel;
    }

    public float getHudShieldClipBottomPixel() {
        return hudShieldClipBottomPixel;
    }

    public void setHudShieldClipBottomPixel(float hudShieldClipBottomPixel) {
        this.hudShieldClipBottomPixel = hudShieldClipBottomPixel;
    }

    public float getHudHullClipTopPixel() {
        return hudHullClipTopPixel;
    }

    public void setHudHullClipTopPixel(float hudHullClipTopPixel) {
        this.hudHullClipTopPixel = hudHullClipTopPixel;
    }

    public float getHudHullClipBottomPixel() {
        return hudHullClipBottomPixel;
    }

    public void setHudHullClipBottomPixel(float hudHullClipBottomPixel) {
        this.hudHullClipBottomPixel = hudHullClipBottomPixel;
    }

    /**
     * Returns the account XP cost to unlock this ship type (design.md —
     * ship unlocks), checked against a player's
     * {@link ShipUnlocks#availableXp available XP}, not their raw total.
     * Meaningless for a ship type that's always unlocked
     * ({@link ShipType#SNOWSPEEDER}) — {@link ShipUnlocks#isUnlocked} never
     * even reads it for that one.
     *
     * @return the XP cost to unlock this ship type
     */
    public int getUnlockCostXp() {
        return unlockCostXp;
    }

    public void setUnlockCostXp(int unlockCostXp) {
        this.unlockCostXp = unlockCostXp;
    }

    /**
     * Returns whether this ship type's omnidirectional base radar (design.md
     * 2.14) is enabled at all — a ship type can have this (and/or the cone/
     * pulse below) individually switched off, for a future per-ship-type
     * radar loadout (design.md 7's Ship Tree progression); every current
     * ship type has all three enabled.
     *
     * @return {@code true} if the base radar is enabled
     */
    public boolean isRadarBaseEnabled() {
        return radarBaseEnabled;
    }

    public void setRadarBaseEnabled(boolean radarBaseEnabled) {
        this.radarBaseEnabled = radarBaseEnabled;
    }

    /**
     * Returns the base radar's omnidirectional detection range, meaningless
     * if {@link #isRadarBaseEnabled()} is {@code false}.
     *
     * @return the range, in meters
     */
    public float getRadarBaseRangeMeters() {
        return radarBaseRangeMeters;
    }

    public void setRadarBaseRangeMeters(float radarBaseRangeMeters) {
        this.radarBaseRangeMeters = radarBaseRangeMeters;
    }

    /**
     * Returns whether this ship type's forward-facing cone radar (design.md
     * 2.14) is enabled at all.
     *
     * @return {@code true} if the cone radar is enabled
     */
    public boolean isRadarConeEnabled() {
        return radarConeEnabled;
    }

    public void setRadarConeEnabled(boolean radarConeEnabled) {
        this.radarConeEnabled = radarConeEnabled;
    }

    /**
     * Returns the cone radar's detection range, meaningless if
     * {@link #isRadarConeEnabled()} is {@code false}.
     *
     * @return the range, in meters
     */
    public float getRadarConeRangeMeters() {
        return radarConeRangeMeters;
    }

    public void setRadarConeRangeMeters(float radarConeRangeMeters) {
        this.radarConeRangeMeters = radarConeRangeMeters;
    }

    /**
     * Returns the cone radar's half-angle — the arc spans this many degrees
     * to either side of the ship's current facing (e.g. 30 means a 60°-wide
     * total arc), meaningless if {@link #isRadarConeEnabled()} is
     * {@code false}.
     *
     * @return the half-angle, in degrees
     */
    public float getRadarConeHalfAngleDegrees() {
        return radarConeHalfAngleDegrees;
    }

    public void setRadarConeHalfAngleDegrees(float radarConeHalfAngleDegrees) {
        this.radarConeHalfAngleDegrees = radarConeHalfAngleDegrees;
    }

    /**
     * Returns whether this ship type's active pulse radar (design.md 2.14,
     * the "R" keybind) is enabled at all.
     *
     * @return {@code true} if the pulse is enabled
     */
    public boolean isRadarPulseEnabled() {
        return radarPulseEnabled;
    }

    public void setRadarPulseEnabled(boolean radarPulseEnabled) {
        this.radarPulseEnabled = radarPulseEnabled;
    }

    /**
     * Returns the pulse's omnidirectional detection range while active,
     * meaningless if {@link #isRadarPulseEnabled()} is {@code false}.
     *
     * @return the range, in meters
     */
    public float getRadarPulseRangeMeters() {
        return radarPulseRangeMeters;
    }

    public void setRadarPulseRangeMeters(float radarPulseRangeMeters) {
        this.radarPulseRangeMeters = radarPulseRangeMeters;
    }

    /**
     * Returns how long after triggering the pulse before it can be
     * triggered again.
     *
     * @return the cooldown, in seconds
     */
    public float getRadarPulseCooldownSeconds() {
        return radarPulseCooldownSeconds;
    }

    public void setRadarPulseCooldownSeconds(float radarPulseCooldownSeconds) {
        this.radarPulseCooldownSeconds = radarPulseCooldownSeconds;
    }

    /**
     * Returns how long, after triggering, the pulse's own detection stays
     * active <em>and</em> the pulsing ship stays unconditionally visible to
     * every other player's radar (design.md 2.14's "one simplification" —
     * the spec's two separate effects share this one duration).
     *
     * @return the duration, in seconds
     */
    public float getRadarPulseRevealDurationSeconds() {
        return radarPulseRevealDurationSeconds;
    }

    public void setRadarPulseRevealDurationSeconds(float radarPulseRevealDurationSeconds) {
        this.radarPulseRevealDurationSeconds = radarPulseRevealDurationSeconds;
    }
}
