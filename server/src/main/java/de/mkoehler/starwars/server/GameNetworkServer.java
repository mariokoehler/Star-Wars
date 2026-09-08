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
import de.mkoehler.starwars.net.messages.PlayerScoreEntry;
import de.mkoehler.starwars.net.messages.PowerAdjustMessage;
import de.mkoehler.starwars.net.messages.ProjectileState;
import de.mkoehler.starwars.net.messages.RadarPulseRequest;
import de.mkoehler.starwars.net.messages.ScoreboardMessage;
import de.mkoehler.starwars.net.messages.ShipDestroyedMessage;
import de.mkoehler.starwars.net.messages.ShipSpawnedMessage;
import de.mkoehler.starwars.net.messages.ShipState;
import de.mkoehler.starwars.net.messages.SpawnRequest;
import de.mkoehler.starwars.net.messages.TurretToggleMessage;
import de.mkoehler.starwars.net.messages.UnlockShipRequest;
import de.mkoehler.starwars.net.messages.UnlockShipResponse;
import de.mkoehler.starwars.net.messages.WorldSnapshotMessage;
import de.mkoehler.starwars.server.accounts.AccountStore;
import de.mkoehler.starwars.server.accounts.AuthResult;
import de.mkoehler.starwars.server.accounts.PlayerAccount;
import de.mkoehler.starwars.sim.KillXp;
import de.mkoehler.starwars.sim.PowerSystem;
import de.mkoehler.starwars.sim.ShipDamage;
import de.mkoehler.starwars.sim.ShipFactory;
import de.mkoehler.starwars.sim.ShipStats;
import de.mkoehler.starwars.sim.ShipTree;
import de.mkoehler.starwars.sim.ShipType;
import de.mkoehler.starwars.sim.ShipUnlocks;
import de.mkoehler.starwars.sim.components.CombatTimerComponent;
import de.mkoehler.starwars.sim.components.HullComponent;
import de.mkoehler.starwars.sim.components.NetworkInputComponent;
import de.mkoehler.starwars.sim.components.PhysicsBodyComponent;
import de.mkoehler.starwars.sim.components.PlayerIdComponent;
import de.mkoehler.starwars.sim.components.PowerDistributionComponent;
import de.mkoehler.starwars.sim.components.ProjectileComponent;
import de.mkoehler.starwars.sim.components.RadarComponent;
import de.mkoehler.starwars.sim.components.ShieldComponent;
import de.mkoehler.starwars.sim.components.ShipTypeComponent;
import de.mkoehler.starwars.sim.components.TurretComponent;
import de.mkoehler.starwars.sim.systems.CombatTimerSystem;
import de.mkoehler.starwars.sim.systems.PhysicsSystem;
import de.mkoehler.starwars.sim.systems.ProjectileLifetimeSystem;
import de.mkoehler.starwars.sim.systems.RadarSystem;
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
import java.util.Optional;
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

    private static final String TAG = "GameNetworkServer";

    /** Fixed delay between a ship being destroyed and it respawning; not a tuned value. */
    private static final float RESPAWN_DELAY_SECONDS = 3f;
    /** design.md 2.3's combat-lock window: ESC-leave is blocked within this long of firing or being hit. */
    private static final float COMBAT_LOCK_SECONDS = 20f;
    /** How often {@link #broadcastScoreboard()} runs - the TAB overlay (design.md 2.11) doesn't need per-tick freshness. */
    private static final float SCOREBOARD_BROADCAST_INTERVAL_SECONDS = 1f;
    /**
     * {@link #tick(float)} logs a warning if called with a {@code deltaTime}
     * beyond this - temporary diagnostic instrumentation added while
     * investigating an intermittent screen-transition pause (CLAUDE.md): a
     * tick this slow means the tick thread was blocked/stalled since the
     * previous call, which (via {@code PhysicsSystem}'s existing
     * {@code MAX_STEPS_PER_FRAME} clamp) can take many subsequent ticks to
     * fully catch up from, showing up as erratic movement for a while
     * afterward even once the actual stall is over.
     */
    private static final float TICK_STALL_WARN_SECONDS = 0.5f;

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
    private final RadarSystem radarSystem = new RadarSystem(engine);

    private final Map<Integer, Entity> shipsByPlayerId = new HashMap<>();
    private final Map<Integer, Connection> connectionsByPlayerId = new HashMap<>();
    private final Map<Integer, ShipType> shipTypeByPlayerId = new HashMap<>();
    // Populated on a successful handshake, removed on disconnect - lets a kill/death/XP change
    // be credited to the right account (design.md - kill XP, 2.11's addendum) via nothing more
    // than a playerId.
    private final Map<Integer, String> loginByPlayerId = new HashMap<>();
    private final Map<Integer, Float> respawnTimers = new HashMap<>();
    private final List<HitEvent> pendingHits = new ArrayList<>();
    private float scoreboardBroadcastTimer;
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
        engine.addSystem(radarSystem);

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
     * advances respawn timers, broadcasts the resulting world state to every
     * connected client, and - on its own, much slower cadence, see
     * {@link #broadcastScoreboard()} - the scoreboard overlay's data
     * (design.md 2.11).
     *
     * @param deltaTime time since the last tick, in seconds
     */
    public void tick(float deltaTime) {
        if (deltaTime > TICK_STALL_WARN_SECONDS) {
            Gdx.app.log(TAG, "tick() called with deltaTime=" + deltaTime
                + "s - the tick thread was likely blocked/stalled since the previous tick");
        }

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

        // Recomputed against this tick's freshest (post-physics-step) positions, immediately
        // before broadcastSnapshot() reads it to decide what each player actually sees.
        radarSystem.update(deltaTime);

        broadcastSnapshot();

        scoreboardBroadcastTimer += deltaTime;
        if (scoreboardBroadcastTimer >= SCOREBOARD_BROADCAST_INTERVAL_SECONDS) {
            scoreboardBroadcastTimer = 0f;
            broadcastScoreboard();
        }
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
        // Credits whichever hit actually tips a ship's hull to zero, not just whichever hit
        // happens to be processed last for it this tick (design.md - kill XP) - matters when
        // more than one hit lands on the same ship in a single tick.
        Map<Entity, Integer> killerPlayerIdByShip = new HashMap<>();
        for (HitEvent hit : pendingHits) {
            if (!projectilesToRemove.add(hit.projectile)) {
                continue; // already resolved this tick (e.g. two simultaneous contact events)
            }
            float damage = hit.projectile.getComponent(ProjectileComponent.class).getDamage();
            HullComponent hull = hit.ship.getComponent(HullComponent.class);
            boolean wasAlreadyDestroyed = hull.isDestroyed();
            ShipDamage.apply(hit.ship.getComponent(ShieldComponent.class), hull, damage);
            hit.ship.getComponent(CombatTimerComponent.class).markHit();
            shipsToCheck.add(hit.ship);
            if (!wasAlreadyDestroyed && hull.isDestroyed()) {
                int killerPlayerId = hit.projectile.getComponent(ProjectileComponent.class).getOwnerPlayerId();
                killerPlayerIdByShip.put(hit.ship, killerPlayerId);
            }
        }
        pendingHits.clear();

        for (Entity projectile : projectilesToRemove) {
            world.destroyBody(projectile.getComponent(PhysicsBodyComponent.class).getBody());
            engine.removeEntity(projectile);
        }
        for (Entity ship : shipsToCheck) {
            if (ship.getComponent(HullComponent.class).isDestroyed()) {
                handleShipDestroyed(ship, killerPlayerIdByShip.get(ship));
            }
        }
    }

    /**
     * Handles a combat kill: tears down the victim's ship, schedules their
     * respawn, credits the kill/death to each account's lifetime totals
     * (design.md 2.11's addendum - persisted, same as XP, specifically so
     * they survive a combat death rather than resetting every time, which
     * they did back when they were tracked as in-memory per-connection
     * state instead), awards the killer kill XP ({@link #awardKillXp}),
     * broadcasts the just-updated scoreboard, and broadcasts the death.
     * <p>
     * The scoreboard broadcast happens deliberately <i>before</i> the
     * {@link ShipDestroyedMessage} below, both over the same
     * reliable/ordered TCP channel: this guarantees the victim's own
     * client has already applied the fresh {@link ScoreboardMessage} (see
     * {@code Client#onReceived}) by the time it reacts to its own death
     * and switches to the Death Screen, which shows a snapshot of exactly
     * that data (design.md 5.1's addendum) - without this ordering, the
     * screen could show stats from just before this death.
     *
     * @param ship           the destroyed ship entity
     * @param killerPlayerId the id of whoever landed the fatal hit, or
     *                       {@code null} if that isn't known (see
     *                       {@link #resolvePendingHits} - not expected in
     *                       practice, handled rather than risking a crash)
     */
    private void handleShipDestroyed(Entity ship, Integer killerPlayerId) {
        int playerId = destroyShipEntity(ship);
        respawnTimers.put(playerId, RESPAWN_DELAY_SECONDS);
        recordDeath(playerId);
        if (killerPlayerId != null) {
            recordKill(killerPlayerId);
        }
        awardKillXp(playerId, killerPlayerId);
        broadcastScoreboard();
        sendToAllTCP(new ShipDestroyedMessage(playerId));
    }

    /**
     * Credits one death to a player's account, if their login is still
     * known - see {@link #handleShipDestroyed}.
     *
     * @param playerId the destroyed ship's owning player id
     */
    private void recordDeath(int playerId) {
        String login = loginByPlayerId.get(playerId);
        if (login != null) {
            accountStore.addDeath(login);
        }
    }

    /**
     * Credits one kill to a player's account, if their login is still
     * known - see {@link #handleShipDestroyed}.
     *
     * @param playerId the killer's player id
     */
    private void recordKill(int playerId) {
        String login = loginByPlayerId.get(playerId);
        if (login != null) {
            accountStore.addKill(login);
        }
    }

    /**
     * Awards {@link KillXp} to the killer's account (design.md - kill XP),
     * if the kill is attributable to a specific player and both ships'
     * types and the killer's login are still known - not expected to ever
     * be missing in practice (a ship can only be destroyed by a hit, and
     * both players are still fully tracked mid-tick even if one of them
     * disconnects, since disconnects are themselves queued through
     * {@link #pendingActions}), but this fails soft rather than crashing
     * the tick loop if one of them somehow is.
     *
     * @param victimPlayerId the destroyed ship's owning player id
     * @param killerPlayerId the id of whoever landed the fatal hit, or
     *                       {@code null} if unknown
     */
    private void awardKillXp(int victimPlayerId, Integer killerPlayerId) {
        if (killerPlayerId == null) {
            return;
        }
        ShipType victimShipType = shipTypeByPlayerId.get(victimPlayerId);
        ShipType killerShipType = shipTypeByPlayerId.get(killerPlayerId);
        String killerLogin = loginByPlayerId.get(killerPlayerId);
        if (victimShipType == null || killerShipType == null || killerLogin == null) {
            return;
        }
        accountStore.addXp(killerLogin, KillXp.calculate(victimShipType, killerShipType));
    }

    /**
     * Grants a leave-match request (design.md 2.3): destroys the ship the
     * same way a combat death does — including broadcasting the same
     * {@link ShipDestroyedMessage}, so other clients see an identical
     * explosion, per design.md 2.3's "visually indistinguishable"
     * requirement — but deliberately does <strong>not</strong> schedule a
     * respawn timer, since the player is leaving to Ship Selection (a full
     * reconnect for their next match), not waiting to respawn into this
     * one. No kill credit/XP is awarded either way — this is a self-
     * destruct, not a kill, so it never goes through {@link #handleShipDestroyed}
     * at all.
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
     * actually joins a match. On acceptance, the response also carries the
     * account's XP/unlocked ships (design.md - ship unlocks) -
     * {@code ShipSelectionScreen} reads these from its own fresh handshake.
     * <p>
     * Logs how long the call took - temporary diagnostic instrumentation
     * added while investigating an intermittent screen-transition pause
     * (CLAUDE.md), to rule in/out {@link #accountStore}'s (synchronous,
     * in-memory-only, see its own class Javadoc) login check as the cause.
     * Runs on KryoNet's network thread (see the class Javadoc), not the
     * tick thread, so this can't itself be why {@link #tick(float)} would
     * see a large {@code deltaTime}.
     */
    @Override
    protected HandshakeResponse handleHandshake(Connection connection, HandshakeRequest request) {
        long startMillis = System.currentTimeMillis();
        try {
            int playerId = connection.getID();
            AuthResult result = accountStore.login(request.getLogin(), request.getPassword(), request.getDisplayName());
            if (!result.success()) {
                return new HandshakeResponse(false, result.message());
            }
            pendingActions.add(() -> {
                connectionsByPlayerId.put(playerId, connection);
                loginByPlayerId.put(playerId, request.getLogin());
            });
            PlayerAccount account = result.account();
            return new HandshakeResponse(true, result.message(), account.getXp(), toArray(account.getUnlockedShips()));
        } finally {
            Gdx.app.log(TAG, "handleHandshake(" + request.getLogin() + ") took "
                + (System.currentTimeMillis() - startMillis) + "ms");
        }
    }

    /**
     * Spawns a ship for a player that has already logged in (see
     * {@link #handleHandshake}) and just chose a ship type on the Ship
     * Selection screen (design.md 5.1) - the actual "join the match" moment.
     * Dropped harmlessly if the requested ship type isn't actually unlocked
     * on this player's account (design.md - ship unlocks) - the client's
     * own UI already prevents this in normal play (a locked ship can't be
     * selected to Start with), this is just the same "don't trust the
     * client" defense every other player-controlled action here already
     * gets.
     */
    private void handleSpawnRequest(int playerId, Connection connection, ShipType shipType) {
        String login = loginByPlayerId.get(playerId);
        Optional<PlayerAccount> account = login != null ? accountStore.findByLogin(login) : Optional.empty();
        if (account.isEmpty() || !ShipUnlocks.isUnlocked(shipType, account.get().getUnlockedShips())) {
            return;
        }
        // Fixed spawn point for now - map/arena design (design.md 7) is still an open question.
        float spawnX = 0f;
        float spawnY = 0f;
        shipTypeByPlayerId.put(playerId, shipType);
        spawnShip(playerId, spawnX, spawnY, shipType);
        connection.sendTCP(new ShipSpawnedMessage(playerId, spawnX, spawnY, shipType));
    }

    /**
     * Handles an {@link UnlockShipRequest} (design.md - ship unlocks):
     * re-validates both the {@link ShipTree} branch prerequisite and XP
     * affordability server-side (the client's own padlock UI is only ever a
     * convenience, never trusted on its own), and if both pass, adds the
     * ship type to the account's unlocked set and persists it. Always
     * replies with the account's current XP/unlocked-ships state, whether
     * the ship ends up unlocked just now, was already unlocked (treated as
     * a harmless success, not an error), or the request is denied.
     *
     * @param playerId   the requesting player's id
     * @param connection that player's connection, to reply to
     * @param shipType   the ship type requested to unlock
     */
    private void handleUnlockShipRequest(int playerId, Connection connection, ShipType shipType) {
        String login = loginByPlayerId.get(playerId);
        Optional<PlayerAccount> maybeAccount = login != null ? accountStore.findByLogin(login) : Optional.empty();
        if (maybeAccount.isEmpty()) {
            return; // not logged in somehow - shouldn't happen, nothing sensible to reply with
        }
        PlayerAccount account = maybeAccount.get();
        Set<ShipType> unlockedShips = account.getUnlockedShips();
        if (ShipUnlocks.isUnlocked(shipType, unlockedShips)) {
            connection.sendTCP(new UnlockShipResponse(true, "Already unlocked.", account.getXp(), toArray(unlockedShips)));
            return;
        }
        if (!ShipTree.prerequisiteMet(shipType, unlockedShips)) {
            connection.sendTCP(new UnlockShipResponse(false, "Unlock the previous ship in this branch first.", account.getXp(), toArray(unlockedShips)));
            return;
        }
        int availableXp = ShipUnlocks.availableXp(account.getXp(), unlockedShips);
        int cost = ShipStats.forType(shipType).getUnlockCostXp();
        if (availableXp < cost) {
            connection.sendTCP(new UnlockShipResponse(false, "Not enough XP.", account.getXp(), toArray(unlockedShips)));
            return;
        }
        accountStore.unlockShip(login, shipType);
        Set<ShipType> updated = accountStore.findByLogin(login).orElseThrow().getUnlockedShips();
        connection.sendTCP(new UnlockShipResponse(true, "Unlocked.", account.getXp(), toArray(updated)));
    }

    private static ShipType[] toArray(Set<ShipType> shipTypes) {
        return shipTypes.toArray(new ShipType[0]);
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
        } else if (object instanceof UnlockShipRequest unlockRequest) {
            int playerId = connection.getID();
            pendingActions.add(() -> handleUnlockShipRequest(playerId, connection, unlockRequest.getShipType()));
        } else if (object instanceof RadarPulseRequest) {
            int playerId = connection.getID();
            pendingActions.add(() -> applyRadarPulse(playerId));
        }
    }

    @Override
    protected void onDisconnected(Connection connection) {
        int playerId = connection.getID();
        pendingActions.add(() -> {
            connectionsByPlayerId.remove(playerId);
            shipTypeByPlayerId.remove(playerId);
            loginByPlayerId.remove(playerId);
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

    /**
     * Triggers a ship's radar pulse (design.md 2.14, the "R" keybind).
     * Dropped harmlessly if the ship isn't spawned right now, its ship type
     * has the pulse disabled, or it's still on cooldown from a previous
     * pulse — same "don't trust the client, just don't let a bad request do
     * anything" treatment as {@link #applyTurretToggle}.
     *
     * @param playerId the requesting player's id
     */
    private void applyRadarPulse(int playerId) {
        Entity ship = shipsByPlayerId.get(playerId);
        if (ship == null) {
            return;
        }
        ShipType shipType = shipTypeByPlayerId.get(playerId);
        ShipStats stats = ShipStats.forType(shipType);
        if (!stats.isRadarPulseEnabled()) {
            return;
        }
        RadarComponent radar = ship.getComponent(RadarComponent.class);
        if (radar.isPulseOnCooldown()) {
            return;
        }
        radar.triggerPulse(stats.getRadarPulseCooldownSeconds(), stats.getRadarPulseRevealDurationSeconds());
    }

    private void despawnShip(int playerId) {
        Entity ship = shipsByPlayerId.remove(playerId);
        if (ship == null) {
            return;
        }
        world.destroyBody(ship.getComponent(PhysicsBodyComponent.class).getBody());
        engine.removeEntity(ship);
    }

    /**
     * Builds and sends one personalized {@link WorldSnapshotMessage} per
     * spawned player (design.md 2.14) — no longer a single shared broadcast.
     * Each recipient's ship list always includes their own ship, plus
     * whichever other ships their {@link RadarComponent} currently detects
     * (computed by {@link RadarSystem} earlier this same tick) — a ship this
     * player doesn't currently detect simply isn't included, the mechanism
     * by which radar/fog-of-war actually works (never trust a client to hide
     * something on its own). Projectiles are <em>not</em> filtered this way
     * — every currently-alive projectile is included for everyone, same as
     * before radar existed (design.md 2.14 flags this as a deliberate scope
     * boundary, not an oversight).
     */
    private void broadcastSnapshot() {
        Map<Integer, ShipState> shipStatesByPlayerId = new HashMap<>();
        for (Map.Entry<Integer, Entity> entry : shipsByPlayerId.entrySet()) {
            Entity ship = entry.getValue();
            Body body = ship.getComponent(PhysicsBodyComponent.class).getBody();
            HullComponent hull = ship.getComponent(HullComponent.class);
            ShieldComponent shield = ship.getComponent(ShieldComponent.class);
            ShipType shipType = ship.getComponent(ShipTypeComponent.class).getShipType();
            RadarComponent radar = ship.getComponent(RadarComponent.class);
            shipStatesByPlayerId.put(entry.getKey(), new ShipState(entry.getKey(),
                body.getPosition().x, body.getPosition().y, body.getAngle(),
                body.getLinearVelocity().x, body.getLinearVelocity().y, body.getAngularVelocity(),
                hull.getCurrent(), hull.getMax(), shield.getCurrent(), shield.getMax(), shipType,
                turretAimAngles(ship), radar.getPulseCooldownRemaining()));
        }

        ImmutableArray<Entity> projectileEntities = engine.getEntitiesFor(
            Family.all(ProjectileComponent.class, PhysicsBodyComponent.class).get());
        ProjectileState[] projectileStates = new ProjectileState[projectileEntities.size()];
        for (int j = 0; j < projectileEntities.size(); j++) {
            Entity entity = projectileEntities.get(j);
            ProjectileComponent projectile = entity.getComponent(ProjectileComponent.class);
            Body body = entity.getComponent(PhysicsBodyComponent.class).getBody();
            projectileStates[j] = new ProjectileState(projectile.getProjectileId(), projectile.getOwnerPlayerId(),
                body.getPosition().x, body.getPosition().y,
                body.getLinearVelocity().x, body.getLinearVelocity().y);
        }

        for (Map.Entry<Integer, Entity> entry : shipsByPlayerId.entrySet()) {
            int playerId = entry.getKey();
            Connection connection = connectionsByPlayerId.get(playerId);
            if (connection == null) {
                continue; // shouldn't happen in practice - a spawned ship always has a known connection
            }
            RadarComponent radar = entry.getValue().getComponent(RadarComponent.class);
            List<ShipState> visibleShips = new ArrayList<>(radar.getDetectedPlayerIds().size() + 1);
            visibleShips.add(shipStatesByPlayerId.get(playerId));
            for (int detectedPlayerId : radar.getDetectedPlayerIds()) {
                ShipState detected = shipStatesByPlayerId.get(detectedPlayerId);
                if (detected != null) {
                    visibleShips.add(detected);
                }
            }
            connection.sendUDP(new WorldSnapshotMessage(visibleShips.toArray(new ShipState[0]), projectileStates));
        }
    }

    /**
     * Broadcasts one {@link PlayerScoreEntry} per currently-connected player
     * (design.md 2.11's TAB overlay) - everyone tracked in
     * {@link #connectionsByPlayerId}, whether or not they've spawned a ship
     * yet, not just those with a live {@link ShipState}. XP/kills/deaths
     * are read live from each player's account (design.md 2.11's addendum -
     * all three are lifetime totals now), not cached anywhere here. Sent
     * over the reliable TCP channel: on {@link #SCOREBOARD_BROADCAST_INTERVAL_SECONDS}'s
     * slower periodic cadence (this data isn't normally render-critical),
     * and once more immediately whenever a kill/death actually happens
     * (see {@link #handleShipDestroyed}).
     */
    private void broadcastScoreboard() {
        PlayerScoreEntry[] entries = new PlayerScoreEntry[connectionsByPlayerId.size()];
        int i = 0;
        for (int playerId : connectionsByPlayerId.keySet()) {
            String login = loginByPlayerId.get(playerId);
            Optional<PlayerAccount> account = login != null ? accountStore.findByLogin(login) : Optional.empty();
            String displayName = account.map(PlayerAccount::getDisplayName).orElse("?");
            int xp = account.map(PlayerAccount::getXp).orElse(0);
            int kills = account.map(PlayerAccount::getKills).orElse(0);
            int deaths = account.map(PlayerAccount::getDeaths).orElse(0);
            entries[i++] = new PlayerScoreEntry(playerId, displayName, xp, kills, deaths);
        }
        sendToAllTCP(new ScoreboardMessage(entries));
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
