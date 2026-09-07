package de.mkoehler.starwars.net;

import com.esotericsoftware.kryonet.Connection;
import de.mkoehler.starwars.net.messages.HandshakeRequest;
import de.mkoehler.starwars.net.messages.HandshakeResponse;
import de.mkoehler.starwars.net.messages.TcpPongMessage;
import de.mkoehler.starwars.net.messages.UdpPongMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link NetworkServer} and {@link NetworkClient} against each other
 * over real loopback sockets: the connection lifecycle, the handshake
 * exchange, and a ping/pong round trip over both the TCP and UDP channels.
 */
class NetworkServerClientIntegrationTest {

    private static final long AWAIT_SECONDS = 5;

    private NetworkServer server;
    private NetworkClient client;

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.stop();
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void handshakeAndPingPongRoundTripSucceed() throws Exception {
        int[] ports = findTwoFreePorts();
        int tcpPort = ports[0];
        int udpPort = ports[1];

        server = new NetworkServer();
        server.start(tcpPort, udpPort);

        CountDownLatch connectedLatch = new CountDownLatch(1);
        CountDownLatch handshakeLatch = new CountDownLatch(1);
        CountDownLatch tcpPongLatch = new CountDownLatch(1);
        CountDownLatch udpPongLatch = new CountDownLatch(1);
        AtomicReference<HandshakeResponse> handshakeResponse = new AtomicReference<>();
        AtomicReference<TcpPongMessage> tcpPong = new AtomicReference<>();
        AtomicReference<UdpPongMessage> udpPong = new AtomicReference<>();

        client = new NetworkClient() {
            @Override
            protected void onConnected(Connection connection) {
                connectedLatch.countDown();
            }

            @Override
            protected void onReceived(Object object) {
                if (object instanceof HandshakeResponse response) {
                    handshakeResponse.set(response);
                    handshakeLatch.countDown();
                } else if (object instanceof TcpPongMessage pong) {
                    tcpPong.set(pong);
                    tcpPongLatch.countDown();
                } else if (object instanceof UdpPongMessage pong) {
                    udpPong.set(pong);
                    udpPongLatch.countDown();
                }
            }
        };

        client.connect(NetworkConstants.CONNECTION_TIMEOUT_MILLIS, "localhost", tcpPort, udpPort);
        assertTrue(connectedLatch.await(AWAIT_SECONDS, TimeUnit.SECONDS), "client did not connect in time");

        client.sendHandshake("test_pilot", "password123", "Test Pilot");
        assertTrue(handshakeLatch.await(AWAIT_SECONDS, TimeUnit.SECONDS), "no handshake response received");
        assertTrue(handshakeResponse.get().isAccepted());

        long tcpTimestamp = 123456789L;
        client.sendTcpPing(tcpTimestamp);
        assertTrue(tcpPongLatch.await(AWAIT_SECONDS, TimeUnit.SECONDS), "no TCP pong received");
        assertEquals(tcpTimestamp, tcpPong.get().getTimestamp());

        long udpTimestamp = 987654321L;
        client.sendUdpPing(udpTimestamp);
        assertTrue(udpPongLatch.await(AWAIT_SECONDS, TimeUnit.SECONDS), "no UDP pong received");
        assertEquals(udpTimestamp, udpPong.get().getTimestamp());
    }

    @Test
    void handshakeIsRejectedWhenClientVersionDoesNotMatchServerVersion() throws Exception {
        int[] ports = findTwoFreePorts();
        int tcpPort = ports[0];
        int udpPort = ports[1];

        server = new NetworkServer();
        server.start(tcpPort, udpPort);

        CountDownLatch connectedLatch = new CountDownLatch(1);
        CountDownLatch handshakeLatch = new CountDownLatch(1);
        AtomicReference<HandshakeResponse> handshakeResponse = new AtomicReference<>();

        client = new NetworkClient() {
            @Override
            protected void onConnected(Connection connection) {
                connectedLatch.countDown();
            }

            @Override
            protected void onReceived(Object object) {
                if (object instanceof HandshakeResponse response) {
                    handshakeResponse.set(response);
                    handshakeLatch.countDown();
                }
            }
        };

        client.connect(NetworkConstants.CONNECTION_TIMEOUT_MILLIS, "localhost", tcpPort, udpPort);
        assertTrue(connectedLatch.await(AWAIT_SECONDS, TimeUnit.SECONDS), "client did not connect in time");

        // Bypasses NetworkClient#sendHandshake (which always stamps the real AppVersion) to
        // simulate a client built from a different commit than this test's own server.
        String bogusVersion = AppVersion.getVersion() + "-old";
        client.sendTCP(new HandshakeRequest("test_pilot", "password123", "Test Pilot", bogusVersion));

        assertTrue(handshakeLatch.await(AWAIT_SECONDS, TimeUnit.SECONDS), "no handshake response received");
        assertFalse(handshakeResponse.get().isAccepted());
        assertTrue(handshakeResponse.get().getMessage().contains(bogusVersion),
            "rejection message should name the client's version: " + handshakeResponse.get().getMessage());
        assertTrue(handshakeResponse.get().getMessage().contains(AppVersion.getVersion()),
            "rejection message should name the server's version: " + handshakeResponse.get().getMessage());
    }

    /**
     * Picks two distinct currently-free ports to bind the test server's TCP
     * and UDP channels to, so the test doesn't collide with a real dedicated
     * server that might be running on this machine's default ports.
     *
     * <p>Both {@link ServerSocket}s are opened before either is closed:
     * opening and immediately closing them one at a time let the OS (observed
     * reliably on Windows) hand back the exact same just-freed port number
     * for the second call, which made the server fail to bind the UDP socket
     * on top of the still-open TCP one.
     */
    private static int[] findTwoFreePorts() throws IOException {
        try (ServerSocket first = new ServerSocket(0);
             ServerSocket second = new ServerSocket(0)) {
            return new int[] {first.getLocalPort(), second.getLocalPort()};
        }
    }
}
