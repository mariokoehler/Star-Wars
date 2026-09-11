package de.mkoehler.starwars.sim.components;

import com.badlogic.ashley.core.Component;

/**
 * Tracks a ship's current and maximum hull health — the non-regenerating
 * pool "beneath" its {@link ShieldComponent} (design.md 2.5). Server-side
 * only — hull/shield are simulation state, broadcast to clients as
 * fractions via {@code ShipState} for HUD rendering
 * ({@link de.mkoehler.starwars.render.ShipStatusHud}), not mutated
 * client-side.
 * <p>
 * Damage isn't applied here directly — see {@link ShieldComponent} and
 * {@link de.mkoehler.starwars.sim.ShipDamage} for how a hit is split
 * between shield and hull before either one's {@code damage(...)} is
 * called.
 */
public class HullComponent implements Component {

    private final float max;
    private float current;

    /**
     * Creates a hull component at full health.
     *
     * @param max the ship's maximum hull health
     */
    public HullComponent(float max) {
        this.max = max;
        this.current = max;
    }

    /**
     * Returns the current hull health.
     *
     * @return the current hull health
     */
    public float getCurrent() {
        return current;
    }

    /**
     * Returns the maximum hull health.
     *
     * @return the maximum hull health
     */
    public float getMax() {
        return max;
    }

    /**
     * Reduces current hull health by the given amount, not below zero.
     *
     * @param amount the damage to apply
     */
    public void damage(float amount) {
        current = Math.max(0f, current - amount);
    }

    /**
     * Returns whether current hull health has reached zero.
     *
     * @return {@code true} if the ship is destroyed
     */
    public boolean isDestroyed() {
        return current <= 0f;
    }

    /**
     * Increases current hull health by the given amount, not above
     * {@link #getMax()} (design.md — power-ups' REPAIR effect).
     *
     * @param amount the amount to repair
     */
    public void repair(float amount) {
        current = Math.min(max, current + amount);
    }
}
