package de.mkoehler.starwars.net.messages;

/**
 * Sent by a client when the player presses "M" during gameplay, requesting
 * their ship fire a missile at its currently locked target (design.md —
 * missiles). Carries no payload — the server identifies the requesting
 * player from the {@code Connection} it arrives on, same shape as
 * {@link RadarPulseRequest}/{@link TurretToggleMessage}. Sent over the
 * reliable channel — a dropped request would silently deny the player a shot
 * they thought they'd fired, with no way to tell.
 * <p>
 * Harmless no-op if the player's ship type has no missiles enabled, isn't
 * currently spawned, has no lock acquired, or has no missiles left — the
 * server never trusts this request alone, same as every other
 * player-triggered action.
 */
public class MissileFireRequest {
}
