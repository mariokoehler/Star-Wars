package de.mkoehler.starwars.sim.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;

/**
 * Marks an entity as an in-flight missile and holds the one thing that's
 * missile-specific beyond the generic {@link ProjectileComponent} every
 * projectile already carries: which entity it's actively steering toward.
 * Fixed at launch, never reassigned (design.md — missiles: a missile stays
 * focused on the enemy it locked onto). {@code MissileGuidanceSystem} reads
 * this every physics step; if the target entity is no longer alive, guidance
 * simply stops steering (the missile flies straight on its last heading)
 * rather than picking a new target or self-destructing early.
 */
public class MissileComponent implements Component {

    private final Entity target;

    /**
     * Creates a missile component.
     *
     * @param target the entity this missile is tracking
     */
    public MissileComponent(Entity target) {
        this.target = target;
    }

    /**
     * Returns the entity this missile is tracking.
     *
     * @return the target entity
     */
    public Entity getTarget() {
        return target;
    }
}
