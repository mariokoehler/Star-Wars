package de.mkoehler.starwars.sim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.World;
import de.mkoehler.starwars.sim.ProjectileFactory;
import de.mkoehler.starwars.sim.TurretAiming;
import de.mkoehler.starwars.sim.components.CombatTimerComponent;
import de.mkoehler.starwars.sim.components.HullComponent;
import de.mkoehler.starwars.sim.components.PhysicsBodyComponent;
import de.mkoehler.starwars.sim.components.PlayerIdComponent;
import de.mkoehler.starwars.sim.components.TurretComponent;
import de.mkoehler.starwars.sim.components.WeaponComponent;
import de.mkoehler.starwars.sim.metadata.TurretConfig;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Drives every enabled turret's autonomous scan/track/fire behavior — the
 * "T" keybind only flips {@link TurretComponent#isEnabled()}; everything
 * else here runs on its own once that's true. Server-side only, same as
 * every other combat system.
 * <p>
 * Per turret mount, each tick: if the current target is gone (destroyed/
 * disconnected) or has left the mount's scan range, drop it and immediately
 * look for the closest remaining enemy ship within range — "the process
 * repeats from the start," per its design. With a target, the mount turns
 * toward a lead-computed intercept angle ({@link TurretAiming}) at its
 * configured turn rate, and fires once aligned, off cooldown, and the
 * ship's weapon capacitor has charge. Every mount on one ship tracks and
 * fires independently (a Star Destroyer's four turrets can have four
 * different targets), but they all draw from that <em>one</em> shared
 * capacitor — firing the turret competes with the main gun for the same
 * energy pool, both scaled by the same
 * {@link de.mkoehler.starwars.sim.PowerSystem#WEAPONS} allocation. That
 * capacitor is recharged once per tick by {@link WeaponSystem} already (for
 * every ship, turreted or not) — this system only ever reads
 * {@link WeaponComponent#canFire()} and drains it via
 * {@link WeaponComponent#consumeShot()}, never recharges it itself, or a
 * ship with both a main gun and a turret would recharge twice as fast.
 */
public class TurretSystem extends IteratingSystem {

    private static final Family LIVE_SHIP_FAMILY =
        Family.all(PhysicsBodyComponent.class, PlayerIdComponent.class, HullComponent.class).get();

    /** How close a turret's aim must be to its firing solution before it's allowed to shoot - untuned placeholder. */
    private static final float FIRING_ALIGNMENT_TOLERANCE_RADIANS = (float) Math.toRadians(5.0);

    private static final Vector2 SCRATCH_OFFSET = new Vector2();

    private final ComponentMapper<TurretComponent> turretMapper = ComponentMapper.getFor(TurretComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> bodyMapper = ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<PlayerIdComponent> playerIdMapper = ComponentMapper.getFor(PlayerIdComponent.class);
    private final ComponentMapper<WeaponComponent> weaponMapper = ComponentMapper.getFor(WeaponComponent.class);
    private final ComponentMapper<CombatTimerComponent> combatTimerMapper = ComponentMapper.getFor(CombatTimerComponent.class);

    private final Engine engine;
    private final World world;
    private final AtomicInteger nextProjectileId;

    private ImmutableArray<Entity> liveShips;

    /**
     * Creates the turret system.
     *
     * @param engine           the Ashley engine to add fired projectiles to
     * @param world            the Box2D world to create fired projectiles' bodies in
     * @param nextProjectileId a counter shared with {@code WeaponSystem} — both fire real
     *                         projectiles into the same world, so they must draw ids from the
     *                         same source or two live projectiles could collide on one id
     */
    public TurretSystem(Engine engine, World world, AtomicInteger nextProjectileId) {
        super(Family.all(TurretComponent.class, PhysicsBodyComponent.class, PlayerIdComponent.class,
            WeaponComponent.class, CombatTimerComponent.class).get());
        this.engine = engine;
        this.world = world;
        this.nextProjectileId = nextProjectileId;
    }

    @Override
    public void update(float deltaTime) {
        // Refreshed once per tick, not per turret-bearing ship - every mount's scan/validity
        // check reads from this same snapshot of "who's currently alive," which is exactly what
        // "closest enemy" and "target left scan range" mean at this instant.
        liveShips = engine.getEntitiesFor(LIVE_SHIP_FAMILY);
        super.update(deltaTime);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        TurretComponent turrets = turretMapper.get(entity);
        if (!turrets.isEnabled()) {
            return;
        }

        Body ownBody = bodyMapper.get(entity).getBody();
        int ownerPlayerId = playerIdMapper.get(entity).getPlayerId();
        WeaponComponent weapon = weaponMapper.get(entity);
        TurretConfig config = turrets.getConfig();

        for (TurretComponent.TurretMount mount : turrets.getMounts()) {
            processMount(mount, config, ownBody, ownerPlayerId, weapon, entity, deltaTime);
        }
    }

    private void processMount(TurretComponent.TurretMount mount, TurretConfig config, Body ownBody,
                               int ownerPlayerId, WeaponComponent weapon, Entity ownEntity, float deltaTime) {
        mount.tickCooldown(deltaTime);

        SCRATCH_OFFSET.set(mount.getLocalOffsetMeters()).rotateRad(ownBody.getAngle());
        float turretX = ownBody.getPosition().x + SCRATCH_OFFSET.x;
        float turretY = ownBody.getPosition().y + SCRATCH_OFFSET.y;

        if (mount.getTarget() != null && !isValidTarget(mount.getTarget(), turretX, turretY, config.getScanRangeMeters())) {
            mount.setTarget(null);
        }
        if (mount.getTarget() == null) {
            mount.setTarget(findClosestTarget(ownerPlayerId, turretX, turretY, config.getScanRangeMeters()));
        }
        if (mount.getTarget() == null) {
            return; // nothing to track or shoot at - stays parked until a target is (re)acquired
        }

        Body targetBody = bodyMapper.get(mount.getTarget()).getBody();
        float desiredAngle = TurretAiming.computeLeadAngle(turretX, turretY,
            targetBody.getPosition().x, targetBody.getPosition().y,
            targetBody.getLinearVelocity().x, targetBody.getLinearVelocity().y,
            weapon.getStats().getProjectileSpeed());

        float maxStep = (float) Math.toRadians(config.getTurnRateDegreesPerSecond()) * deltaTime;
        mount.setAimAngleRadians(TurretAiming.rotateToward(mount.getAimAngleRadians(), desiredAngle, maxStep));

        boolean aligned = Math.abs(TurretAiming.angularDifference(mount.getAimAngleRadians(), desiredAngle))
            <= FIRING_ALIGNMENT_TOLERANCE_RADIANS;
        if (aligned && mount.getCooldownRemaining() <= 0f && weapon.canFire()) {
            ProjectileFactory.createProjectile(engine, world, nextProjectileId.getAndIncrement(), ownerPlayerId,
                turretX, turretY, mount.getAimAngleRadians(),
                ownBody.getLinearVelocity().x, ownBody.getLinearVelocity().y, weapon.getStats(), true);
            weapon.consumeShot();
            mount.resetCooldown(config.getCooldownSeconds());
            combatTimerMapper.get(ownEntity).markFired();
        }
    }

    private boolean isValidTarget(Entity target, float turretX, float turretY, float scanRangeMeters) {
        if (!liveShips.contains(target, true)) {
            return false;
        }
        Body targetBody = bodyMapper.get(target).getBody();
        float dx = targetBody.getPosition().x - turretX;
        float dy = targetBody.getPosition().y - turretY;
        return dx * dx + dy * dy <= scanRangeMeters * scanRangeMeters;
    }

    private Entity findClosestTarget(int ownerPlayerId, float turretX, float turretY, float scanRangeMeters) {
        Entity closest = null;
        float closestDistanceSq = scanRangeMeters * scanRangeMeters;
        for (Entity candidate : liveShips) {
            if (playerIdMapper.get(candidate).getPlayerId() == ownerPlayerId) {
                continue; // never target your own ship
            }
            Body candidateBody = bodyMapper.get(candidate).getBody();
            float dx = candidateBody.getPosition().x - turretX;
            float dy = candidateBody.getPosition().y - turretY;
            float distanceSq = dx * dx + dy * dy;
            if (distanceSq <= closestDistanceSq) {
                closest = candidate;
                closestDistanceSq = distanceSq;
            }
        }
        return closest;
    }
}
