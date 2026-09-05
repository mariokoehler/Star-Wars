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
    private float thrustForce;
    private float turnTorque;
    private float hullMaxHealth;
    private float shieldMaxCapacity;
    private float shieldRechargePerSecond;
    private float hudShieldClipTopPixel;
    private float hudShieldClipBottomPixel;
    private float hudHullClipTopPixel;
    private float hudHullClipBottomPixel;

    public float getRadiusMeters() {
        return radiusMeters;
    }

    public void setRadiusMeters(float radiusMeters) {
        this.radiusMeters = radiusMeters;
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
}
