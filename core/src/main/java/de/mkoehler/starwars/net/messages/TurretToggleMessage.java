package de.mkoehler.starwars.net.messages;

/**
 * Sent by a client when the player presses "T" during gameplay, toggling
 * their ship's turret(s) on/off (design.md — turret weapons). Carries no
 * payload — the server identifies the requesting player from the
 * {@code Connection} it arrives on, and simply flips the current state
 * ({@code TurretComponent#toggle()}), so there's no risk of the client and
 * server disagreeing on which way to toggle. Sent over the reliable
 * channel, since a dropped toggle would silently leave the turret in the
 * wrong state until the player pressed "T" again.
 * <p>
 * Harmless no-op if the player's ship type has no turrets at all, or isn't
 * currently spawned.
 */
public class TurretToggleMessage {
}
