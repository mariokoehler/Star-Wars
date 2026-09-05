package de.mkoehler.starwars.net;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import de.mkoehler.starwars.net.messages.HandshakeRequest;
import de.mkoehler.starwars.net.messages.HandshakeResponse;
import de.mkoehler.starwars.net.messages.PlayerInputMessage;
import de.mkoehler.starwars.net.messages.PlayerJoinedMessage;
import de.mkoehler.starwars.net.messages.PlayerLeftMessage;
import de.mkoehler.starwars.net.messages.ShipState;
import de.mkoehler.starwars.net.messages.TcpPingMessage;
import de.mkoehler.starwars.net.messages.TcpPongMessage;
import de.mkoehler.starwars.net.messages.UdpPingMessage;
import de.mkoehler.starwars.net.messages.UdpPongMessage;
import de.mkoehler.starwars.net.messages.WorldSnapshotMessage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the guarantees {@link MessageRegistry} relies on: registration
 * order is deterministic across independent {@link Kryo} instances, and every
 * registered message class survives a serialize/deserialize round trip.
 */
class MessageRegistryTest {

    @Test
    void registrationIdsAreIdenticalAcrossIndependentKryoInstances() {
        Kryo first = new Kryo();
        Kryo second = new Kryo();

        MessageRegistry.register(first);
        MessageRegistry.register(second);

        Class<?>[] messageClasses = {
            HandshakeRequest.class, HandshakeResponse.class,
            TcpPingMessage.class, TcpPongMessage.class,
            UdpPingMessage.class, UdpPongMessage.class,
            PlayerJoinedMessage.class, PlayerInputMessage.class,
            ShipState.class, ShipState[].class,
            WorldSnapshotMessage.class, PlayerLeftMessage.class
        };

        for (Class<?> messageClass : messageClasses) {
            int firstId = first.getRegistration(messageClass).getId();
            int secondId = second.getRegistration(messageClass).getId();
            assertEquals(firstId, secondId,
                messageClass.getSimpleName() + " must register to the same id on both ends of a connection");
        }
    }

    @Test
    void handshakeRequestSurvivesRoundTrip() {
        HandshakeRequest original = new HandshakeRequest("Red Five");
        HandshakeRequest copy = roundTrip(original, HandshakeRequest.class);
        assertEquals(original.getDisplayName(), copy.getDisplayName());
    }

    @Test
    void handshakeResponseSurvivesRoundTrip() {
        HandshakeResponse original = new HandshakeResponse(true, "Welcome.");
        HandshakeResponse copy = roundTrip(original, HandshakeResponse.class);
        assertTrue(copy.isAccepted());
        assertEquals(original.getMessage(), copy.getMessage());
    }

    @Test
    void tcpPingPongSurviveRoundTrip() {
        TcpPingMessage ping = roundTrip(new TcpPingMessage(42L), TcpPingMessage.class);
        assertEquals(42L, ping.getTimestamp());

        TcpPongMessage pong = roundTrip(new TcpPongMessage(42L), TcpPongMessage.class);
        assertEquals(42L, pong.getTimestamp());
    }

    @Test
    void udpPingPongSurviveRoundTrip() {
        UdpPingMessage ping = roundTrip(new UdpPingMessage(7L), UdpPingMessage.class);
        assertEquals(7L, ping.getTimestamp());

        UdpPongMessage pong = roundTrip(new UdpPongMessage(7L), UdpPongMessage.class);
        assertEquals(7L, pong.getTimestamp());
    }

    @Test
    void playerJoinedMessageSurvivesRoundTrip() {
        PlayerJoinedMessage original = new PlayerJoinedMessage(7, 1.5f, -2.5f);
        PlayerJoinedMessage copy = roundTrip(original, PlayerJoinedMessage.class);
        assertEquals(7, copy.getPlayerId());
        assertEquals(1.5f, copy.getSpawnX());
        assertEquals(-2.5f, copy.getSpawnY());
    }

    @Test
    void playerInputMessageSurvivesRoundTrip() {
        PlayerInputMessage original = new PlayerInputMessage(true, false, true, false);
        PlayerInputMessage copy = roundTrip(original, PlayerInputMessage.class);
        assertTrue(copy.isThrustForward());
        assertEquals(false, copy.isThrustReverse());
        assertTrue(copy.isTurnLeft());
        assertEquals(false, copy.isTurnRight());
    }

    @Test
    void worldSnapshotMessageSurvivesRoundTrip() {
        WorldSnapshotMessage original = new WorldSnapshotMessage(new ShipState[]{
            new ShipState(1, 10f, 20f, 0.5f),
            new ShipState(2, -5f, 3f, -1.2f)
        });
        WorldSnapshotMessage copy = roundTrip(original, WorldSnapshotMessage.class);
        assertEquals(2, copy.getShips().length);
        assertEquals(1, copy.getShips()[0].getPlayerId());
        assertEquals(10f, copy.getShips()[0].getX());
        assertEquals(-1.2f, copy.getShips()[1].getAngle());
    }

    @Test
    void playerLeftMessageSurvivesRoundTrip() {
        PlayerLeftMessage original = new PlayerLeftMessage(3);
        PlayerLeftMessage copy = roundTrip(original, PlayerLeftMessage.class);
        assertEquals(3, copy.getPlayerId());
    }

    private static <T> T roundTrip(T original, Class<T> type) {
        Kryo kryo = new Kryo();
        MessageRegistry.register(kryo);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (Output output = new Output(bytes)) {
            kryo.writeObject(output, original);
        }

        try (Input input = new Input(bytes.toByteArray())) {
            return kryo.readObject(input, type);
        }
    }
}
