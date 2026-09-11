package de.mkoehler.starwars.sim.components;

import com.badlogic.ashley.core.Component;

/**
 * A ship's temporary power-generation boost, granted by picking up a
 * {@code BOOST} power-up (design.md — power-ups): while active, every power
 * system's effect multiplier ({@code PowerDistribution#multiplierFor}) is
 * additionally scaled by {@link #getMultiplier()} — "the amount of power
 * that feeds into the power distribution system" doubles, on top of however
 * that total is currently split across shields/weapons/engines, rather than
 * changing the split itself.
 * <p>
 * Added to every ship unconditionally ({@code ShipFactory}), inactive by
 * default. {@link de.mkoehler.starwars.sim.systems.PowerBoostSystem} ticks
 * {@link #remainingSeconds} down every server tick; {@link de.mkoehler.starwars.sim.systems.ShipControlSystem}/
 * {@link de.mkoehler.starwars.sim.systems.ShieldRegenSystem}/
 * {@link de.mkoehler.starwars.sim.systems.WeaponSystem} each read
 * {@link #getMultiplier()} alongside the ship's own
 * {@code PowerDistributionComponent}. Picking up a second BOOST while one is
 * already active simply refreshes the timer back to the full duration
 * (see {@link #activate}) rather than stacking multipliers or extending it
 * additively — an explicit, flagged default, not specified by the original
 * power-up spec.
 */
public class PowerBoostComponent implements Component {

    /** The effect multiplier applied on top of a system's own power-distribution multiplier while active. */
    public static final float BOOST_MULTIPLIER = 2f;

    private float remainingSeconds;

    /**
     * Returns this ship's current power-generation multiplier: {@link #BOOST_MULTIPLIER}
     * while a boost is active, {@code 1f} otherwise.
     *
     * @return the multiplier
     */
    public float getMultiplier() {
        return remainingSeconds > 0f ? BOOST_MULTIPLIER : 1f;
    }

    /**
     * Returns how much longer the boost stays active.
     *
     * @return the remaining duration, in seconds; {@code <= 0} means inactive
     */
    public float getRemainingSeconds() {
        return remainingSeconds;
    }

    /**
     * Activates (or refreshes) the boost for {@code durationSeconds} — always
     * sets the timer to exactly this value, so picking up a second BOOST
     * while one is already running resets the countdown rather than adding
     * to it.
     *
     * @param durationSeconds how long the boost should last from now
     */
    public void activate(float durationSeconds) {
        remainingSeconds = durationSeconds;
    }

    /**
     * Counts the remaining duration down by {@code deltaTime}, not below zero.
     *
     * @param deltaTime time since the last tick, in seconds
     */
    public void tick(float deltaTime) {
        remainingSeconds = Math.max(0f, remainingSeconds - deltaTime);
    }
}
