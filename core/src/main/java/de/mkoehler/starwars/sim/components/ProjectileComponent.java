package de.mkoehler.starwars.sim.components;

import com.badlogic.ashley.core.Component;

/**
 * Marks an entity as a projectile: who fired it (so it can't damage its own
 * shooter, and so clients know which color to draw it), how much damage it
 * deals on a hit, how much longer it survives before expiring on its own,
 * and (missiles only, design.md — missiles) which enemy it was fired at.
 * <p>
 * A missile's {@link #getTrackedTargetPlayerId()} is set once at launch and
 * never changes, even if the target is later destroyed — it's the one field
 * that distinguishes a missile from an ordinary blaster bolt on the wire, so
 * both the sprite to draw and the lock-reticle's "in flight" stage
 * ({@code Client}) key off it rather than a separate projectile-type enum.
 */
public class ProjectileComponent implements Component {

    /**
     * Sentinel {@link #getTrackedTargetPlayerId()} value for a projectile
     * that isn't tracking anyone — every ordinary blaster bolt.
     */
    public static final int NO_TRACKED_TARGET = -1;

    private final int projectileId;
    private final int ownerPlayerId;
    private final float damage;
    private final int trackedTargetPlayerId;
    private float remainingLifetime;

    /**
     * Creates a projectile component.
     *
     * @param projectileId           this projectile's id, unique among currently-alive projectiles
     * @param ownerPlayerId          the id of the player who fired it
     * @param damage                 damage dealt on a hit
     * @param remainingLifetime      how much longer, in seconds, this projectile survives before expiring
     * @param trackedTargetPlayerId  the id of the enemy player this projectile is tracking (a missile),
     *                               or {@link #NO_TRACKED_TARGET} for an ordinary, non-tracking projectile
     */
    public ProjectileComponent(int projectileId, int ownerPlayerId, float damage, float remainingLifetime,
                                int trackedTargetPlayerId) {
        this.projectileId = projectileId;
        this.ownerPlayerId = ownerPlayerId;
        this.damage = damage;
        this.remainingLifetime = remainingLifetime;
        this.trackedTargetPlayerId = trackedTargetPlayerId;
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
     * Returns the damage this projectile deals on a hit.
     *
     * @return the damage
     */
    public float getDamage() {
        return damage;
    }

    /**
     * Returns how much longer this projectile survives before expiring.
     *
     * @return the remaining lifetime, in seconds
     */
    public float getRemainingLifetime() {
        return remainingLifetime;
    }

    /**
     * Reduces the remaining lifetime by the given amount.
     *
     * @param deltaTime time elapsed, in seconds
     */
    public void tickLifetime(float deltaTime) {
        remainingLifetime -= deltaTime;
    }

    /**
     * Returns the id of the enemy player this projectile is tracking, or
     * {@link #NO_TRACKED_TARGET} if it isn't (every ordinary blaster bolt).
     * Fixed at launch — a missile whose target dies mid-flight keeps this
     * id, it just stops steering (design.md — missiles).
     *
     * @return the tracked target's player id, or {@link #NO_TRACKED_TARGET}
     */
    public int getTrackedTargetPlayerId() {
        return trackedTargetPlayerId;
    }
}
