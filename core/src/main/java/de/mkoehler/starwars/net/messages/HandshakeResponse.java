package de.mkoehler.starwars.net.messages;

import de.mkoehler.starwars.sim.ShipType;

/**
 * Sent by the server in reply to a {@link HandshakeRequest}, indicating whether
 * the client has been accepted onto the server.
 * <p>
 * On acceptance, also carries the account's total XP and currently-unlocked
 * ship types (design.md — ship unlocks) — {@code ShipSelectionScreen} reads
 * these from its own fresh handshake (design.md 3.6 — logging in again is
 * harmless) to decide which padlock, if any, to show over each ship. Both
 * are meaningless (and left at their defaults) on a rejection; the base
 * {@link de.mkoehler.starwars.net.NetworkServer}'s own generic, account-free
 * default handshake handling never sets them either.
 */
public class HandshakeResponse {

    private static final ShipType[] NO_UNLOCKED_SHIPS = new ShipType[0];

    private boolean accepted;
    private String message;
    private int xp;
    private ShipType[] unlockedShips = NO_UNLOCKED_SHIPS;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public HandshakeResponse() {
    }

    /**
     * Creates a handshake response with no account data - the rejection
     * path, and the base {@link de.mkoehler.starwars.net.NetworkServer}'s
     * generic default handshake handling.
     *
     * @param accepted whether the client's handshake was accepted
     * @param message  a human-readable message accompanying the result
     */
    public HandshakeResponse(boolean accepted, String message) {
        this.accepted = accepted;
        this.message = message;
    }

    /**
     * Creates a handshake response carrying the logged-into account's XP/
     * unlocked ships.
     *
     * @param accepted      whether the client's handshake was accepted
     * @param message       a human-readable message accompanying the result
     * @param xp            the account's total (never-decremented) XP
     * @param unlockedShips the account's full current set of unlocked ship
     *                      types
     */
    public HandshakeResponse(boolean accepted, String message, int xp, ShipType[] unlockedShips) {
        this.accepted = accepted;
        this.message = message;
        this.xp = xp;
        this.unlockedShips = unlockedShips;
    }

    /**
     * Returns whether the server accepted the client's handshake.
     *
     * @return {@code true} if the client may proceed, {@code false} otherwise
     */
    public boolean isAccepted() {
        return accepted;
    }

    /**
     * Returns a human-readable message accompanying the handshake result, such
     * as a rejection reason.
     *
     * @return the handshake result message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the logged-into account's total XP - meaningless if
     * {@link #isAccepted()} is {@code false}.
     *
     * @return the account's total XP
     */
    public int getXp() {
        return xp;
    }

    /**
     * Returns the logged-into account's full current set of unlocked ship
     * types - meaningless if {@link #isAccepted()} is {@code false}.
     *
     * @return the unlocked ship types
     */
    public ShipType[] getUnlockedShips() {
        return unlockedShips;
    }
}
