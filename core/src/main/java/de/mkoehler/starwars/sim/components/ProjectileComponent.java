package de.mkoehler.starwars.sim.components;

import com.badlogic.ashley.core.Component;

/**
 * Marks an entity as a projectile: who fired it (so it can't damage its own
 * shooter, and so clients know which color to draw it), how much damage it
 * deals on a hit, and how much longer it survives before expiring on its own.
 */
public class ProjectileComponent implements Component {

    private final int projectileId;
    private final int ownerPlayerId;
    private final float damage;
    private float remainingLifetime;

    /**
     * Creates a projectile component.
     *
     * @param projectileId      this projectile's id, unique among currently-alive projectiles
     * @param ownerPlayerId     the id of the player who fired it
     * @param damage            damage dealt on a hit
     * @param remainingLifetime how much longer, in seconds, this projectile survives before expiring
     */
    public ProjectileComponent(int projectileId, int ownerPlayerId, float damage, float remainingLifetime) {
        this.projectileId = projectileId;
        this.ownerPlayerId = ownerPlayerId;
        this.damage = damage;
        this.remainingLifetime = remainingLifetime;
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
}
