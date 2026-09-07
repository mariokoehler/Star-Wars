package de.mkoehler.starwars.net.messages;

import de.mkoehler.starwars.sim.ShipType;

/**
 * Sent by {@code ShipSelectionScreen} when the player presses <b>SPACE</b>
 * over a locked ship type they can currently afford (design.md — ship
 * unlocks), asking the server to add it to their account's unlocked ships.
 * The server re-validates affordability itself before granting this — the
 * client only ever shows the "affordable" (green) padlock as a UI
 * convenience, it isn't trusted on its own.
 */
public class UnlockShipRequest {

    private ShipType shipType;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public UnlockShipRequest() {
    }

    /**
     * Creates an unlock request.
     *
     * @param shipType the ship type to unlock
     */
    public UnlockShipRequest(ShipType shipType) {
        this.shipType = shipType;
    }

    /**
     * Returns the ship type requested to unlock.
     *
     * @return the requested ship type
     */
    public ShipType getShipType() {
        return shipType;
    }
}
