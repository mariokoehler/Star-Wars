package de.mkoehler.starwars.net.messages;

/**
 * Sent by a client when the player presses ESC during gameplay, asking to
 * leave the match and self-destruct (design.md 2.3). Carries no payload —
 * the server identifies the requesting player from the {@code Connection}
 * it arrives on. Sent over the reliable channel, since a dropped or
 * reordered leave request would be a confusing silent no-op for the player.
 * <p>
 * The server either grants the request (destroying the ship and broadcasting
 * a {@code ShipDestroyedMessage}, same as a combat death, per design.md
 * 2.3's "visually indistinguishable" requirement) or denies it with a
 * {@link LeaveMatchDeniedMessage} if the player is still within the
 * combat-lock window.
 */
public class LeaveMatchRequest {
}
