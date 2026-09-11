package de.mkoehler.starwars.net;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import de.mkoehler.starwars.net.messages.AsteroidState;
import de.mkoehler.starwars.net.messages.HandshakeRequest;
import de.mkoehler.starwars.net.messages.HandshakeResponse;
import de.mkoehler.starwars.net.messages.LeaveMatchDeniedMessage;
import de.mkoehler.starwars.net.messages.LeaveMatchRequest;
import de.mkoehler.starwars.net.messages.MineDetonatedMessage;
import de.mkoehler.starwars.net.messages.MineState;
import de.mkoehler.starwars.net.messages.MissileFireRequest;
import de.mkoehler.starwars.net.messages.PlayerInputMessage;
import de.mkoehler.starwars.net.messages.PlayerLeftMessage;
import de.mkoehler.starwars.net.messages.PlayerScoreEntry;
import de.mkoehler.starwars.net.messages.PowerAdjustMessage;
import de.mkoehler.starwars.net.messages.PowerUpPickedUpMessage;
import de.mkoehler.starwars.net.messages.PowerUpState;
import de.mkoehler.starwars.net.messages.ProjectileHitMessage;
import de.mkoehler.starwars.net.messages.ProjectileState;
import de.mkoehler.starwars.net.messages.RadarPulseRequest;
import de.mkoehler.starwars.net.messages.ShipDestroyedMessage;
import de.mkoehler.starwars.net.messages.ShipImpactMessage;
import de.mkoehler.starwars.net.messages.ShipSpawnedMessage;
import de.mkoehler.starwars.net.messages.ScoreboardMessage;
import de.mkoehler.starwars.net.messages.ShipState;
import de.mkoehler.starwars.net.messages.SpawnRequest;
import de.mkoehler.starwars.net.messages.TcpPingMessage;
import de.mkoehler.starwars.net.messages.TcpPongMessage;
import de.mkoehler.starwars.net.messages.TurretToggleMessage;
import de.mkoehler.starwars.net.messages.UdpPingMessage;
import de.mkoehler.starwars.net.messages.UdpPongMessage;
import de.mkoehler.starwars.net.messages.UnlockShipRequest;
import de.mkoehler.starwars.net.messages.UnlockShipResponse;
import de.mkoehler.starwars.net.messages.WorldSnapshotMessage;
import de.mkoehler.starwars.sim.AsteroidType;
import de.mkoehler.starwars.sim.PowerSystem;
import de.mkoehler.starwars.sim.PowerUpType;
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
            TurretToggleMessage.class, float[].class, SpawnRequest.class,
            PlayerScoreEntry.class, PlayerScoreEntry[].class, ScoreboardMessage.class,
            ShipType[].class, UnlockShipRequest.class, UnlockShipResponse.class, RadarPulseRequest.class,
            MissileFireRequest.class, ProjectileHitMessage.class,
            AsteroidType.class, AsteroidState.class, AsteroidState[].class,
            PowerUpType.class, PowerUpState.class, PowerUpState[].class,
            MineState.class, MineState[].class, MineDetonatedMessage.class,
            ShipImpactMessage.class, PowerUpPickedUpMessage.class
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
        HandshakeRequest original = new HandshakeRequest("red_five", "s3cret", "Red Five", "1.2.3-test");
        HandshakeRequest copy = roundTrip(original, HandshakeRequest.class);
        assertEquals(original.getLogin(), copy.getLogin());
        assertEquals(original.getPassword(), copy.getPassword());
        assertEquals(original.getDisplayName(), copy.getDisplayName());
        assertEquals(original.getVersion(), copy.getVersion());
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
    void handshakeResponseWithAccountDataSurvivesRoundTrip() {
        HandshakeResponse original = new HandshakeResponse(true, "Welcome back.", 2500,
            new ShipType[] {ShipType.TIEFIGHTER, ShipType.XWING});
        HandshakeResponse copy = roundTrip(original, HandshakeResponse.class);
        assertTrue(copy.isAccepted());
        assertEquals(2500, copy.getXp());
        assertEquals(2, copy.getUnlockedShips().length);
        assertEquals(ShipType.TIEFIGHTER, copy.getUnlockedShips()[0]);
        assertEquals(ShipType.XWING, copy.getUnlockedShips()[1]);
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
        PlayerInputMessage original = new PlayerInputMessage(true, true, false, true);
        PlayerInputMessage copy = roundTrip(original, PlayerInputMessage.class);
        assertTrue(copy.isThrustForward());
        assertTrue(copy.isTurnLeft());
        assertEquals(false, copy.isTurnRight());
        assertTrue(copy.isFiring());
    }

    @Test
    void worldSnapshotMessageSurvivesRoundTrip() {
        WorldSnapshotMessage original = new WorldSnapshotMessage(
            new ShipState[]{
                new ShipState(1, 10f, 20f, 0.5f, 1f, 2f, 0.1f, 80f, 100f, 60f, 100f, ShipType.XWING, new float[0], 12f,
                    5, true, true, false, true, 2f),
                new ShipState(2, -5f, 3f, -1.2f, -1f, 0f, -0.3f, 100f, 100f, 100f, 100f, ShipType.STARDESTROYER,
                    new float[]{0.4f, -1.1f, 2.9f, -2.9f}, 0f, ShipState.NO_MISSILE_LOCK_TARGET, false, false, false, false, 1f)
            },
            new ProjectileState[]{
                new ProjectileState(100, 1, 11f, 20f, 3f, 48f, 5, true)
            },
            new AsteroidState[]{
                new AsteroidState(9, AsteroidType.ASTEROID3, -40f, 15f, 0.7f, 1.5f, -2f, 0.05f)
            },
            new PowerUpState[]{
                new PowerUpState(3, PowerUpType.BOOST, 12f, -8f, 0.9f, 2.5f, -1.5f, 0.2f)
            },
            new MineState[]{
                new MineState(4, 30f, -60f)
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
        assertEquals(12f, copy.getShips()[0].getRadarPulseCooldownRemaining());
        assertEquals(5, copy.getShips()[0].getMissileLockTargetPlayerId());
        assertTrue(copy.getShips()[0].isMissileLockAcquired());
        assertTrue(copy.getShips()[0].isTargetedByMissileLock());
        assertEquals(false, copy.getShips()[0].isTargetedByMissileLockAcquired());
        assertTrue(copy.getShips()[0].isThrusting());
        assertEquals(2f, copy.getShips()[0].getPowerGenerationMultiplier());
        assertEquals(false, copy.getShips()[1].isThrusting());
        assertEquals(1f, copy.getShips()[1].getPowerGenerationMultiplier());
        assertEquals(ShipState.NO_MISSILE_LOCK_TARGET, copy.getShips()[1].getMissileLockTargetPlayerId());
        assertEquals(false, copy.getShips()[1].isMissileLockAcquired());
        assertEquals(false, copy.getShips()[1].isTargetedByMissileLock());
        assertEquals(false, copy.getShips()[1].isTargetedByMissileLockAcquired());
        assertEquals(-1.2f, copy.getShips()[1].getAngle());
        assertEquals(-0.3f, copy.getShips()[1].getAngularVelocity());
        assertEquals(ShipType.STARDESTROYER, copy.getShips()[1].getShipType());
        assertEquals(4, copy.getShips()[1].getTurretAimAngles().length);
        assertEquals(-1.1f, copy.getShips()[1].getTurretAimAngles()[1]);
        assertEquals(1, copy.getProjectiles().length);
        assertEquals(100, copy.getProjectiles()[0].getProjectileId());
        assertEquals(1, copy.getProjectiles()[0].getOwnerPlayerId());
        assertEquals(11f, copy.getProjectiles()[0].getX());
        assertEquals(3f, copy.getProjectiles()[0].getVelocityX());
        assertEquals(48f, copy.getProjectiles()[0].getVelocityY());
        assertEquals(5, copy.getProjectiles()[0].getTrackedTargetPlayerId());
        assertTrue(copy.getProjectiles()[0].isTurretShot());
        assertEquals(1, copy.getAsteroids().length);
        assertEquals(9, copy.getAsteroids()[0].getAsteroidId());
        assertEquals(AsteroidType.ASTEROID3, copy.getAsteroids()[0].getType());
        assertEquals(-40f, copy.getAsteroids()[0].getX());
        assertEquals(15f, copy.getAsteroids()[0].getY());
        assertEquals(0.7f, copy.getAsteroids()[0].getAngle());
        assertEquals(1.5f, copy.getAsteroids()[0].getVelocityX());
        assertEquals(-2f, copy.getAsteroids()[0].getVelocityY());
        assertEquals(0.05f, copy.getAsteroids()[0].getAngularVelocity());
        assertEquals(1, copy.getPowerUps().length);
        assertEquals(3, copy.getPowerUps()[0].getPowerUpId());
        assertEquals(PowerUpType.BOOST, copy.getPowerUps()[0].getType());
        assertEquals(12f, copy.getPowerUps()[0].getX());
        assertEquals(-8f, copy.getPowerUps()[0].getY());
        assertEquals(0.9f, copy.getPowerUps()[0].getAngle());
        assertEquals(2.5f, copy.getPowerUps()[0].getVelocityX());
        assertEquals(-1.5f, copy.getPowerUps()[0].getVelocityY());
        assertEquals(0.2f, copy.getPowerUps()[0].getAngularVelocity());
        assertEquals(1, copy.getMines().length);
        assertEquals(4, copy.getMines()[0].getMineId());
        assertEquals(30f, copy.getMines()[0].getX());
        assertEquals(-60f, copy.getMines()[0].getY());
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

    @Test
    void scoreboardMessageSurvivesRoundTrip() {
        ScoreboardMessage original = new ScoreboardMessage(new PlayerScoreEntry[]{
            new PlayerScoreEntry(1, "Red Five", 250, 3, 1),
            new PlayerScoreEntry(2, "Blue Two", 0, 0, 0)
        });
        ScoreboardMessage copy = roundTrip(original, ScoreboardMessage.class);
        assertEquals(2, copy.getEntries().length);
        assertEquals(1, copy.getEntries()[0].getPlayerId());
        assertEquals("Red Five", copy.getEntries()[0].getDisplayName());
        assertEquals(250, copy.getEntries()[0].getXp());
        assertEquals(3, copy.getEntries()[0].getKills());
        assertEquals(1, copy.getEntries()[0].getDeaths());
        assertEquals("Blue Two", copy.getEntries()[1].getDisplayName());
    }

    @Test
    void unlockShipRequestSurvivesRoundTrip() {
        UnlockShipRequest original = new UnlockShipRequest(ShipType.AWING);
        UnlockShipRequest copy = roundTrip(original, UnlockShipRequest.class);
        assertEquals(ShipType.AWING, copy.getShipType());
    }

    @Test
    void unlockShipResponseSurvivesRoundTrip() {
        UnlockShipResponse original = new UnlockShipResponse(true, "Unlocked.", 2500,
            new ShipType[] {ShipType.TIEFIGHTER, ShipType.AWING});
        UnlockShipResponse copy = roundTrip(original, UnlockShipResponse.class);
        assertTrue(copy.isSuccess());
        assertEquals("Unlocked.", copy.getMessage());
        assertEquals(2500, copy.getXp());
        assertEquals(2, copy.getUnlockedShips().length);
        assertEquals(ShipType.AWING, copy.getUnlockedShips()[1]);
    }

    @Test
    void radarPulseRequestSurvivesRoundTrip() {
        RadarPulseRequest copy = roundTrip(new RadarPulseRequest(), RadarPulseRequest.class);
        assertEquals(RadarPulseRequest.class, copy.getClass());
    }

    @Test
    void missileFireRequestSurvivesRoundTrip() {
        MissileFireRequest copy = roundTrip(new MissileFireRequest(), MissileFireRequest.class);
        assertEquals(MissileFireRequest.class, copy.getClass());
    }

    @Test
    void projectileHitMessageSurvivesRoundTrip() {
        ProjectileHitMessage original = new ProjectileHitMessage(12.5f, -7.25f);
        ProjectileHitMessage copy = roundTrip(original, ProjectileHitMessage.class);
        assertEquals(12.5f, copy.getX());
        assertEquals(-7.25f, copy.getY());
    }

    @Test
    void mineDetonatedMessageSurvivesRoundTrip() {
        MineDetonatedMessage original = new MineDetonatedMessage(18.5f, -33.25f);
        MineDetonatedMessage copy = roundTrip(original, MineDetonatedMessage.class);
        assertEquals(18.5f, copy.getX());
        assertEquals(-33.25f, copy.getY());
    }

    @Test
    void shipImpactMessageSurvivesRoundTrip() {
        ShipImpactMessage original = new ShipImpactMessage(9.5f, -14.25f);
        ShipImpactMessage copy = roundTrip(original, ShipImpactMessage.class);
        assertEquals(9.5f, copy.getX());
        assertEquals(-14.25f, copy.getY());
    }

    @Test
    void powerUpPickedUpMessageSurvivesRoundTrip() {
        PowerUpPickedUpMessage original = new PowerUpPickedUpMessage(22f, -6.5f);
        PowerUpPickedUpMessage copy = roundTrip(original, PowerUpPickedUpMessage.class);
        assertEquals(22f, copy.getX());
        assertEquals(-6.5f, copy.getY());
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
