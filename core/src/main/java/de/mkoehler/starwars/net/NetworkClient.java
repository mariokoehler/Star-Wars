package de.mkoehler.starwars.net;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.minlog.Log;
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
        NetworkLogging.useAbsoluteTimestamps();
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
     * <p>
     * Logs how long the call actually took (via {@link Log#info}) —
     * temporary diagnostic instrumentation added while investigating an
     * intermittent multi-second-to-multi-minute pause on this exact call
     * during a screen transition (CLAUDE.md). Notably, this KryoNet fork's
     * {@code Client.connect(...)} resolves {@code host} via a blocking
     * {@code InetAddress.getByName(host)} call <b>before</b> the
     * {@code timeoutMillis} budget below is ever applied to the actual
     * socket connect — so a slow/hung hostname resolution (DNS, VPN, a
     * flaky corporate network) is not bounded by {@code timeoutMillis} at
     * all, and would freeze whichever thread calls this (the render thread,
     * for every caller in this codebase) for as long as it takes.
     *
     * @param timeoutMillis maximum time to wait for the connection to complete, in milliseconds
     * @param host          the server's hostname or IP address
     * @param tcpPort       the server's TCP port
     * @param udpPort       the server's UDP port
     * @throws IOException if the connection could not be established
     */
    public void connect(int timeoutMillis, String host, int tcpPort, int udpPort) throws IOException {
        long startMillis = System.currentTimeMillis();
        try {
            client.connect(timeoutMillis, host, tcpPort, udpPort);
        } finally {
            Log.info("net-client", "connect(" + host + ":" + tcpPort + "/" + udpPort
                + ") took " + (System.currentTimeMillis() - startMillis) + "ms");
        }
    }

    /**
     * Closes the connection, if any, and stops the underlying network thread.
     * Blocks until the underlying socket(s) are actually closed.
     * <p>
     * Logs how long the call took, same temporary diagnostic reasoning as
     * {@link #connect}. <b>That logging is what root-caused the
     * screen-transition pause this method's own {@link #stopAsync()}
     * sibling now works around</b> (CLAUDE.md/design.md) — this call
     * itself does almost nothing (no I/O, no dispatch); the time is spent
     * inside KryoNet's {@code Client.close()}, in
     * {@code SocketChannel.close()}/{@code DatagramChannel.close()} — the
     * JDK's/OS's own socket teardown, observed on some machines/networks to
     * take anywhere from single-digit milliseconds to several seconds, for
     * reasons outside this codebase's control (Windows socket-close
     * behavior, antivirus/firewall interception, etc.). Prefer
     * {@link #stopAsync()} for any caller that can't afford to block on
     * that (e.g. a screen transitioning away) — this method remains for
     * callers (tests especially) that need the stop to have genuinely
     * completed before proceeding.
     */
    public void stop() {
        long startMillis = System.currentTimeMillis();
        client.stop();
        Log.info("net-client", "stop() took " + (System.currentTimeMillis() - startMillis) + "ms");
    }

    /**
     * Like {@link #stop()}, but runs it on a short-lived background thread
     * instead of blocking the caller — see {@link #stop()}'s Javadoc for
     * why that block can take seconds on some machines. A screen
     * transitioning away doesn't need to wait for its old connection to
     * actually finish closing before moving on: each screen's
     * {@code NetworkClient} owns an independent socket on its own
     * ephemeral port (already proven safe for multiple simultaneous
     * connections, design.md 3.4/CLAUDE.md), so there's no resource this
     * teardown needs to release before the next screen's own connection
     * can be established. Fire-and-forget — this method returns
     * immediately, before the stop has actually completed.
     */
    public void stopAsync() {
        Thread thread = new Thread(this::stop, "NetworkClient-stopAsync");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Sends a handshake request over the reliable channel, logging into (or
     * creating, design.md 3.6) a player account. Stamped with this build's
     * own {@link AppVersion#getVersion()} (design.md 3.10), checked by the
     * server before login is even attempted.
     *
     * @param login       the account's login name
     * @param password    the account's password, in plain text
     * @param displayName the name to identify this client with
     */
    public void sendHandshake(String login, String password, String displayName) {
        client.sendTCP(new HandshakeRequest(login, password, displayName, AppVersion.getVersion()));
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
     * Sends an arbitrary, registered message over the reliable (TCP) channel.
     *
     * @param object the message to send
     */
    public void sendTCP(Object object) {
        client.sendTCP(object);
    }

    /**
     * Sends an arbitrary, registered message over the unreliable (UDP) channel.
     *
     * @param object the message to send
     */
    public void sendUDP(Object object) {
        client.sendUDP(object);
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
