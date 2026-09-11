package de.mkoehler.starwars.sim.components;

import com.badlogic.ashley.core.Component;
import de.mkoehler.starwars.sim.PowerUpType;

/**
 * Marks an entity as a world pickup (design.md — power-ups): its id (unique
 * among currently-active power-ups, broadcast so clients can track it across
 * snapshots the same way an asteroid's id already does) and its effect type.
 * Carries no other state — a power-up has no health, and which ship (if any)
 * has just touched it is detected separately via a Box2D sensor contact, not
 * tracked here.
 */
public class PowerUpComponent implements Component {

    private final int powerUpId;
    private final PowerUpType type;

    /**
     * Creates a power-up component.
     *
     * @param powerUpId this power-up's id, unique among currently-active power-ups
     * @param type      this power-up's effect type
     */
    public PowerUpComponent(int powerUpId, PowerUpType type) {
        this.powerUpId = powerUpId;
        this.type = type;
    }

    /**
     * Returns this power-up's id.
     *
     * @return the power-up id
     */
    public int getPowerUpId() {
        return powerUpId;
    }

    /**
     * Returns this power-up's effect type.
     *
     * @return the power-up type
     */
    public PowerUpType getType() {
        return type;
    }
}
