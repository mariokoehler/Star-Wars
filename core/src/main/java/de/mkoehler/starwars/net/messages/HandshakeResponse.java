package de.mkoehler.starwars.net.messages;

/**
 * Sent by the server in reply to a {@link HandshakeRequest}, indicating whether
 * the client has been accepted onto the server.
 */
public class HandshakeResponse {

    private boolean accepted;
    private String message;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public HandshakeResponse() {
    }

    /**
     * Creates a handshake response.
     *
     * @param accepted whether the client's handshake was accepted
     * @param message  a human-readable message accompanying the result
     */
    public HandshakeResponse(boolean accepted, String message) {
        this.accepted = accepted;
        this.message = message;
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
}
