package de.mkoehler.starwars.sim.components;

import com.badlogic.ashley.core.Component;
import de.mkoehler.starwars.sim.WeaponStats;

/**
 * Tracks a ship's weapon cooldown state. Server-side only —
 * {@link de.mkoehler.starwars.sim.systems.WeaponSystem} decrements
 * {@link #getCooldownRemaining()} every tick and fires (spawning a
 * projectile) once it reaches zero while the input is held.
 */
public class WeaponComponent implements Component {

    private final WeaponStats stats;
    private float cooldownRemaining;

    /**
     * Creates a weapon component, ready to fire immediately.
     *
     * @param stats the weapon type's tuning values
     */
    public WeaponComponent(WeaponStats stats) {
        this.stats = stats;
    }

    /**
     * Returns this weapon's tuning values.
     *
     * @return the weapon stats
     */
    public WeaponStats getStats() {
        return stats;
    }

    /**
     * Returns how much longer until this weapon can fire again.
     *
     * @return the remaining cooldown, in seconds; {@code <= 0} means ready
     */
    public float getCooldownRemaining() {
        return cooldownRemaining;
    }

    /**
     * Reduces the remaining cooldown by the given amount, not below zero.
     *
     * @param deltaTime time elapsed, in seconds
     */
    public void tickCooldown(float deltaTime) {
        cooldownRemaining = Math.max(0f, cooldownRemaining - deltaTime);
    }

    /**
     * Resets the cooldown to the weapon's full duration, e.g. right after firing.
     */
    public void resetCooldown() {
        cooldownRemaining = stats.getCooldownSeconds();
    }
}
