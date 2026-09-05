package de.mkoehler.starwars.sim.components;

import com.badlogic.ashley.core.Component;
import de.mkoehler.starwars.sim.ShipType;

/**
 * Tracks which {@link ShipType} a ship entity is — lets systems that need a
 * ship's stats (e.g. {@link de.mkoehler.starwars.sim.systems.WeaponSystem}
 * for its attachment points/radius) look up the *correct* per-entity
 * {@link de.mkoehler.starwars.sim.ShipStats} instead of assuming a single
 * hardcoded ship type, now that more than one exists (design.md 5.1/6 "Ship
 * roster").
 */
public class ShipTypeComponent implements Component {

    private final ShipType shipType;

    /**
     * Creates a ship type component.
     *
     * @param shipType the entity's ship type
     */
    public ShipTypeComponent(ShipType shipType) {
        this.shipType = shipType;
    }

    /**
     * Returns the entity's ship type.
     *
     * @return the ship type
     */
    public ShipType getShipType() {
        return shipType;
    }
}
