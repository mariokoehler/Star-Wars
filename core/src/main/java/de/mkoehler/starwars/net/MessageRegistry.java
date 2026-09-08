package de.mkoehler.starwars.net;

import com.esotericsoftware.kryo.Kryo;
import de.mkoehler.starwars.net.messages.HandshakeRequest;
import de.mkoehler.starwars.net.messages.HandshakeResponse;
import de.mkoehler.starwars.net.messages.LeaveMatchDeniedMessage;
import de.mkoehler.starwars.net.messages.LeaveMatchRequest;
import de.mkoehler.starwars.net.messages.MissileFireRequest;
import de.mkoehler.starwars.net.messages.PlayerInputMessage;
import de.mkoehler.starwars.net.messages.PlayerLeftMessage;
import de.mkoehler.starwars.net.messages.PlayerScoreEntry;
import de.mkoehler.starwars.net.messages.PowerAdjustMessage;
import de.mkoehler.starwars.net.messages.ProjectileState;
import de.mkoehler.starwars.net.messages.RadarPulseRequest;
import de.mkoehler.starwars.net.messages.ShipDestroyedMessage;
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
import de.mkoehler.starwars.sim.PowerSystem;
import de.mkoehler.starwars.sim.ShipType;

/**
 * Registers every class sent over the wire with a {@link Kryo} instance, in a
 * fixed order.
 * <p>
 * Kryo assigns each registered class a numeric id based on registration order
 * and relies on that id, rather than the class name, to identify types on the
 * wire. Both ends of a connection must therefore register the exact same
 * classes in the exact same order, or messages will be misread on the
 * receiving end. This class is the single shared place that order is defined,
 * used by both {@link NetworkServer} and {@link NetworkClient}.
 */
public final class MessageRegistry {

    private MessageRegistry() {
    }

    /**
     * Registers all wire message classes with the given {@link Kryo} instance.
     *
     * @param kryo the Kryo instance to register classes with, typically obtained
     *             from an {@code EndPoint}'s {@code getKryo()} method
     */
    public static void register(Kryo kryo) {
        kryo.register(HandshakeRequest.class);
        kryo.register(HandshakeResponse.class);
        kryo.register(TcpPingMessage.class);
        kryo.register(TcpPongMessage.class);
        kryo.register(UdpPingMessage.class);
        kryo.register(UdpPongMessage.class);
        // Appended below, in the order added - never reorder or insert above existing
        // entries, that would change everyone's registration ids (see class Javadoc).
        kryo.register(ShipSpawnedMessage.class);
        kryo.register(PlayerInputMessage.class);
        kryo.register(ShipState.class);
        kryo.register(ShipState[].class);
        kryo.register(WorldSnapshotMessage.class);
        kryo.register(PlayerLeftMessage.class);
        kryo.register(ProjectileState.class);
        kryo.register(ProjectileState[].class);
        kryo.register(ShipDestroyedMessage.class);
        kryo.register(ShipType.class);
        kryo.register(PowerSystem.class);
        kryo.register(PowerAdjustMessage.class);
        kryo.register(PowerAdjustMessage.Kind.class);
        kryo.register(LeaveMatchRequest.class);
        kryo.register(LeaveMatchDeniedMessage.class);
        kryo.register(TurretToggleMessage.class);
        kryo.register(float[].class);
        kryo.register(SpawnRequest.class);
        kryo.register(PlayerScoreEntry.class);
        kryo.register(PlayerScoreEntry[].class);
        kryo.register(ScoreboardMessage.class);
        kryo.register(ShipType[].class);
        kryo.register(UnlockShipRequest.class);
        kryo.register(UnlockShipResponse.class);
        kryo.register(RadarPulseRequest.class);
        kryo.register(MissileFireRequest.class);
    }
}
