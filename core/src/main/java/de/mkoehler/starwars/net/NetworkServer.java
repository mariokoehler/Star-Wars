package de.mkoehler.starwars.net;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import de.mkoehler.starwars.net.messages.HandshakeRequest;
import de.mkoehler.starwars.net.messages.HandshakeResponse;
import de.mkoehler.starwars.net.messages.TcpPingMessage;
import de.mkoehler.starwars.net.messages.TcpPongMessage;
import de.mkoehler.starwars.net.messages.UdpPingMessage;
import de.mkoehler.starwars.net.messages.UdpPongMessage;

import java.io.IOException;

/**
 * Thin wrapper around a KryoNet {@link Server} that wires up message
 * registration and the connection lifecycle/handshake/ping-pong handling
 * shared by every platform this project's dedicated server runs on.
 * <p>
 * This class has no dependency on libGDX, so it can be started, exercised and
 * stopped directly in tests without a headless application context.
 */
public class NetworkServer {

    private final Server server;

    /**
     * Creates a network server bound to no port yet; call {@link #start(int, int)}
     * to begin listening for connections.
     */
    public NetworkServer() {
        this.server = new Server(NetworkConstants.WRITE_BUFFER_SIZE, NetworkConstants.OBJECT_BUFFER_SIZE);
        MessageRegistry.register(server.getKryo());
        server.addListener(new Listener() {
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
                onReceived(connection, object);
            }
        });
    }

    /**
     * Starts the underlying network threads and binds the given TCP and UDP ports.
     *
     * @param tcpPort the TCP port to listen on
     * @param udpPort the UDP port to listen on
     * @throws IOException if either port could not be bound
     */
    public void start(int tcpPort, int udpPort) throws IOException {
        server.start();
        server.bind(tcpPort, udpPort);
    }

    /**
     * Stops the underlying network threads and closes all open connections.
     */
    public void stop() {
        server.stop();
    }

    /**
     * Called whenever a new client connection is established, before any
     * handshake has taken place. The default implementation does nothing;
     * subclasses may override it to add behavior.
     *
     * @param connection the newly established connection
     */
    protected void onConnected(Connection connection) {
    }

    /**
     * Called whenever a client disconnects, whether cleanly or due to a
     * timeout/error. The default implementation does nothing; subclasses may
     * override it to add behavior.
     *
     * @param connection the connection that was closed
     */
    protected void onDisconnected(Connection connection) {
    }

    /**
     * Dispatches an incoming message to the appropriate handler based on its
     * runtime type. Subclasses may override this for full control over
     * dispatch, or override {@link #handleHandshake(Connection, HandshakeRequest)}
     * to only customize handshake behavior.
     *
     * @param connection the connection the message was received on
     * @param object     the deserialized message
     */
    protected void onReceived(Connection connection, Object object) {
        if (object instanceof HandshakeRequest request) {
            connection.sendTCP(handleHandshake(connection, request));
        } else if (object instanceof TcpPingMessage ping) {
            connection.sendTCP(new TcpPongMessage(ping.getTimestamp()));
        } else if (object instanceof UdpPingMessage ping) {
            connection.sendUDP(new UdpPongMessage(ping.getTimestamp()));
        }
    }

    /**
     * Produces the response to a client's handshake request. The default
     * implementation accepts every request unconditionally; subclasses may
     * override this to add validation, such as protocol version checks or
     * account authentication.
     *
     * @param connection the connection the request came from
     * @param request    the handshake request
     * @return the response to send back to the client
     */
    protected HandshakeResponse handleHandshake(Connection connection, HandshakeRequest request) {
        return new HandshakeResponse(true, "Welcome, " + request.getDisplayName() + ".");
    }
}
