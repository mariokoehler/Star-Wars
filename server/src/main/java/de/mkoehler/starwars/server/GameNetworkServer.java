package de.mkoehler.starwars.server;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.World;
import com.esotericsoftware.kryonet.Connection;
import de.mkoehler.starwars.net.NetworkServer;
import de.mkoehler.starwars.net.messages.HandshakeRequest;
import de.mkoehler.starwars.net.messages.HandshakeResponse;
import de.mkoehler.starwars.net.messages.PlayerInputMessage;
import de.mkoehler.starwars.net.messages.PlayerJoinedMessage;
import de.mkoehler.starwars.net.messages.PlayerLeftMessage;
import de.mkoehler.starwars.net.messages.ShipState;
import de.mkoehler.starwars.net.messages.WorldSnapshotMessage;
import de.mkoehler.starwars.sim.ShipFactory;
import de.mkoehler.starwars.sim.ShipStats;
import de.mkoehler.starwars.sim.components.NetworkInputComponent;
import de.mkoehler.starwars.sim.components.PhysicsBodyComponent;
import de.mkoehler.starwars.sim.systems.PhysicsSystem;
import de.mkoehler.starwars.sim.systems.ShipControlSystem;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The authoritative game simulation: owns the Box2D {@link World} and Ashley
 * {@link Engine} for every connected player's ship, and the network protocol
 * on top of the generic connect/handshake/ping-pong {@link NetworkServer}.
 * <p>
 * KryoNet invokes {@link #handleHandshake}, {@link #onReceived} and
 * {@link #onDisconnected} on its own network thread, never the thread
 * {@link #tick(float)} is called from (the headless application's render
 * thread) — so none of them touch {@link #shipsByPlayerId}, the Ashley
 * {@link Engine} or the Box2D {@link World} directly. Instead they enqueue a
 * {@link Runnable} onto {@link #pendingActions}, which {@link #tick(float)}
 * drains at the start of every call, before stepping physics. This is the
 * only place cross-thread coordination happens; everything else in this
 * class runs exclusively on the tick thread.
 */
public class GameNetworkServer extends NetworkServer {

    private final World world = new World(new Vector2(0, 0), true);
    private final Engine engine = new Engine();
    private final ShipControlSystem shipControlSystem = new ShipControlSystem();
    private final PhysicsSystem physicsSystem = new PhysicsSystem(world);

    private final Map<Integer, Entity> shipsByPlayerId = new HashMap<>();
    private final Queue<Runnable> pendingActions = new ConcurrentLinkedQueue<>();

    /**
     * Creates the game server, wiring up the simulation systems.
     */
    public GameNetworkServer() {
        engine.addSystem(shipControlSystem);
        engine.addSystem(physicsSystem);
    }

    /**
     * Advances the simulation by one tick: applies every queued network
     * action (spawns, input updates, despawns), steps the simulation, and
     * broadcasts the resulting world state to every connected client.
     *
     * @param deltaTime time since the last tick, in seconds
     */
    public void tick(float deltaTime) {
        Runnable action;
        while ((action = pendingActions.poll()) != null) {
            action.run();
        }

        shipControlSystem.update(deltaTime);
        physicsSystem.update(deltaTime);

        broadcastSnapshot();
    }

    @Override
    protected HandshakeResponse handleHandshake(Connection connection, HandshakeRequest request) {
        int playerId = connection.getID();
        // Fixed spawn point for now - map/arena design (design.md 7) is still an open question.
        float spawnX = 0f;
        float spawnY = 0f;
        pendingActions.add(() -> spawnShip(playerId, spawnX, spawnY));
        connection.sendTCP(new PlayerJoinedMessage(playerId, spawnX, spawnY));
        return new HandshakeResponse(true, "Welcome, " + request.getDisplayName() + ".");
    }

    @Override
    protected void onReceived(Connection connection, Object object) {
        super.onReceived(connection, object);
        if (object instanceof PlayerInputMessage input) {
            int playerId = connection.getID();
            pendingActions.add(() -> applyInput(playerId, input));
        }
    }

    @Override
    protected void onDisconnected(Connection connection) {
        int playerId = connection.getID();
        pendingActions.add(() -> despawnShip(playerId));
        sendToAllTCP(new PlayerLeftMessage(playerId));
    }

    private void spawnShip(int playerId, float x, float y) {
        if (shipsByPlayerId.containsKey(playerId)) {
            return;
        }
        Entity ship = ShipFactory.createShip(engine, world, playerId, x, y, ShipStats.XWING);
        shipsByPlayerId.put(playerId, ship);
    }

    private void applyInput(int playerId, PlayerInputMessage input) {
        Entity ship = shipsByPlayerId.get(playerId);
        if (ship == null) {
            // Input that arrived before the queued spawn ran, or after despawn; harmless to drop.
            return;
        }
        ship.getComponent(NetworkInputComponent.class).set(
            input.isThrustForward(), input.isThrustReverse(), input.isTurnLeft(), input.isTurnRight());
    }

    private void despawnShip(int playerId) {
        Entity ship = shipsByPlayerId.remove(playerId);
        if (ship == null) {
            return;
        }
        world.destroyBody(ship.getComponent(PhysicsBodyComponent.class).getBody());
        engine.removeEntity(ship);
    }

    private void broadcastSnapshot() {
        ShipState[] states = new ShipState[shipsByPlayerId.size()];
        int i = 0;
        for (Map.Entry<Integer, Entity> entry : shipsByPlayerId.entrySet()) {
            Body body = entry.getValue().getComponent(PhysicsBodyComponent.class).getBody();
            states[i++] = new ShipState(entry.getKey(), body.getPosition().x, body.getPosition().y, body.getAngle());
        }
        sendToAllUDP(new WorldSnapshotMessage(states));
    }
}
