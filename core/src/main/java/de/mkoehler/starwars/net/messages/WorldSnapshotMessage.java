package de.mkoehler.starwars.net.messages;

/**
 * Broadcast by the server on every simulation tick: the position/orientation
 * of every currently-connected player's ship. Clients render every ship
 * (including their own — there is no client-side prediction yet, see
 * design.md 3.5) purely from these snapshots.
 */
public class WorldSnapshotMessage {

    private ShipState[] ships;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public WorldSnapshotMessage() {
    }

    /**
     * Creates a world snapshot.
     *
     * @param ships every currently-connected player's ship state
     */
    public WorldSnapshotMessage(ShipState[] ships) {
        this.ships = ships;
    }

    /**
     * Returns every currently-connected player's ship state.
     *
     * @return the ship states in this snapshot
     */
    public ShipState[] getShips() {
        return ships;
    }
}
