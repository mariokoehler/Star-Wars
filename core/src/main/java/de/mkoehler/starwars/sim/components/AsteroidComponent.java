package de.mkoehler.starwars.sim.components;

import com.badlogic.ashley.core.Component;
import de.mkoehler.starwars.sim.AsteroidType;

/**
 * Marks an entity as an asteroid (design.md — asteroids): its id (unique
 * among currently-alive asteroids, broadcast so clients can track it across
 * snapshots the same way a ship/projectile id already does) and its
 * texture/hitbox type. Carries no health/damage state of its own —
 * asteroids are indestructible, only ships take damage from hitting one.
 */
public class AsteroidComponent implements Component {

    private final int asteroidId;
    private final AsteroidType type;

    /**
     * Creates an asteroid component.
     *
     * @param asteroidId this asteroid's id, unique among currently-alive asteroids
     * @param type       this asteroid's texture/hitbox type
     */
    public AsteroidComponent(int asteroidId, AsteroidType type) {
        this.asteroidId = asteroidId;
        this.type = type;
    }

    /**
     * Returns this asteroid's id.
     *
     * @return the asteroid id
     */
    public int getAsteroidId() {
        return asteroidId;
    }

    /**
     * Returns this asteroid's texture/hitbox type.
     *
     * @return the asteroid type
     */
    public AsteroidType getType() {
        return type;
    }
}
