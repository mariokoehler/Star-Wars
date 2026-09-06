package de.mkoehler.starwars.net.messages;

/**
 * Sent back to a client whose {@link LeaveMatchRequest} was denied because
 * the player is still within design.md 2.3's 20-second combat-lock window
 * (fired or been hit too recently). Carries no payload — the client already
 * knows why, and just needs to show the fixed warning message/sound; the
 * player's ship is left completely untouched.
 */
public class LeaveMatchDeniedMessage {
}
