package de.mkoehler.starwars.sim.components;

import com.badlogic.ashley.core.Component;

/**
 * Tracks a ship's current and maximum shield strength, and its recharge
 * rate (design.md 2.5). The shield absorbs a share of incoming damage
 * proportional to its current fraction — see
 * {@link de.mkoehler.starwars.sim.ShipDamage} — and regenerates
 * continuously at a flat rate via
 * {@link de.mkoehler.starwars.sim.systems.ShieldRegenSystem}, with no
 * regen-delay-after-hit mechanic yet (a common enhancement, deliberately
 * not built until it's asked for).
 */
public class ShieldComponent implements Component {

    private final float max;
    private final float rechargePerSecond;
    private float current;

    /**
     * Creates a shield component at full charge.
     *
     * @param max               the ship's maximum shield capacity
     * @param rechargePerSecond how fast the shield recharges, in shield
     *                          points/second
     */
    public ShieldComponent(float max, float rechargePerSecond) {
        this.max = max;
        this.rechargePerSecond = rechargePerSecond;
        this.current = max;
    }

    /**
     * Returns the current shield strength.
     *
     * @return the current shield strength
     */
    public float getCurrent() {
        return current;
    }

    /**
     * Returns the maximum shield capacity.
     *
     * @return the maximum shield capacity
     */
    public float getMax() {
        return max;
    }

    /**
     * Reduces current shield strength by the given amount, not below zero.
     *
     * @param amount the damage to apply
     */
    public void damage(float amount) {
        current = Math.max(0f, current - amount);
    }

    /**
     * Increases current shield strength at this shield's recharge rate, not
     * above its maximum. Equivalent to {@link #regenerate(float, float)}
     * with a multiplier of 1.
     *
     * @param deltaTime time elapsed, in seconds
     */
    public void regenerate(float deltaTime) {
        regenerate(deltaTime, 1f);
    }

    /**
     * Increases current shield strength at this shield's recharge rate
     * scaled by {@code multiplier} (the ship's current
     * {@link de.mkoehler.starwars.sim.PowerSystem#SHIELDS} power allocation,
     * see {@link de.mkoehler.starwars.sim.PowerDistribution#multiplierFor}),
     * not above its maximum.
     *
     * @param deltaTime  time elapsed, in seconds
     * @param multiplier the current Shields power multiplier
     */
    public void regenerate(float deltaTime, float multiplier) {
        current = Math.min(max, current + rechargePerSecond * multiplier * deltaTime);
    }
}
