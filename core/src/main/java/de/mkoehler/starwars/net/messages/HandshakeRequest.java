package de.mkoehler.starwars.net.messages;

/**
 * Sent by a client immediately after a connection is established, identifying
 * itself to the server and requesting to proceed past the initial handshake.
 */
public class HandshakeRequest {

    private String displayName;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public HandshakeRequest() {
    }

    /**
     * Creates a handshake request.
     *
     * @param displayName the name the connecting player wishes to be shown as
     */
    public HandshakeRequest(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the display name supplied by the connecting client.
     *
     * @return the requested display name
     */
    public String getDisplayName() {
        return displayName;
    }
}
