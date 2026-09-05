package de.mkoehler.starwars.net.messages;

/**
 * Sent over the unreliable (UDP) channel to verify that channel is alive and to
 * measure round-trip time; the server echoes it back as a {@link UdpPongMessage}.
 */
public class UdpPingMessage {

    private long timestamp;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public UdpPingMessage() {
    }

    /**
     * Creates a ping message.
     *
     * @param timestamp a caller-supplied value echoed back unchanged in the reply
     */
    public UdpPingMessage(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Returns the timestamp supplied when this ping was created.
     *
     * @return the ping's timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }
}
