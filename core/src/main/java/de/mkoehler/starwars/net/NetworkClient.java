package de.mkoehler.starwars.net;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import de.mkoehler.starwars.net.messages.HandshakeRequest;
import de.mkoehler.starwars.net.messages.HandshakeResponse;
import de.mkoehler.starwars.net.messages.TcpPingMessage;
import de.mkoehler.starwars.net.messages.TcpPongMessage;
import de.mkoehler.starwars.net.messages.UdpPingMessage;
import de.mkoehler.starwars.net.messages.UdpPongMessage;

import java.io.IOException;

/**
 * Thin wrapper around a KryoNet {@link Client} that wires up message
 * registration and exposes overridable handler methods for the connection
 * lifecycle and incoming messages.
 * <p>
 * This class has no dependency on libGDX, so it can be reused unchanged by any
 * platform this project's client runs on, and exercised directly in tests
 * without a graphics context.
 */
public class NetworkClient {

    private final Client client;

    /**
     * Creates a network client and starts its network thread; the client is not
     * yet connected to a server until {@link #connect(int, String, int, int)} is called.
     */
    public NetworkClient() {
        this.client = new Client(NetworkConstants.WRITE_BUFFER_SIZE, NetworkConstants.OBJECT_BUFFER_SIZE);
        MessageRegistry.register(client.getKryo());
        client.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                onConnected(connection);
            }

            @Override
            public void disconnected(Connection connection) {
                onDisconnected(connection);
            }

            @Override
            public void received(Connection connection, Object object) {
                onReceived(object);
            }
        });
        client.start();
    }

    /**
     * Connects to a server, blocking until the connection is established or the
     * timeout elapses.
     *
     * @param timeoutMillis maximum time to wait for the connection to complete, in milliseconds
     * @param host          the server's hostname or IP address
     * @param tcpPort       the server's TCP port
     * @param udpPort       the server's UDP port
     * @throws IOException if the connection could not be established
     */
    public void connect(int timeoutMillis, String host, int tcpPort, int udpPort) throws IOException {
        client.connect(timeoutMillis, host, tcpPort, udpPort);
    }

    /**
     * Closes the connection, if any, and stops the underlying network thread.
     */
    public void stop() {
        client.stop();
    }

    /**
     * Sends a handshake request over the reliable channel.
     *
     * @param displayName the name to identify this client with
     */
    public void sendHandshake(String displayName) {
        client.sendTCP(new HandshakeRequest(displayName));
    }

    /**
     * Sends a ping over the reliable (TCP) channel.
     *
     * @param timestamp a caller-supplied value echoed back unchanged in the reply,
     *                  typically {@link System#nanoTime()}
     */
    public void sendTcpPing(long timestamp) {
        client.sendTCP(new TcpPingMessage(timestamp));
    }

    /**
     * Sends a ping over the unreliable (UDP) channel.
     *
     * @param timestamp a caller-supplied value echoed back unchanged in the reply,
     *                  typically {@link System#nanoTime()}
     */
    public void sendUdpPing(long timestamp) {
        client.sendUDP(new UdpPingMessage(timestamp));
    }

    /**
     * Called once the connection to the server has been established. The
     * default implementation does nothing; subclasses may override it to add
     * behavior.
     *
     * @param connection the established connection
     */
    protected void onConnected(Connection connection) {
    }

    /**
     * Called when the connection to the server is closed, whether cleanly or
     * due to a timeout/error. The default implementation does nothing;
     * subclasses may override it to add behavior.
     *
     * @param connection the connection that was closed
     */
    protected void onDisconnected(Connection connection) {
    }

    /**
     * Called whenever a message is received from the server. Subclasses should
     * override this to react to {@link HandshakeResponse}, {@link TcpPongMessage}
     * and {@link UdpPongMessage} instances; the default implementation does
     * nothing.
     *
     * @param object the deserialized message
     */
    protected void onReceived(Object object) {
    }
}
