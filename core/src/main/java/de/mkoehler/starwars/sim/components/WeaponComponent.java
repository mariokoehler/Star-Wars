package de.mkoehler.starwars.sim.components;

import com.badlogic.ashley.core.Component;
import de.mkoehler.starwars.sim.WeaponStats;

/**
 * Tracks a ship's weapon cooldown and capacitor charge state. Server-side
 * only — {@link de.mkoehler.starwars.sim.systems.WeaponSystem} decrements
 * {@link #getCooldownRemaining()} and recharges the capacitor
 * ({@link #rechargeCapacitor(float, float)}) every tick, and fires
 * (spawning a projectile, {@link #consumeShot()}) once {@link #canFire()}
 * is true while the input is held.
 */
public class WeaponComponent implements Component {

    private final WeaponStats stats;
    private float cooldownRemaining;
    private float currentCharge;

    /**
     * Creates a weapon component, ready to fire immediately with a full capacitor.
     *
     * @param stats the weapon type's tuning values
     */
    public WeaponComponent(WeaponStats stats) {
        this.stats = stats;
        this.currentCharge = stats.getCapacitorMaxCharge();
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
     * Returns the capacitor's current charge.
     *
     * @return the current charge
     */
    public float getCurrentCharge() {
        return currentCharge;
    }

    /**
     * Returns whether this weapon can fire right now: its mechanical
     * cooldown has expired <em>and</em> the capacitor holds enough charge
     * for a shot.
     *
     * @return {@code true} if a shot can be fired
     */
    public boolean canFire() {
        return cooldownRemaining <= 0f && currentCharge >= stats.getShotEnergyCost();
    }

    /**
     * Increases the capacitor's charge at its base rate scaled by
     * {@code multiplier} (the ship's current
     * {@link de.mkoehler.starwars.sim.PowerSystem#WEAPONS} power allocation,
     * see {@link de.mkoehler.starwars.sim.PowerDistribution#multiplierFor}),
     * not above its maximum.
     *
     * @param deltaTime  time elapsed, in seconds
     * @param multiplier the current Weapons power multiplier
     */
    public void rechargeCapacitor(float deltaTime, float multiplier) {
        currentCharge = Math.min(stats.getCapacitorMaxCharge(),
            currentCharge + stats.getBaseRechargePerSecond() * multiplier * deltaTime);
    }

    /**
     * Draws one shot's energy cost from the capacitor and resets the
     * mechanical cooldown to the weapon's full duration — call only when
     * {@link #canFire()} is true.
     */
    public void consumeShot() {
        currentCharge -= stats.getShotEnergyCost();
        cooldownRemaining = stats.getCooldownSeconds();
    }
}
