package de.mkoehler.starwars.net;

/**
 * Fixed values shared by every {@link NetworkServer} and {@link NetworkClient}
 * instance in this project.
 */
public final class NetworkConstants {

    /**
     * The default TCP port the dedicated server listens on for the reliable channel.
     */
    public static final int TCP_PORT = 45625;

    /**
     * The default UDP port the dedicated server listens on for the unreliable channel.
     */
    public static final int UDP_PORT = 45626;

    /**
     * Size, in bytes, of the buffer used to write objects to the network.
     */
    public static final int WRITE_BUFFER_SIZE = 16384;

    /**
     * Size, in bytes, of the buffer used to read/write a single serialized object.
     */
    public static final int OBJECT_BUFFER_SIZE = 2048;

    /**
     * Maximum time, in milliseconds, a client waits for a connection attempt to
     * complete before giving up.
     */
    public static final int CONNECTION_TIMEOUT_MILLIS = 5000;

    /**
     * Placeholder rate, in updates per second, at which the dedicated server's
     * simulation loop runs. Not yet a final decision; see design.md 3.5.
     */
    public static final int SIMULATION_TICK_RATE_HZ = 30;

    private NetworkConstants() {
    }
}
