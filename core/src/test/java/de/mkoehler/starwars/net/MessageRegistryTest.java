package de.mkoehler.starwars.net;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import de.mkoehler.starwars.net.messages.HandshakeRequest;
import de.mkoehler.starwars.net.messages.HandshakeResponse;
import de.mkoehler.starwars.net.messages.LeaveMatchDeniedMessage;
import de.mkoehler.starwars.net.messages.LeaveMatchRequest;
import de.mkoehler.starwars.net.messages.PlayerInputMessage;
import de.mkoehler.starwars.net.messages.PlayerLeftMessage;
import de.mkoehler.starwars.net.messages.PowerAdjustMessage;
import de.mkoehler.starwars.net.messages.ProjectileState;
import de.mkoehler.starwars.net.messages.ShipDestroyedMessage;
import de.mkoehler.starwars.net.messages.ShipSpawnedMessage;
import de.mkoehler.starwars.net.messages.ShipState;
import de.mkoehler.starwars.net.messages.SpawnRequest;
import de.mkoehler.starwars.net.messages.TcpPingMessage;
import de.mkoehler.starwars.net.messages.TcpPongMessage;
import de.mkoehler.starwars.net.messages.TurretToggleMessage;
import de.mkoehler.starwars.net.messages.UdpPingMessage;
import de.mkoehler.starwars.net.messages.UdpPongMessage;
import de.mkoehler.starwars.net.messages.WorldSnapshotMessage;
import de.mkoehler.starwars.sim.PowerSystem;
import de.mkoehler.starwars.sim.ShipType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
            ShipSpawnedMessage.class, PlayerInputMessage.class,
            ShipState.class, ShipState[].class,
            WorldSnapshotMessage.class, PlayerLeftMessage.class,
            ProjectileState.class, ProjectileState[].class,
            ShipDestroyedMessage.class, ShipType.class,
            PowerSystem.class, PowerAdjustMessage.class, PowerAdjustMessage.Kind.class,
            LeaveMatchRequest.class, LeaveMatchDeniedMessage.class,
            TurretToggleMessage.class, float[].class, SpawnRequest.class
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
        HandshakeRequest original = new HandshakeRequest("red_five", "s3cret", "Red Five");
        HandshakeRequest copy = roundTrip(original, HandshakeRequest.class);
        assertEquals(original.getLogin(), copy.getLogin());
        assertEquals(original.getPassword(), copy.getPassword());
        assertEquals(original.getDisplayName(), copy.getDisplayName());
    }

    @Test
    void spawnRequestSurvivesRoundTrip() {
        SpawnRequest original = new SpawnRequest(ShipType.FALCON);
        SpawnRequest copy = roundTrip(original, SpawnRequest.class);
        assertEquals(ShipType.FALCON, copy.getShipType());
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
    void shipSpawnedMessageSurvivesRoundTrip() {
        ShipSpawnedMessage original = new ShipSpawnedMessage(7, 1.5f, -2.5f, ShipType.FALCON);
        ShipSpawnedMessage copy = roundTrip(original, ShipSpawnedMessage.class);
        assertEquals(7, copy.getPlayerId());
        assertEquals(1.5f, copy.getSpawnX());
        assertEquals(-2.5f, copy.getSpawnY());
        assertEquals(ShipType.FALCON, copy.getShipType());
    }

    @Test
    void playerInputMessageSurvivesRoundTrip() {
        PlayerInputMessage original = new PlayerInputMessage(true, false, true, false, true);
        PlayerInputMessage copy = roundTrip(original, PlayerInputMessage.class);
        assertTrue(copy.isThrustForward());
        assertEquals(false, copy.isThrustReverse());
        assertTrue(copy.isTurnLeft());
        assertEquals(false, copy.isTurnRight());
        assertTrue(copy.isFiring());
    }

    @Test
    void worldSnapshotMessageSurvivesRoundTrip() {
        WorldSnapshotMessage original = new WorldSnapshotMessage(
            new ShipState[]{
                new ShipState(1, 10f, 20f, 0.5f, 1f, 2f, 0.1f, 80f, 100f, 60f, 100f, ShipType.XWING, new float[0]),
                new ShipState(2, -5f, 3f, -1.2f, -1f, 0f, -0.3f, 100f, 100f, 100f, 100f, ShipType.STARDESTROYER,
                    new float[]{0.4f, -1.1f, 2.9f, -2.9f})
            },
            new ProjectileState[]{
                new ProjectileState(100, 1, 11f, 20f, 0.5f)
            });
        WorldSnapshotMessage copy = roundTrip(original, WorldSnapshotMessage.class);
        assertEquals(2, copy.getShips().length);
        assertEquals(1, copy.getShips()[0].getPlayerId());
        assertEquals(10f, copy.getShips()[0].getX());
        assertEquals(2f, copy.getShips()[0].getVelocityY());
        assertEquals(80f, copy.getShips()[0].getHullCurrent());
        assertEquals(100f, copy.getShips()[0].getHullMax());
        assertEquals(60f, copy.getShips()[0].getShieldCurrent());
        assertEquals(100f, copy.getShips()[0].getShieldMax());
        assertEquals(ShipType.XWING, copy.getShips()[0].getShipType());
        assertEquals(0, copy.getShips()[0].getTurretAimAngles().length);
        assertEquals(-1.2f, copy.getShips()[1].getAngle());
        assertEquals(-0.3f, copy.getShips()[1].getAngularVelocity());
        assertEquals(ShipType.STARDESTROYER, copy.getShips()[1].getShipType());
        assertEquals(4, copy.getShips()[1].getTurretAimAngles().length);
        assertEquals(-1.1f, copy.getShips()[1].getTurretAimAngles()[1]);
        assertEquals(1, copy.getProjectiles().length);
        assertEquals(100, copy.getProjectiles()[0].getProjectileId());
        assertEquals(1, copy.getProjectiles()[0].getOwnerPlayerId());
        assertEquals(11f, copy.getProjectiles()[0].getX());
    }

    @Test
    void playerLeftMessageSurvivesRoundTrip() {
        PlayerLeftMessage original = new PlayerLeftMessage(3);
        PlayerLeftMessage copy = roundTrip(original, PlayerLeftMessage.class);
        assertEquals(3, copy.getPlayerId());
    }

    @Test
    void shipDestroyedMessageSurvivesRoundTrip() {
        ShipDestroyedMessage original = new ShipDestroyedMessage(9);
        ShipDestroyedMessage copy = roundTrip(original, ShipDestroyedMessage.class);
        assertEquals(9, copy.getPlayerId());
    }

    @Test
    void powerAdjustMessageSurvivesRoundTrip() {
        PowerAdjustMessage original = new PowerAdjustMessage(PowerAdjustMessage.Kind.ADJUST, PowerSystem.WEAPONS);
        PowerAdjustMessage copy = roundTrip(original, PowerAdjustMessage.class);
        assertEquals(PowerAdjustMessage.Kind.ADJUST, copy.getKind());
        assertEquals(PowerSystem.WEAPONS, copy.getTarget());
    }

    @Test
    void powerAdjustMessageMaximizeSurvivesRoundTrip() {
        PowerAdjustMessage original = new PowerAdjustMessage(PowerAdjustMessage.Kind.MAXIMIZE, PowerSystem.ENGINES);
        PowerAdjustMessage copy = roundTrip(original, PowerAdjustMessage.class);
        assertEquals(PowerAdjustMessage.Kind.MAXIMIZE, copy.getKind());
        assertEquals(PowerSystem.ENGINES, copy.getTarget());
    }

    @Test
    void powerAdjustMessageResetSurvivesRoundTripAsANullTarget() {
        PowerAdjustMessage original = new PowerAdjustMessage(PowerAdjustMessage.Kind.RESET, null);
        PowerAdjustMessage copy = roundTrip(original, PowerAdjustMessage.class);
        assertEquals(PowerAdjustMessage.Kind.RESET, copy.getKind());
        assertNull(copy.getTarget());
    }

    @Test
    void leaveMatchRequestSurvivesRoundTrip() {
        LeaveMatchRequest copy = roundTrip(new LeaveMatchRequest(), LeaveMatchRequest.class);
        assertEquals(LeaveMatchRequest.class, copy.getClass());
    }

    @Test
    void leaveMatchDeniedMessageSurvivesRoundTrip() {
        LeaveMatchDeniedMessage copy = roundTrip(new LeaveMatchDeniedMessage(), LeaveMatchDeniedMessage.class);
        assertEquals(LeaveMatchDeniedMessage.class, copy.getClass());
    }

    @Test
    void turretToggleMessageSurvivesRoundTrip() {
        TurretToggleMessage copy = roundTrip(new TurretToggleMessage(), TurretToggleMessage.class);
        assertEquals(TurretToggleMessage.class, copy.getClass());
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
