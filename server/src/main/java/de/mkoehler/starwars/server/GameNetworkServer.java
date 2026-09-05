package de.mkoehler.starwars.server;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactFilter;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.badlogic.gdx.physics.box2d.World;
import com.esotericsoftware.kryonet.Connection;
import de.mkoehler.starwars.net.NetworkServer;
import de.mkoehler.starwars.net.messages.HandshakeRequest;
import de.mkoehler.starwars.net.messages.HandshakeResponse;
import de.mkoehler.starwars.net.messages.PlayerInputMessage;
import de.mkoehler.starwars.net.messages.PlayerLeftMessage;
import de.mkoehler.starwars.net.messages.ProjectileState;
import de.mkoehler.starwars.net.messages.ShipDestroyedMessage;
import de.mkoehler.starwars.net.messages.ShipSpawnedMessage;
import de.mkoehler.starwars.net.messages.ShipState;
import de.mkoehler.starwars.net.messages.WorldSnapshotMessage;
import de.mkoehler.starwars.sim.ShipDamage;
import de.mkoehler.starwars.sim.ShipFactory;
import de.mkoehler.starwars.sim.ShipStats;
import de.mkoehler.starwars.sim.components.HullComponent;
import de.mkoehler.starwars.sim.components.NetworkInputComponent;
import de.mkoehler.starwars.sim.components.PhysicsBodyComponent;
import de.mkoehler.starwars.sim.components.PlayerIdComponent;
import de.mkoehler.starwars.sim.components.ProjectileComponent;
import de.mkoehler.starwars.sim.components.ShieldComponent;
import de.mkoehler.starwars.sim.systems.PhysicsSystem;
import de.mkoehler.starwars.sim.systems.ProjectileLifetimeSystem;
import de.mkoehler.starwars.sim.systems.ShieldRegenSystem;
import de.mkoehler.starwars.sim.systems.ShipControlSystem;
import de.mkoehler.starwars.sim.systems.WeaponSystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The authoritative game simulation: owns the Box2D {@link World} and Ashley
 * {@link Engine} for every connected player's ship and every live
 * projectile, and the network protocol on top of the generic
 * connect/handshake/ping-pong {@link NetworkServer}.
 * <p>
 * KryoNet invokes {@link #handleHandshake}, {@link #onReceived} and
 * {@link #onDisconnected} on its own network thread, never the thread
 * {@link #tick(float)} is called from (the headless application's render
 * thread) — so none of them touch {@link #shipsByPlayerId},
 * {@link #connectionsByPlayerId}, the Ashley {@link Engine} or the Box2D
 * {@link World} directly. Instead they enqueue a {@link Runnable} onto
 * {@link #pendingActions}, which {@link #tick(float)} drains at the start of
 * every call, before stepping physics. This is the only place cross-thread
 * coordination happens; everything else in this class runs exclusively on
 * the tick thread. (Sending a message, e.g. {@code connection.sendTCP(...)},
 * is safe from any thread — only mutating simulation state is not.)
 */
public class GameNetworkServer extends NetworkServer {

    /** Fixed delay between a ship being destroyed and it respawning; not a tuned value. */
    private static final float RESPAWN_DELAY_SECONDS = 3f;

    private final World world = new World(new Vector2(0, 0), true);
    private final Engine engine = new Engine();
    private final ShipControlSystem shipControlSystem = new ShipControlSystem();
    private final PhysicsSystem physicsSystem = new PhysicsSystem(world);
    private final WeaponSystem weaponSystem = new WeaponSystem(engine, world);
    private final ProjectileLifetimeSystem projectileLifetimeSystem = new ProjectileLifetimeSystem(engine, world);
    private final ShieldRegenSystem shieldRegenSystem = new ShieldRegenSystem();

    private final Map<Integer, Entity> shipsByPlayerId = new HashMap<>();
    private final Map<Integer, Connection> connectionsByPlayerId = new HashMap<>();
    private final Map<Integer, Float> respawnTimers = new HashMap<>();
    private final List<HitEvent> pendingHits = new ArrayList<>();
    private final Queue<Runnable> pendingActions = new ConcurrentLinkedQueue<>();

    /**
     * Creates the game server, wiring up the simulation systems and the
     * projectile-vs-ship hit detection.
     */
    public GameNetworkServer() {
        engine.addSystem(shipControlSystem);
        engine.addSystem(physicsSystem);
        engine.addSystem(weaponSystem);
        engine.addSystem(projectileLifetimeSystem);
        engine.addSystem(shieldRegenSystem);

        // Without this, a freshly-fired projectile would generate a real Box2D collision
        // against its own shooter's ship the instant it spawns (previously spawned exactly at
        // the ship's center, now just ahead of the hull - see WeaponSystem - but this filter is
        // the actual fix regardless of spawn offset: it stops the collision from being generated
        // at all, rather than relying on positioning to avoid it). Found via play-testing: shots
        // fired while turning visibly flew in the wrong direction past 180 degrees of rotation -
        // Box2D was resolving the spawn-time overlap by pushing the projectile along some fixed
        // fallback axis (two exactly-coincident circles have no defined separation direction),
        // unrelated to the ship's actual facing.
        world.setContactFilter((fixtureA, fixtureB) -> {
            Entity a = asEntity(fixtureA.getBody());
            Entity b = asEntity(fixtureB.getBody());
            return !isOwnShip(a, b) && !isOwnShip(b, a);
        });

        // Fires synchronously during world.step() (called from tick(), never from the network
        // thread), so no cross-thread concern here - just the separate Box2D rule that bodies
        // can't be created/destroyed from inside a contact callback. Hits are collected into
        // pendingHits and only acted on after physicsSystem.update() returns, in tick().
        world.setContactListener(new ContactListener() {
            @Override
            public void beginContact(Contact contact) {
                Entity a = asEntity(contact.getFixtureA().getBody());
                Entity b = asEntity(contact.getFixtureB().getBody());
                registerPotentialHit(a, b);
                registerPotentialHit(b, a);
            }

            @Override
            public void endContact(Contact contact) {
            }

            @Override
            public void preSolve(Contact contact, Manifold oldManifold) {
            }

            @Override
            public void postSolve(Contact contact, ContactImpulse impulse) {
            }
        });
    }

    /**
     * Advances the simulation by one tick: applies every queued network
     * action (spawns, input updates, despawns), steps physics (which is
     * also where projectile-vs-ship contacts are detected), fires weapons,
     * resolves any hits (splitting damage between shield and hull, see
     * {@link ShipDamage}), regenerates shields, expires old projectiles,
     * advances respawn timers, and broadcasts the resulting world state to
     * every connected client.
     *
     * @param deltaTime time since the last tick, in seconds
     */
    public void tick(float deltaTime) {
        Runnable action;
        while ((action = pendingActions.poll()) != null) {
            action.run();
        }

        // Reapply every ship's current input before each individual physics step, not once
        // per tick - the server ticks at 30Hz but physics steps at a fixed 60Hz, so most
        // ticks need two steps, and Box2D clears applied forces after every step. Applying
        // once per tick silently starved every second step of its force (see
        // PhysicsSystem#update(float, Runnable) for the full explanation) - found via
        // play-testing that felt like "flying in slow motion" compared to the unnetworked
        // prototype, plus knock-on jitter from reconciliation fighting that speed gap.
        physicsSystem.update(deltaTime, () -> shipControlSystem.update(0f));

        // Fires weapons *after* this tick's physics stepping, not before: a projectile created
        // here won't be moved by this tick's world.step() calls at all, so the position first
        // broadcast for it is its exact spawn point. Firing before physics stepping (the
        // original order) let a freshly-spawned projectile get swept forward by however many
        // physics steps this tick ran (~2, at 30Hz tick / 60Hz step) before ever being
        // broadcast - found via play-testing: shots visually originated well ahead of their
        // attachment point, in a straight line along the correct facing, which pointed at "it
        // moved before its first render" rather than a spawn-position bug.
        weaponSystem.update(deltaTime);

        resolvePendingHits();
        shieldRegenSystem.update(deltaTime);
        projectileLifetimeSystem.update(deltaTime);
        tickRespawns(deltaTime);

        broadcastSnapshot();
    }

    private static Entity asEntity(Body body) {
        return body.getUserData() instanceof Entity entity ? entity : null;
    }

    /**
     * Returns whether {@code maybeProjectile} is a projectile fired by the
     * player who owns {@code maybeShip} — used both to filter out the
     * physical collision entirely ({@link ContactFilter}, so a freshly-fired
     * shot spawned overlapping its own shooter never gets pushed around by
     * Box2D resolving that overlap) and, redundantly but harmlessly, to skip
     * damage in the unlikely case a contact still gets through.
     */
    private static boolean isOwnShip(Entity maybeProjectile, Entity maybeShip) {
        if (maybeProjectile == null || maybeShip == null) {
            return false;
        }
        ProjectileComponent projectile = maybeProjectile.getComponent(ProjectileComponent.class);
        PlayerIdComponent shipOwner = maybeShip.getComponent(PlayerIdComponent.class);
        return projectile != null && shipOwner != null && projectile.getOwnerPlayerId() == shipOwner.getPlayerId();
    }

    private void registerPotentialHit(Entity maybeProjectile, Entity maybeShip) {
        if (maybeProjectile == null || maybeShip == null) {
            return;
        }
        ProjectileComponent projectile = maybeProjectile.getComponent(ProjectileComponent.class);
        PlayerIdComponent shipOwner = maybeShip.getComponent(PlayerIdComponent.class);
        if (projectile == null || shipOwner == null) {
            return;
        }
        if (isOwnShip(maybeProjectile, maybeShip)) {
            return; // no self-damage from your own shot
        }
        pendingHits.add(new HitEvent(maybeProjectile, maybeShip));
    }

    private void resolvePendingHits() {
        if (pendingHits.isEmpty()) {
            return;
        }
        Set<Entity> projectilesToRemove = new HashSet<>();
        Set<Entity> shipsToCheck = new HashSet<>();
        for (HitEvent hit : pendingHits) {
            if (!projectilesToRemove.add(hit.projectile)) {
                continue; // already resolved this tick (e.g. two simultaneous contact events)
            }
            float damage = hit.projectile.getComponent(ProjectileComponent.class).getDamage();
            ShipDamage.apply(hit.ship.getComponent(ShieldComponent.class), hit.ship.getComponent(HullComponent.class), damage);
            shipsToCheck.add(hit.ship);
        }
        pendingHits.clear();

        for (Entity projectile : projectilesToRemove) {
            world.destroyBody(projectile.getComponent(PhysicsBodyComponent.class).getBody());
            engine.removeEntity(projectile);
        }
        for (Entity ship : shipsToCheck) {
            if (ship.getComponent(HullComponent.class).isDestroyed()) {
                handleShipDestroyed(ship);
            }
        }
    }

    private void handleShipDestroyed(Entity ship) {
        int playerId = ship.getComponent(PlayerIdComponent.class).getPlayerId();
        world.destroyBody(ship.getComponent(PhysicsBodyComponent.class).getBody());
        engine.removeEntity(ship);
        shipsByPlayerId.remove(playerId);
        respawnTimers.put(playerId, RESPAWN_DELAY_SECONDS);
        sendToAllTCP(new ShipDestroyedMessage(playerId));
    }

    private void tickRespawns(float deltaTime) {
        Iterator<Map.Entry<Integer, Float>> iterator = respawnTimers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Float> entry = iterator.next();
            float remaining = entry.getValue() - deltaTime;
            if (remaining <= 0f) {
                int playerId = entry.getKey();
                iterator.remove();
                respawnShip(playerId);
            } else {
                entry.setValue(remaining);
            }
        }
    }

    private void respawnShip(int playerId) {
        // Same fixed spawn point as an initial join for now - map/arena design (design.md 7)
        // is still an open question.
        float spawnX = 0f;
        float spawnY = 0f;
        spawnShip(playerId, spawnX, spawnY);
        Connection connection = connectionsByPlayerId.get(playerId);
        if (connection != null) {
            connection.sendTCP(new ShipSpawnedMessage(playerId, spawnX, spawnY));
        }
    }

    @Override
    protected HandshakeResponse handleHandshake(Connection connection, HandshakeRequest request) {
        int playerId = connection.getID();
        // Fixed spawn point for now - map/arena design (design.md 7) is still an open question.
        float spawnX = 0f;
        float spawnY = 0f;
        pendingActions.add(() -> {
            connectionsByPlayerId.put(playerId, connection);
            spawnShip(playerId, spawnX, spawnY);
        });
        connection.sendTCP(new ShipSpawnedMessage(playerId, spawnX, spawnY));
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
        pendingActions.add(() -> {
            connectionsByPlayerId.remove(playerId);
            respawnTimers.remove(playerId);
            despawnShip(playerId);
        });
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
            // Input that arrived before the queued spawn ran, or after despawn/death; harmless to drop.
            return;
        }
        ship.getComponent(NetworkInputComponent.class).set(
            input.isThrustForward(), input.isThrustReverse(), input.isTurnLeft(), input.isTurnRight(), input.isFiring());
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
        ShipState[] shipStates = new ShipState[shipsByPlayerId.size()];
        int i = 0;
        for (Map.Entry<Integer, Entity> entry : shipsByPlayerId.entrySet()) {
            Entity ship = entry.getValue();
            Body body = ship.getComponent(PhysicsBodyComponent.class).getBody();
            HullComponent hull = ship.getComponent(HullComponent.class);
            ShieldComponent shield = ship.getComponent(ShieldComponent.class);
            shipStates[i++] = new ShipState(entry.getKey(),
                body.getPosition().x, body.getPosition().y, body.getAngle(),
                body.getLinearVelocity().x, body.getLinearVelocity().y, body.getAngularVelocity(),
                hull.getCurrent(), hull.getMax(), shield.getCurrent(), shield.getMax());
        }

        ImmutableArray<Entity> projectileEntities = engine.getEntitiesFor(
            Family.all(ProjectileComponent.class, PhysicsBodyComponent.class).get());
        ProjectileState[] projectileStates = new ProjectileState[projectileEntities.size()];
        for (int j = 0; j < projectileEntities.size(); j++) {
            Entity entity = projectileEntities.get(j);
            ProjectileComponent projectile = entity.getComponent(ProjectileComponent.class);
            Body body = entity.getComponent(PhysicsBodyComponent.class).getBody();
            projectileStates[j] = new ProjectileState(projectile.getProjectileId(), projectile.getOwnerPlayerId(),
                body.getPosition().x, body.getPosition().y, body.getAngle());
        }

        sendToAllUDP(new WorldSnapshotMessage(shipStates, projectileStates));
    }

    /**
     * One detected, not-yet-resolved projectile-vs-ship contact.
     */
    private record HitEvent(Entity projectile, Entity ship) {
    }
}
