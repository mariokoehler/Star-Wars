package de.mkoehler.starwars.net.messages;

import de.mkoehler.starwars.sim.ShipType;

/**
 * One ship's position, orientation, velocity, hull/shield status, and ship
 * type at the moment a {@link WorldSnapshotMessage} was built. Not sent on
 * its own, only as an element of that message's ship list.
 * <p>
 * Velocity is included (not just position/angle) so a client reconciling its
 * own predicted ship against this authoritative state can correct both —
 * correcting position alone while leaving a mismatched velocity in place
 * would just cause the ship to immediately drift out of sync again.
 * <p>
 * Hull/shield current and max (design.md 2.5) are included for every ship,
 * not just the local player's own, even though only the local player's HUD
 * ({@link de.mkoehler.starwars.render.ShipStatusHud}) reads them today —
 * broadcasting them for everyone costs little and leaves the door open for
 * a future enemy health readout without a protocol change.
 * <p>
 * The ship type is included so a client can render *other* players' ships
 * with the correct sprite/size (design.md 5.1) — other clients only ever
 * learn a ship's type from here, since {@link ShipSpawnedMessage} (which
 * also carries it) is only ever sent to the owning player.
 * <p>
 * {@link #getTurretAimAngles()} carries the current world-space aim angle
 * of every turret this ship has (design.md — turret weapons), in the same
 * order as that ship type's {@code "TURRET"} attachment points — empty for
 * a ship type with no turrets. Turret aim/targeting is entirely
 * server-simulated, never predicted (like projectiles), so this is the only
 * way any client, including the turret's own owner, learns where it's
 * currently pointed.
 * <p>
 * {@link #getRadarPulseCooldownRemaining()} (design.md 2.14) is broadcast
 * for every ship, not just the local player's own, same low-cost-now
 * reasoning as hull/shield/turret aim above — a future "can I pulse again"
 * HUD readout needs it, even though nothing reads it yet.
 * <p>
 * {@link #getMissileLockTargetPlayerId()}/{@link #isMissileLockAcquired()}
 * (design.md — missiles) carry a missile-capable ship's current lock state,
 * same broadcast-for-every-ship-even-if-only-locally-meaningful convention —
 * only the local player's own entry actually drives their lock-reticle HUD,
 * but it costs nothing to send for everyone and avoids a protocol change
 * later. Sentinel {@code -1}/{@code false} for a ship with no current lock,
 * or no missiles enabled at all.
 * <p>
 * {@link #isTargetedByMissileLock()}/{@link #isTargetedByMissileLockAcquired()}
 * are the mirror image, from the victim's side: whether *any* enemy ship
 * currently has this ship as their lock target, and whether any of those
 * locks is fully acquired — aggregated across every attacker rather than
 * naming one, since more than one enemy could in principle be locking the
 * same target at once. Lets the targeted player see the same lock-reticle
 * animation on their own ship that the attacker sees on them, rather than
 * being caught completely unaware a missile is coming (design.md —
 * missiles' addendum; the original pass only rendered the reticle for the
 * attacker).
 * <p>
 * This ship state's own presence in a {@link WorldSnapshotMessage} is
 * itself meaningful now (design.md 2.14): the server only ever includes a
 * ship here if the receiving player's radar currently detects it (or it's
 * their own ship, always included) — not every currently-connected ship
 * unconditionally, the way it was before radar existed.
 * <p>
 * {@link #isThrusting()} (design.md — engine particle effects) carries
 * whether this ship is currently holding its forward-thrust input, so
 * every client can render the correct ship's engine glow, not just the
 * local player's own — same broadcast-for-every-ship convention as
 * hull/shield/turret aim above.
 */
public class ShipState {

    /**
     * Sentinel {@link #getMissileLockTargetPlayerId()} value for "no current
     * lock target."
     */
    public static final int NO_MISSILE_LOCK_TARGET = -1;

    private int playerId;
    private float x;
    private float y;
    private float angle;
    private float velocityX;
    private float velocityY;
    private float angularVelocity;
    private float hullCurrent;
    private float hullMax;
    private float shieldCurrent;
    private float shieldMax;
    private ShipType shipType;
    private float[] turretAimAngles;
    private float radarPulseCooldownRemaining;
    private int missileLockTargetPlayerId;
    private boolean missileLockAcquired;
    private boolean targetedByMissileLock;
    private boolean targetedByMissileLockAcquired;
    private boolean thrusting;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public ShipState() {
    }

    /**
     * Creates a ship state entry.
     *
     * @param playerId        the id of the player this ship belongs to
     * @param x               the ship's position, in meters
     * @param y               the ship's position, in meters
     * @param angle           the ship's facing angle, in radians
     * @param velocityX       the ship's linear velocity, in meters/second
     * @param velocityY       the ship's linear velocity, in meters/second
     * @param angularVelocity the ship's angular velocity, in radians/second
     * @param hullCurrent     the ship's current hull health
     * @param hullMax         the ship's maximum hull health
     * @param shieldCurrent   the ship's current shield strength
     * @param shieldMax       the ship's maximum shield capacity
     * @param shipType        the ship's type
     * @param turretAimAngles this ship's turrets' current aim angles, in radians, one per
     *                        {@code "TURRET"} attachment point in authored order; empty if none
     * @param radarPulseCooldownRemaining how much longer until this ship's radar pulse (design.md
     *                                    2.14) can be triggered again; {@code <= 0} means ready
     * @param missileLockTargetPlayerId   the player id of this ship's current missile lock target,
     *                                    or {@link #NO_MISSILE_LOCK_TARGET} if none
     * @param missileLockAcquired         whether that lock is fully acquired (vs. still being acquired)
     * @param targetedByMissileLock       whether any enemy ship currently has this ship as their
     *                                    lock target (acquiring or acquired)
     * @param targetedByMissileLockAcquired whether any such lock on this ship is fully acquired
     * @param thrusting                   whether this ship is currently holding its forward-thrust input
     */
    public ShipState(int playerId, float x, float y, float angle,
                      float velocityX, float velocityY, float angularVelocity,
                      float hullCurrent, float hullMax, float shieldCurrent, float shieldMax,
                      ShipType shipType, float[] turretAimAngles, float radarPulseCooldownRemaining,
                      int missileLockTargetPlayerId, boolean missileLockAcquired,
                      boolean targetedByMissileLock, boolean targetedByMissileLockAcquired,
                      boolean thrusting) {
        this.playerId = playerId;
        this.x = x;
        this.y = y;
        this.angle = angle;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.angularVelocity = angularVelocity;
        this.hullCurrent = hullCurrent;
        this.hullMax = hullMax;
        this.shieldCurrent = shieldCurrent;
        this.shieldMax = shieldMax;
        this.shipType = shipType;
        this.turretAimAngles = turretAimAngles;
        this.radarPulseCooldownRemaining = radarPulseCooldownRemaining;
        this.missileLockTargetPlayerId = missileLockTargetPlayerId;
        this.missileLockAcquired = missileLockAcquired;
        this.targetedByMissileLock = targetedByMissileLock;
        this.targetedByMissileLockAcquired = targetedByMissileLockAcquired;
        this.thrusting = thrusting;
    }

    /**
     * Returns the id of the player this ship belongs to.
     *
     * @return the owning player's id
     */
    public int getPlayerId() {
        return playerId;
    }

    /**
     * Returns the ship's X position.
     *
     * @return the position, in meters
     */
    public float getX() {
        return x;
    }

    /**
     * Returns the ship's Y position.
     *
     * @return the position, in meters
     */
    public float getY() {
        return y;
    }

    /**
     * Returns the ship's facing angle.
     *
     * @return the angle, in radians
     */
    public float getAngle() {
        return angle;
    }

    /**
     * Returns the ship's linear velocity on the X axis.
     *
     * @return the velocity, in meters/second
     */
    public float getVelocityX() {
        return velocityX;
    }

    /**
     * Returns the ship's linear velocity on the Y axis.
     *
     * @return the velocity, in meters/second
     */
    public float getVelocityY() {
        return velocityY;
    }

    /**
     * Returns the ship's angular velocity.
     *
     * @return the angular velocity, in radians/second
     */
    public float getAngularVelocity() {
        return angularVelocity;
    }

    /**
     * Returns the ship's current hull health.
     *
     * @return the current hull health
     */
    public float getHullCurrent() {
        return hullCurrent;
    }

    /**
     * Returns the ship's maximum hull health.
     *
     * @return the maximum hull health
     */
    public float getHullMax() {
        return hullMax;
    }

    /**
     * Returns the ship's current shield strength.
     *
     * @return the current shield strength
     */
    public float getShieldCurrent() {
        return shieldCurrent;
    }

    /**
     * Returns the ship's maximum shield capacity.
     *
     * @return the maximum shield capacity
     */
    public float getShieldMax() {
        return shieldMax;
    }

    /**
     * Returns the ship's type.
     *
     * @return the ship type
     */
    public ShipType getShipType() {
        return shipType;
    }

    /**
     * Returns this ship's turrets' current aim angles.
     *
     * @return the aim angles, in radians, one per {@code "TURRET"} attachment
     * point in authored order; empty for a ship type with no turrets
     */
    public float[] getTurretAimAngles() {
        return turretAimAngles;
    }

    /**
     * Returns how much longer until this ship's radar pulse can be
     * triggered again (design.md 2.14).
     *
     * @return the remaining cooldown, in seconds; {@code <= 0} means ready
     */
    public float getRadarPulseCooldownRemaining() {
        return radarPulseCooldownRemaining;
    }

    /**
     * Returns the player id of this ship's current missile lock target.
     *
     * @return the target's player id, or {@link #NO_MISSILE_LOCK_TARGET} if none
     */
    public int getMissileLockTargetPlayerId() {
        return missileLockTargetPlayerId;
    }

    /**
     * Returns whether this ship's current missile lock is fully acquired
     * (vs. still being acquired, or there being no lock at all).
     *
     * @return {@code true} if a lock is fully acquired
     */
    public boolean isMissileLockAcquired() {
        return missileLockAcquired;
    }

    /**
     * Returns whether any enemy ship currently has this ship as their
     * missile lock target (acquiring or already acquired).
     *
     * @return {@code true} if this ship is currently targeted
     */
    public boolean isTargetedByMissileLock() {
        return targetedByMissileLock;
    }

    /**
     * Returns whether any lock on this ship (see {@link #isTargetedByMissileLock()})
     * is fully acquired.
     *
     * @return {@code true} if at least one enemy has fully acquired a lock on this ship
     */
    public boolean isTargetedByMissileLockAcquired() {
        return targetedByMissileLockAcquired;
    }

    /**
     * Returns whether this ship is currently holding its forward-thrust
     * input — drives every client's rendering of this ship's engine
     * particle effect(s), if it has any configured.
     *
     * @return {@code true} if this ship is currently thrusting
     */
    public boolean isThrusting() {
        return thrusting;
    }
}
