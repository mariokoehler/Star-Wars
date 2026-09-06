package de.mkoehler.starwars;

/**
 * The validated identity a player connected to the server with (design.md
 * 5.1's Connect Dialog) - carried from {@link ConnectScreen} through
 * {@link ShipSelectionScreen} into {@link Client}, since all three screens
 * belong to the same logged-in session.
 * <p>
 * {@link ConnectScreen} has already performed one successful handshake with
 * these exact values by the time this is created; {@link Client} re-sends
 * them on its own fresh connection when a match actually starts (design.md
 * 3.6 - login is idempotent, so re-authenticating is harmless) rather than
 * this record carrying a live {@code NetworkClient} across screens.
 *
 * @param serverHost  the server's hostname or IP address
 * @param login       the account's login name
 * @param password    the account's password, in plain text (design.md 3.7 -
 *                    kept in memory for the lifetime of this session the
 *                    same way it's accepted to be persisted to local disk)
 * @param displayName the name to show this player as in-game
 */
public record ConnectionInfo(String serverHost, String login, String password, String displayName) {
}
