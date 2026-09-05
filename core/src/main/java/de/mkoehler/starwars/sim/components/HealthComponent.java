package de.mkoehler.starwars.sim.components;

import com.badlogic.ashley.core.Component;

/**
 * Tracks a ship's current and maximum health. Server-side only — health is
 * simulation state, not something a client renders directly yet (no HUD
 * exists, design.md 6).
 */
public class HealthComponent implements Component {

    private final float max;
    private float current;

    /**
     * Creates a health component at full health.
     *
     * @param max the ship's maximum health
     */
    public HealthComponent(float max) {
        this.max = max;
        this.current = max;
    }

    /**
     * Returns the current health.
     *
     * @return the current health
     */
    public float getCurrent() {
        return current;
    }

    /**
     * Returns the maximum health.
     *
     * @return the maximum health
     */
    public float getMax() {
        return max;
    }

    /**
     * Reduces current health by the given amount, not below zero.
     *
     * @param amount the damage to apply
     */
    public void damage(float amount) {
        current = Math.max(0f, current - amount);
    }

    /**
     * Returns whether current health has reached zero.
     *
     * @return {@code true} if the ship is destroyed
     */
    public boolean isDestroyed() {
        return current <= 0f;
    }
}
