package de.mkoehler.starwars.sim.components;

import com.badlogic.ashley.core.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * A ship's radar runtime state (design.md 2.14): the active-pulse cooldown/
 * active-duration timers, and the set of enemy player ids this ship
 * currently detects. Added to <em>every</em> ship, unconditionally — unlike
 * {@link TurretComponent} (only ship types with turret attachment points),
 * every ship has some radar capability, even if a given ship type has one or
 * more of its three mechanisms disabled via {@code ShipTypeConfig}.
 * <p>
 * Server-side only: {@code RadarSystem} ticks the timers and recomputes
 * {@link #getDetectedPlayerIds()} from scratch every tick. Holds no stats
 * reference of its own — callers look up a ship's radar config fresh via
 * {@code ShipTypeComponent}/{@code ShipStats.forType}, same pattern
 * {@code WeaponSystem}/{@code TurretSystem} already use, rather than caching
 * a stats reference here the way {@link WeaponComponent} does.
 */
public class RadarComponent implements Component {

    private float pulseCooldownRemaining;
    private float pulseActiveRemaining;
    private final Set<Integer> detectedPlayerIds = new HashSet<>();

    /**
     * Reduces both timers by one tick's worth of time, not below zero.
     *
     * @param deltaTime time elapsed, in seconds
     */
    public void tickCooldowns(float deltaTime) {
        pulseCooldownRemaining = Math.max(0f, pulseCooldownRemaining - deltaTime);
        pulseActiveRemaining = Math.max(0f, pulseActiveRemaining - deltaTime);
    }

    /**
     * Returns whether the active pulse is still cooling down from its last
     * use.
     *
     * @return {@code true} if a new pulse can't be triggered yet
     */
    public boolean isPulseOnCooldown() {
        return pulseCooldownRemaining > 0f;
    }

    /**
     * Returns how much longer until this ship can pulse again.
     *
     * @return the remaining cooldown, in seconds; {@code <= 0} means ready
     */
    public float getPulseCooldownRemaining() {
        return pulseCooldownRemaining;
    }

    /**
     * Returns whether this ship's pulse is currently active — while true,
     * it both extends this ship's own detection (omnidirectionally, at the
     * pulse's range) and makes it unconditionally visible to every other
     * ship's radar, regardless of range (design.md 2.14).
     *
     * @return {@code true} if a pulse is currently active
     */
    public boolean isPulseActive() {
        return pulseActiveRemaining > 0f;
    }

    /**
     * Starts a new pulse: resets the cooldown to its full duration and
     * starts (or restarts) the active-detection/visible-to-everyone window.
     * Callers must check {@link #isPulseOnCooldown()} (and that the ship
     * type's pulse is enabled at all) before calling this.
     *
     * @param cooldownSeconds      the ship type's configured pulse cooldown
     * @param activeDurationSeconds the ship type's configured pulse active/reveal duration
     */
    public void triggerPulse(float cooldownSeconds, float activeDurationSeconds) {
        pulseCooldownRemaining = cooldownSeconds;
        pulseActiveRemaining = activeDurationSeconds;
    }

    /**
     * Returns the mutable set of enemy player ids this ship currently
     * detects — cleared and repopulated from scratch every tick by
     * {@code RadarSystem}; callers must not retain a reference across ticks.
     *
     * @return the currently-detected enemy player ids
     */
    public Set<Integer> getDetectedPlayerIds() {
        return detectedPlayerIds;
    }
}
