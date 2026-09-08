package de.mkoehler.starwars.sim.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;

/**
 * A ship's missile lock-on runtime state (design.md — missiles): which enemy
 * it's currently trying to (or has) locked onto, how much of the 5-second
 * acquisition window has elapsed, and how many missiles are left to fire.
 * Added only to ships whose type has missiles enabled
 * ({@code ShipTypeConfig#isMissileEnabled()}), unlike {@link RadarComponent}
 * (every ship) — same conditional-add pattern as {@link TurretComponent}.
 * <p>
 * Server-side only: {@code MissileLockSystem} ticks the progress timer and
 * decides acquisition/loss every tick, driven by
 * {@link de.mkoehler.starwars.sim.RadarDetection#isWithinCone} against this
 * ship's own cone radar. There's a single lock "slot" per ship regardless of
 * missile count, unlike {@link TurretComponent}'s list of independently-
 * tracking mounts — only one target can be locked (or being locked) at a
 * time.
 */
public class MissileLockComponent implements Component {

    private Entity lockTarget;
    private float lockProgressSeconds;
    private boolean lockAcquired;
    private int missileCount;

    /**
     * Creates a missile lock component with no current target.
     *
     * @param startingMissileCount the ship's starting missile count
     */
    public MissileLockComponent(int startingMissileCount) {
        this.missileCount = startingMissileCount;
    }

    /**
     * Returns the entity currently being locked onto (or already locked),
     * or {@code null} if none.
     *
     * @return the lock target entity, or {@code null}
     */
    public Entity getLockTarget() {
        return lockTarget;
    }

    public void setLockTarget(Entity lockTarget) {
        this.lockTarget = lockTarget;
    }

    /**
     * Returns how many seconds of uninterrupted cone-radar contact with the
     * current {@link #getLockTarget()} have accumulated so far.
     *
     * @return the elapsed lock-acquisition progress, in seconds
     */
    public float getLockProgressSeconds() {
        return lockProgressSeconds;
    }

    public void setLockProgressSeconds(float lockProgressSeconds) {
        this.lockProgressSeconds = lockProgressSeconds;
    }

    /**
     * Returns whether a full lock has been acquired on {@link #getLockTarget()}
     * — stays {@code true} as long as the target remains within this ship's
     * cone (design.md — missiles: "the lock is immediately lost if the enemy
     * manages to leave the radar cone," interpreted to apply after
     * acquisition too, not just during the 5-second window).
     *
     * @return {@code true} if the current target is fully locked
     */
    public boolean isLockAcquired() {
        return lockAcquired;
    }

    public void setLockAcquired(boolean lockAcquired) {
        this.lockAcquired = lockAcquired;
    }

    /**
     * Fully resets the lock state — no target, zero progress, not acquired.
     * Called whenever the current target leaves the cone, dies, or a missile
     * is successfully fired at it (so the next missile starts a fresh
     * acquisition).
     */
    public void resetLock() {
        lockTarget = null;
        lockProgressSeconds = 0f;
        lockAcquired = false;
    }

    /**
     * Returns how many missiles this ship has left to fire.
     *
     * @return the remaining missile count
     */
    public int getMissileCount() {
        return missileCount;
    }

    /**
     * Reduces the missile count by one. Callers must check
     * {@link #getMissileCount()}{@code > 0} first.
     */
    public void consumeMissile() {
        missileCount--;
    }
}
