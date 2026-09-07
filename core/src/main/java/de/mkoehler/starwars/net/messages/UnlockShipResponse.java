package de.mkoehler.starwars.net.messages;

import de.mkoehler.starwars.sim.ShipType;

/**
 * Sent by the server in reply to an {@link UnlockShipRequest} (design.md —
 * ship unlocks). Always carries the account's current XP and full unlocked-
 * ships list, whether the request was granted, already redundant (the ship
 * was already unlocked), or denied — {@code ShipSelectionScreen} just
 * replaces its local copy of both with whatever comes back, rather than
 * tracking success/failure as a separate code path.
 */
public class UnlockShipResponse {

    private boolean success;
    private String message;
    private int xp;
    private ShipType[] unlockedShips;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public UnlockShipResponse() {
    }

    /**
     * Creates an unlock response.
     *
     * @param success       whether the ship ends up unlocked (either just
     *                      now, or already was)
     * @param message       a human-readable message, e.g. a rejection reason
     * @param xp            the account's total (never-decremented) XP
     * @param unlockedShips the account's full current set of unlocked ship
     *                      types
     */
    public UnlockShipResponse(boolean success, String message, int xp, ShipType[] unlockedShips) {
        this.success = success;
        this.message = message;
        this.xp = xp;
        this.unlockedShips = unlockedShips;
    }

    /**
     * Returns whether the requested ship type ends up unlocked.
     *
     * @return {@code true} if the ship type is unlocked (now or already)
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Returns a human-readable message accompanying the result.
     *
     * @return the result message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the account's total XP.
     *
     * @return the account's total XP
     */
    public int getXp() {
        return xp;
    }

    /**
     * Returns the account's full current set of unlocked ship types.
     *
     * @return the unlocked ship types
     */
    public ShipType[] getUnlockedShips() {
        return unlockedShips;
    }
}
