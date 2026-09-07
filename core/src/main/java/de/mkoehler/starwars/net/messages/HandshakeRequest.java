package de.mkoehler.starwars.net.messages;

import de.mkoehler.starwars.net.AppVersion;

/**
 * Sent by a client immediately after a connection is established, requesting
 * to log into a player account (design.md 3.6/5.1's Connect Dialog).
 * <p>
 * Connecting with a {@link #getLogin()} the server hasn't seen before
 * auto-creates the account using the supplied password/display name;
 * connecting with an existing login requires the password to match, or the
 * server rejects the handshake (see {@link HandshakeResponse}). Carries no
 * ship type — logging in and spawning an actual ship are two different
 * moments, see {@link SpawnRequest}.
 * <p>
 * Also carries the client's {@link #getVersion() build version} (design.md
 * 3.10), checked against the server's own {@link AppVersion#getVersion()}
 * before login is even attempted — a mismatch is rejected regardless of
 * whether the credentials would otherwise be valid.
 */
public class HandshakeRequest {

    private String login;
    private String password;
    private String displayName;
    private String version;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public HandshakeRequest() {
    }

    /**
     * Creates a handshake request.
     *
     * @param login       the account's unique login name
     * @param password    the account's password, in plain text (design.md 3.6 -
     *                    hashed server-side before ever being persisted, never
     *                    stored or compared in plain text)
     * @param displayName the name other players should see this player as
     * @param version     the client's build version (design.md 3.10), normally
     *                    {@link AppVersion#getVersion()} — callers pass it in
     *                    explicitly rather than this class reading it itself, so
     *                    it stays a plain data holder
     */
    public HandshakeRequest(String login, String password, String displayName, String version) {
        this.login = login;
        this.password = password;
        this.displayName = displayName;
        this.version = version;
    }

    /**
     * Returns the account's login name.
     *
     * @return the login name
     */
    public String getLogin() {
        return login;
    }

    /**
     * Returns the account's password, in plain text.
     *
     * @return the plain-text password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Returns the display name supplied by the connecting client.
     *
     * @return the requested display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the client's build version, to be checked against the server's
     * own {@link AppVersion#getVersion()}.
     *
     * @return the client's build version
     */
    public String getVersion() {
        return version;
    }
}
