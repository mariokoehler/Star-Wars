package de.mkoehler.starwars.net.messages;

/**
 * Sent over the unreliable (UDP) channel in reply to a {@link UdpPingMessage},
 * echoing back the original timestamp unchanged.
 */
public class UdpPongMessage {

    private long timestamp;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public UdpPongMessage() {
    }

    /**
     * Creates a pong message.
     *
     * @param timestamp the timestamp copied from the originating {@link UdpPingMessage}
     */
    public UdpPongMessage(long timestamp) {
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
