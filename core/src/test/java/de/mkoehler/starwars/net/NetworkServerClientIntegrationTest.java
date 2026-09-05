package de.mkoehler.starwars.net;

import com.esotericsoftware.kryonet.Connection;
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
        int tcpPort = findFreePort();
        int udpPort = findFreePort();

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

        client.sendHandshake("Test Pilot");
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

    /**
     * Picks a currently-free TCP port to bind the test server to, so the test
     * doesn't collide with a real dedicated server that might be running on
     * this machine's default ports.
     */
    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
