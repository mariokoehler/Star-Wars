package de.mkoehler.starwars.net.messages;

/**
 * One projectile's position and velocity at the moment a
 * {@link WorldSnapshotMessage} was built. Not sent on its own, only as an
 * element of that message's projectile list.
 * <p>
 * Projectiles have no destroyed/expired notification of their own — a
 * client infers a projectile is gone simply by its id no longer appearing
 * in a subsequent snapshot, the same way presence/absence already drives
 * everything else in this message.
 * <p>
 * {@link #getTrackedTargetPlayerId()} (design.md — missiles) is the one
 * field that distinguishes a missile from an ordinary blaster bolt on the
 * wire — sentinel {@link de.mkoehler.starwars.sim.components.ProjectileComponent#NO_TRACKED_TARGET}
 * for a blaster bolt, the target's player id for a missile (fixed at
 * launch, unchanged even if that target is later destroyed). A client uses
 * it both to pick which sprite to draw and to drive the lock-reticle's
 * "in flight" stage — no separate projectile-type field needed.
 */
public class ProjectileState {

    private int projectileId;
    private int ownerPlayerId;
    private float x;
    private float y;
    private float velocityX;
    private float velocityY;
    private int trackedTargetPlayerId;
    private boolean turretShot;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public ProjectileState() {
    }

    /**
     * Creates a projectile state entry.
     *
     * @param projectileId  this projectile's id, unique among currently-alive projectiles
     * @param ownerPlayerId the id of the player who fired it
     * @param x             the projectile's position, in meters
     * @param y             the projectile's position, in meters
     * @param velocityX     the projectile's actual world-frame velocity, in meters/second —
     *                      muzzle speed plus whatever velocity the firing ship had at the
     *                      moment of firing ({@link de.mkoehler.starwars.sim.ProjectileFactory}),
     *                      not derivable client-side from a fixed weapon speed alone. Also doubles
     *                      as the client's render rotation (design.md 2.4's addendum) — a
     *                      projectile's true travel direction, unlike a ship's, can differ from
     *                      whatever angle it was fired at, once the firing ship's own velocity is
     *                      added on top of muzzle velocity.
     * @param velocityY     the projectile's actual world-frame velocity, in meters/second
     * @param trackedTargetPlayerId the enemy player id this projectile is tracking (a missile), or
     *                              {@link de.mkoehler.starwars.sim.components.ProjectileComponent#NO_TRACKED_TARGET}
     * @param turretShot            whether this projectile was fired by a turret mount rather than the
     *                              firing ship's own main gun (design.md — weapon sound: which sound
     *                              clip a client plays for it)
     */
    public ProjectileState(int projectileId, int ownerPlayerId, float x, float y,
                            float velocityX, float velocityY, int trackedTargetPlayerId, boolean turretShot) {
        this.projectileId = projectileId;
        this.ownerPlayerId = ownerPlayerId;
        this.x = x;
        this.y = y;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.trackedTargetPlayerId = trackedTargetPlayerId;
        this.turretShot = turretShot;
    }

    /**
     * Returns this projectile's id.
     *
     * @return the projectile id
     */
    public int getProjectileId() {
        return projectileId;
    }

    /**
     * Returns the id of the player who fired this projectile.
     *
     * @return the owning player's id
     */
    public int getOwnerPlayerId() {
        return ownerPlayerId;
    }

    /**
     * Returns the projectile's X position.
     *
     * @return the position, in meters
     */
    public float getX() {
        return x;
    }

    /**
     * Returns the projectile's Y position.
     *
     * @return the position, in meters
     */
    public float getY() {
        return y;
    }

    /**
     * Returns the projectile's actual world-frame X velocity.
     *
     * @return the velocity, in meters/second
     */
    public float getVelocityX() {
        return velocityX;
    }

    /**
     * Returns the projectile's actual world-frame Y velocity.
     *
     * @return the velocity, in meters/second
     */
    public float getVelocityY() {
        return velocityY;
    }

    /**
     * Returns the enemy player id this projectile is tracking.
     *
     * @return the tracked target's player id, or
     * {@link de.mkoehler.starwars.sim.components.ProjectileComponent#NO_TRACKED_TARGET} for an
     * ordinary, non-tracking projectile
     */
    public int getTrackedTargetPlayerId() {
        return trackedTargetPlayerId;
    }

    /**
     * Returns whether this projectile was fired by a turret mount, rather
     * than the firing ship's own main gun (design.md — weapon sound: which
     * sound clip a client plays for it).
     *
     * @return {@code true} for a turret-fired shot, {@code false} for a main-gun shot or a missile
     */
    public boolean isTurretShot() {
        return turretShot;
    }
}
