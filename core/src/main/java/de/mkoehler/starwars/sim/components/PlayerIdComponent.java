package de.mkoehler.starwars.sim.components;

import com.badlogic.ashley.core.Component;

/**
 * Associates a server-side ship entity with the id of the player it belongs
 * to (the owning KryoNet {@code Connection}'s id — see design.md 3.5).
 */
public class PlayerIdComponent implements Component {

    private final int playerId;

    /**
     * Creates a component for the given player id.
     *
     * @param playerId the owning player's id
     */
    public PlayerIdComponent(int playerId) {
        this.playerId = playerId;
    }

    /**
     * Returns the owning player's id.
     *
     * @return the player id
     */
    public int getPlayerId() {
        return playerId;
    }
}
