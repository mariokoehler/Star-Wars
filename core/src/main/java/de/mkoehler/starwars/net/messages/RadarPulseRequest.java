package de.mkoehler.starwars.net.messages;

/**
 * Sent by a client when the player presses "R" during gameplay, requesting
 * their ship's active radar pulse (design.md 2.14). Carries no payload — the
 * server identifies the requesting player from the {@code Connection} it
 * arrives on. Sent over the reliable channel, same reasoning as
 * {@link TurretToggleMessage}: a dropped request would silently deny the
 * player a pulse they thought they'd used, with no way to tell.
 * <p>
 * Harmless no-op if the player's ship type has the pulse disabled, isn't
 * currently spawned, or the pulse is still on cooldown — the server never
 * trusts this request alone, same as every other player-triggered action.
 */
public class RadarPulseRequest {
}
