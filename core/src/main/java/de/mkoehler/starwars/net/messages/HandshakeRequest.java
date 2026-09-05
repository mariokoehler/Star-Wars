package de.mkoehler.starwars.net.messages;

import de.mkoehler.starwars.sim.ShipType;

/**
 * Sent by a client immediately after a connection is established, identifying
 * itself to the server and requesting to proceed past the initial handshake.
 */
public class HandshakeRequest {

    private String displayName;
    private ShipType shipType;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public HandshakeRequest() {
    }

    /**
     * Creates a handshake request.
     *
     * @param displayName the name the connecting player wishes to be shown as
     * @param shipType    the ship type selected on the Ship Selection screen
     *                    (design.md 5.1) to spawn the player's ship as
     */
    public HandshakeRequest(String displayName, ShipType shipType) {
        this.displayName = displayName;
        this.shipType = shipType;
    }

    /**
     * Returns the display name supplied by the connecting client.
     *
     * @return the requested display name
     */
    public String getDisplayName() {
        return displayName;
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
