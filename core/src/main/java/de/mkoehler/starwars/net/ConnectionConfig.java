package de.mkoehler.starwars.net;

/**
 * The four Connect Dialog fields (design.md 3.7/5.1), persisted locally
 * after a successful connect and used to pre-fill the dialog on next
 * launch, so returning to the game is a one-click "connect" after the
 * first time.
 * <p>
 * Plain mutable bean (public no-arg constructor, getters and setters) so
 * Jackson can (de)serialize it with no extra configuration, same convention
 * as this project's other Jackson beans.
 * <p>
 * Storing the raw password locally in plain text is a deliberate, accepted
 * trade-off (design.md 3.7) - this is a single-purpose game login, not meant
 * to double as a general-purpose credential.
 */
public class ConnectionConfig {

    private String serverHost;
    private String displayName;
    private String login;
    private String password;

    /**
     * No-arg constructor required by Jackson for deserialization.
     */
    public ConnectionConfig() {
    }

    /**
     * Creates a connection config.
     *
     * @param serverHost  the server's hostname or IP address
     * @param displayName the name to show this player as in-game
     * @param login       the account's login name
     * @param password    the account's password, in plain text
     */
    public ConnectionConfig(String serverHost, String displayName, String login, String password) {
        this.serverHost = serverHost;
        this.displayName = displayName;
        this.login = login;
        this.password = password;
    }

    public String getServerHost() {
        return serverHost;
    }

    public void setServerHost(String serverHost) {
        this.serverHost = serverHost;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
