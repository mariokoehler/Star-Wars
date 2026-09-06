package de.mkoehler.starwars.net.messages;

import de.mkoehler.starwars.sim.ShipType;

/**
 * Sent by a client once it has an accepted {@link HandshakeResponse} and the
 * player has picked a ship on the Ship Selection screen (design.md 5.1),
 * asking the server to actually spawn a ship for this connection.
 * <p>
 * Deliberately separate from {@link HandshakeRequest}: the handshake only
 * authenticates the player's account (design.md 3.6) and must not have the
 * side effect of spawning a real ship into the world — logging in and
 * actually joining a match are two different moments (a rejected password
 * is checked well before a ship type is even chosen).
 */
public class SpawnRequest {

    private ShipType shipType;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public SpawnRequest() {
    }

    /**
     * Creates a spawn request.
     *
     * @param shipType the ship type selected on the Ship Selection screen to spawn as
     */
    public SpawnRequest(ShipType shipType) {
        this.shipType = shipType;
    }

    /**
     * Returns the ship type requested for this player's ship.
     *
     * @return the requested ship type
     */
    public ShipType getShipType() {
        return shipType;
    }
}
