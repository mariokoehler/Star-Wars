package de.mkoehler.starwars.server;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
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
import de.mkoehler.starwars.net.messages.TurretToggleMessage;
import de.mkoehler.starwars.net.messages.WorldSnapshotMessage;
import de.mkoehler.starwars.server.accounts.AccountStore;
import de.mkoehler.starwars.server.accounts.AuthResult;
import de.mkoehler.starwars.sim.PowerSystem;
import de.mkoehler.starwars.sim.ShipDamage;
import de.mkoehler.starwars.sim.ShipFactory;
import de.mkoehler.starwars.sim.ShipStats;
import de.mkoehler.starwars.sim.ShipType;
import de.mkoehler.starwars.sim.components.CombatTimerComponent;
import de.mkoehler.starwars.sim.components.HullComponent;
import de.mkoehler.starwars.sim.components.NetworkInputComponent;
import de.mkoehler.starwars.sim.components.PhysicsBodyComponent;
import de.mkoehler.starwars.sim.components.PlayerIdComponent;
import de.mkoehler.starwars.sim.components.PowerDistributionComponent;
import de.mkoehler.starwars.sim.components.ProjectileComponent;
import de.mkoehler.starwars.sim.components.ShieldComponent;
import de.mkoehler.starwars.sim.components.ShipTypeComponent;
import de.mkoehler.starwars.sim.components.TurretComponent;
import de.mkoehler.starwars.sim.systems.CombatTimerSystem;
import de.mkoehler.starwars.sim.systems.PhysicsSystem;
import de.mkoehler.starwars.sim.systems.ProjectileLifetimeSystem;
import de.mkoehler.starwars.sim.systems.ShieldRegenSystem;
import de.mkoehler.starwars.sim.systems.ShipControlSystem;
import de.mkoehler.starwars.sim.systems.TurretSystem;
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
import java.util.concurrent.atomic.AtomicInteger;

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
    /** design.md 2.3's combat-lock window: ESC-leave is blocked within this long of firing or being hit. */
    private static final float COMBAT_LOCK_SECONDS = 20f;

    private final World world = new World(new Vector2(0, 0), true);
    private final Engine engine = new Engine();
    // Shared between WeaponSystem and TurretSystem - both fire real projectiles into the same
    // world, so they must draw ids from the same counter or two live projectiles could collide.
    private final AtomicInteger nextProjectileId = new AtomicInteger();
    private final ShipControlSystem shipControlSystem = new ShipControlSystem();
    private final PhysicsSystem physicsSystem = new PhysicsSystem(world);
    private final WeaponSystem weaponSystem = new WeaponSystem(engine, world, nextProjectileId);
    private final TurretSystem turretSystem = new TurretSystem(engine, world, nextProjectileId);
    private final ProjectileLifetimeSystem projectileLifetimeSystem = new ProjectileLifetimeSystem(engine, world);
    private final ShieldRegenSystem shieldRegenSystem = new ShieldRegenSystem();
    private final CombatTimerSystem combatTimerSystem = new CombatTimerSystem();

    private final Map<Integer, Entity> shipsByPlayerId = new HashMap<>();
    private final Map<Integer, Connection> connectionsByPlayerId = new HashMap<>();
    private final Map<Integer, ShipType> shipTypeByPlayerId = new HashMap<>();
    private final Map<Integer, Float> respawnTimers = new HashMap<>();
    private final List<HitEvent> pendingHits = new ArrayList<>();
    private final Queue<Runnable> pendingActions = new ConcurrentLinkedQueue<>();
    // Resolved via Gdx.files.local (relative to wherever the server process is launched from,
    // design.md 3.6) rather than hardcoded, but AccountStore itself has no libGDX dependency -
    // it's directly unit-tested against a plain java.nio.file.Path.
    private final AccountStore accountStore =
        new AccountStore(Gdx.files.local("data/accounts.json").file().toPath());

    /**
     * Creates the game server, wiring up the simulation systems and the
     * projectile-vs-ship hit detection.
     */
    public GameNetworkServer() {
        engine.addSystem(shipControlSystem);
        engine.addSystem(physicsSystem);
        engine.addSystem(weaponSystem);
        engine.addSystem(turretSystem);
        engine.addSystem(projectileLifetimeSystem);
        engine.addSystem(shieldRegenSystem);
        engine.addSystem(combatTimerSystem);

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
     * runs every enabled turret's autonomous scan/track/fire behavior
     * (design.md — turret weapons, sharing each ship's weapon capacitor with
     * its main gun), resolves any hits (splitting damage between shield and
     * hull, see {@link ShipDamage}), regenerates shields, advances every
     * ship's combat-lock timers (design.md 2.3), expires old projectiles,
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
        // After the main gun, not before - both draw from the same shared capacitor (see
        // TurretSystem's Javadoc), so the player's own held-fire input gets first claim on it
        // each tick over the autonomous turret.
        turretSystem.update(deltaTime);

        resolvePendingHits();
        shieldRegenSystem.update(deltaTime);
        combatTimerSystem.update(deltaTime);
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
            hit.ship.getComponent(CombatTimerComponent.class).markHit();
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
        int playerId = destroyShipEntity(ship);
        respawnTimers.put(playerId, RESPAWN_DELAY_SECONDS);
        sendToAllTCP(new ShipDestroyedMessage(playerId));
    }

    /**
     * Grants a leave-match request (design.md 2.3): destroys the ship the
     * same way a combat death does — including broadcasting the same
     * {@link ShipDestroyedMessage}, so other clients see an identical
     * explosion, per design.md 2.3's "visually indistinguishable"
     * requirement — but deliberately does <strong>not</strong> schedule a
     * respawn timer, since the player is leaving to Ship Selection (a full
     * reconnect for their next match), not waiting to respawn into this
     * one. No kill credit/XP is awarded either way, since no such system
     * exists yet to award it through.
     *
     * @param playerId the leaving player's id
     */
    private void selfDestructShip(int playerId) {
        Entity ship = shipsByPlayerId.get(playerId);
        destroyShipEntity(ship);
        sendToAllTCP(new ShipDestroyedMessage(playerId));
    }

    /**
     * Tears down a ship's Box2D body and Ashley entity and removes it from
     * {@link #shipsByPlayerId} — the teardown shared by both a combat death
     * ({@link #handleShipDestroyed}, which also schedules a respawn) and a
     * granted leave-match request ({@link #selfDestructShip}, which doesn't).
     *
     * @param ship the ship entity to tear down
     * @return the destroyed ship's owning player id
     */
    private int destroyShipEntity(Entity ship) {
        int playerId = ship.getComponent(PlayerIdComponent.class).getPlayerId();
        world.destroyBody(ship.getComponent(PhysicsBodyComponent.class).getBody());
        engine.removeEntity(ship);
        shipsByPlayerId.remove(playerId);
        return playerId;
    }

    /**
     * Applies design.md 2.3's combat-lock rule to a leave-match request:
     * grants it (self-destructing the ship) if the player hasn't fired or
     * been hit in the last {@value #COMBAT_LOCK_SECONDS} seconds, otherwise
     * denies it. Dropped harmlessly if the ship isn't spawned right now.
     *
     * @param playerId   the requesting player's id
     * @param connection that player's connection, to reply to on denial
     */
    private void handleLeaveMatchRequest(int playerId, Connection connection) {
        Entity ship = shipsByPlayerId.get(playerId);
        if (ship == null) {
            return;
        }
        CombatTimerComponent combatTimer = ship.getComponent(CombatTimerComponent.class);
        if (combatTimer.isInCombat(COMBAT_LOCK_SECONDS)) {
            connection.sendTCP(new LeaveMatchDeniedMessage());
            return;
        }
        selfDestructShip(playerId);
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
        // The player's requested ship type doesn't change across respawns within a match -
        // recorded once at spawn-request time (see handleSpawnRequest), just read back here.
        ShipType shipType = shipTypeByPlayerId.get(playerId);
        spawnShip(playerId, spawnX, spawnY, shipType);
        Connection connection = connectionsByPlayerId.get(playerId);
        if (connection != null) {
            connection.sendTCP(new ShipSpawnedMessage(playerId, spawnX, spawnY, shipType));
        }
    }

    /**
     * Authenticates (or creates, design.md 3.6) the connecting player's
     * account via {@link #accountStore}. Deliberately does <b>not</b> spawn
     * a ship - logging in only proves the account, it happens before the
     * player has even chosen a ship type on the Ship Selection screen
     * (design.md 5.1); see {@link #handleSpawnRequest} for the step that
     * actually joins a match.
     */
    @Override
    protected HandshakeResponse handleHandshake(Connection connection, HandshakeRequest request) {
        int playerId = connection.getID();
        AuthResult result = accountStore.login(request.getLogin(), request.getPassword(), request.getDisplayName());
        if (result.success()) {
            pendingActions.add(() -> connectionsByPlayerId.put(playerId, connection));
        }
        return new HandshakeResponse(result.success(), result.message());
    }

    /**
     * Spawns a ship for a player that has already logged in (see
     * {@link #handleHandshake}) and just chose a ship type on the Ship
     * Selection screen (design.md 5.1) - the actual "join the match" moment.
     */
    private void handleSpawnRequest(int playerId, Connection connection, ShipType shipType) {
        // Fixed spawn point for now - map/arena design (design.md 7) is still an open question.
        float spawnX = 0f;
        float spawnY = 0f;
        shipTypeByPlayerId.put(playerId, shipType);
        spawnShip(playerId, spawnX, spawnY, shipType);
        connection.sendTCP(new ShipSpawnedMessage(playerId, spawnX, spawnY, shipType));
    }

    @Override
    protected void onReceived(Connection connection, Object object) {
        super.onReceived(connection, object);
        if (object instanceof PlayerInputMessage input) {
            int playerId = connection.getID();
            pendingActions.add(() -> applyInput(playerId, input));
        } else if (object instanceof PowerAdjustMessage adjust) {
            int playerId = connection.getID();
            pendingActions.add(() -> applyPowerAdjust(playerId, adjust.getKind(), adjust.getTarget()));
        } else if (object instanceof LeaveMatchRequest) {
            int playerId = connection.getID();
            pendingActions.add(() -> handleLeaveMatchRequest(playerId, connection));
        } else if (object instanceof TurretToggleMessage) {
            int playerId = connection.getID();
            pendingActions.add(() -> applyTurretToggle(playerId));
        } else if (object instanceof SpawnRequest spawnRequest) {
            int playerId = connection.getID();
            pendingActions.add(() -> handleSpawnRequest(playerId, connection, spawnRequest.getShipType()));
        }
    }

    @Override
    protected void onDisconnected(Connection connection) {
        int playerId = connection.getID();
        pendingActions.add(() -> {
            connectionsByPlayerId.remove(playerId);
            shipTypeByPlayerId.remove(playerId);
            respawnTimers.remove(playerId);
            despawnShip(playerId);
        });
        sendToAllTCP(new PlayerLeftMessage(playerId));
    }

    private void spawnShip(int playerId, float x, float y, ShipType shipType) {
        if (shipsByPlayerId.containsKey(playerId)) {
            return;
        }
        Entity ship = ShipFactory.createShip(engine, world, playerId, x, y, ShipStats.forType(shipType));
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

    /**
     * Applies one power-distribution action to a ship's authoritative power
     * split (design.md 2.2). Dropped harmlessly if the ship isn't spawned
     * right now (e.g. the message arrived just before a death or just after
     * a disconnect) — the client's own local mirror of this state only ever
     * advances by sending exactly these actions in the first place, so
     * there's nothing to reconcile even if one is dropped here.
     *
     * @param playerId the player whose ship to adjust
     * @param kind     which action to apply
     * @param target   the system to act on; unused for {@link PowerAdjustMessage.Kind#RESET}
     */
    private void applyPowerAdjust(int playerId, PowerAdjustMessage.Kind kind, PowerSystem target) {
        Entity ship = shipsByPlayerId.get(playerId);
        if (ship == null) {
            return;
        }
        PowerDistributionComponent power = ship.getComponent(PowerDistributionComponent.class);
        switch (kind) {
            case ADJUST -> power.adjust(target);
            case MAXIMIZE -> power.maximize(target);
            case RESET -> power.reset();
        }
    }

    /**
     * Toggles a ship's turret(s) on/off (design.md — turret weapons).
     * Dropped harmlessly if the ship isn't spawned right now, or its ship
     * type has no {@link TurretComponent} at all (most don't).
     *
     * @param playerId the player whose ship to toggle
     */
    private void applyTurretToggle(int playerId) {
        Entity ship = shipsByPlayerId.get(playerId);
        if (ship == null) {
            return;
        }
        TurretComponent turrets = ship.getComponent(TurretComponent.class);
        if (turrets != null) {
            turrets.toggle();
        }
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
            ShipType shipType = ship.getComponent(ShipTypeComponent.class).getShipType();
            shipStates[i++] = new ShipState(entry.getKey(),
                body.getPosition().x, body.getPosition().y, body.getAngle(),
                body.getLinearVelocity().x, body.getLinearVelocity().y, body.getAngularVelocity(),
                hull.getCurrent(), hull.getMax(), shield.getCurrent(), shield.getMax(), shipType,
                turretAimAngles(ship));
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

    private static final float[] NO_TURRETS = new float[0];

    /**
     * Returns a ship's turrets' current aim angles, one per mount in
     * authored order, for broadcasting in its {@link ShipState} — empty for
     * a ship type with no {@link TurretComponent} at all.
     *
     * @param ship the ship entity
     * @return the aim angles, in radians
     */
    private static float[] turretAimAngles(Entity ship) {
        TurretComponent turrets = ship.getComponent(TurretComponent.class);
        if (turrets == null) {
            return NO_TURRETS;
        }
        List<TurretComponent.TurretMount> mounts = turrets.getMounts();
        float[] angles = new float[mounts.size()];
        for (int i = 0; i < mounts.size(); i++) {
            angles[i] = mounts.get(i).getAimAngleRadians();
        }
        return angles;
    }

    /**
     * One detected, not-yet-resolved projectile-vs-ship contact.
     */
    private record HitEvent(Entity projectile, Entity ship) {
    }
}
