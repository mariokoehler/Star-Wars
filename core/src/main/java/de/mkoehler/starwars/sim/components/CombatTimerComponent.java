package de.mkoehler.starwars.sim.components;

import com.badlogic.ashley.core.Component;

/**
 * Tracks how long it's been since a ship last fired its weapon or was last
 * hit by another player's shot — the two conditions design.md 2.3's
 * combat-lock rule (for leaving a match via ESC) checks. Server-side only:
 * ticked every tick by {@code CombatTimerSystem}, marked by
 * {@code WeaponSystem} on firing and {@code GameNetworkServer} on
 * resolving a hit.
 */
public class CombatTimerComponent implements Component {

    // Large enough that a freshly-spawned ship, which has done neither yet, is never considered
    // "in combat" by isInCombat for any realistic threshold.
    private static final float NEVER = 1_000_000f;

    private float secondsSinceLastFired = NEVER;
    private float secondsSinceLastHit = NEVER;

    /**
     * Advances both timers by one tick's worth of time.
     *
     * @param deltaTime time elapsed, in seconds
     */
    public void tick(float deltaTime) {
        secondsSinceLastFired += deltaTime;
        secondsSinceLastHit += deltaTime;
    }

    /**
     * Resets the "last fired" timer to zero — call when this ship's weapon fires.
     */
    public void markFired() {
        secondsSinceLastFired = 0f;
    }

    /**
     * Resets the "last hit" timer to zero — call when this ship takes damage.
     */
    public void markHit() {
        secondsSinceLastHit = 0f;
    }

    /**
     * Returns whether this ship is currently "in combat" per design.md
     * 2.3's rule: it fired its weapon or was hit within the last
     * {@code thresholdSeconds}.
     *
     * @param thresholdSeconds the combat-lock window, in seconds
     * @return {@code true} if either timer is still under the threshold
     */
    public boolean isInCombat(float thresholdSeconds) {
        return secondsSinceLastFired < thresholdSeconds || secondsSinceLastHit < thresholdSeconds;
    }
}
