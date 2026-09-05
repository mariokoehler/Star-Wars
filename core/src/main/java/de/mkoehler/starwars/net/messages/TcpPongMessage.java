package de.mkoehler.starwars.net.messages;

/**
 * Sent over the reliable (TCP) channel in reply to a {@link TcpPingMessage},
 * echoing back the original timestamp unchanged.
 */
public class TcpPongMessage {

    private long timestamp;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public TcpPongMessage() {
    }

    /**
     * Creates a pong message.
     *
     * @param timestamp the timestamp copied from the originating {@link TcpPingMessage}
     */
    public TcpPongMessage(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Returns the timestamp copied from the originating ping.
     *
     * @return the echoed timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }
}
