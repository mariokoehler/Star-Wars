package de.mkoehler.starwars.sim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.World;
import de.mkoehler.starwars.sim.PowerSystem;
import de.mkoehler.starwars.sim.ProjectileFactory;
import de.mkoehler.starwars.sim.ShipStats;
import de.mkoehler.starwars.sim.components.CombatTimerComponent;
import de.mkoehler.starwars.sim.components.NetworkInputComponent;
import de.mkoehler.starwars.sim.components.PhysicsBodyComponent;
import de.mkoehler.starwars.sim.components.PlayerIdComponent;
import de.mkoehler.starwars.sim.components.PowerDistributionComponent;
import de.mkoehler.starwars.sim.components.ShipTypeComponent;
import de.mkoehler.starwars.sim.components.WeaponComponent;
import de.mkoehler.starwars.sim.metadata.PixelPoint;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fires a projectile for every ship whose fire input is held and whose
 * weapon {@link WeaponComponent#canFire()} — cooldown expired and capacitor
 * charged enough — drawing that shot's energy cost from the capacitor each
 * time. A ship with multiple {@value #PROJECTILE_ATTACHMENT_NAME} attachment
 * points authored in its sprite metadata (design.md 2.4) fires one
 * projectile per point per shot instead of a single one from a fixed
 * offset, but still draws only one shot's energy cost for the whole volley.
 * Also recharges every ship's capacitor each tick, scaled by its current
 * {@link PowerSystem#WEAPONS} power allocation (design.md 2.2), regardless
 * of whether it's currently firing. Firing also marks
 * {@link CombatTimerComponent#markFired()}, feeding design.md 2.3's
 * combat-lock rule for leaving a match via ESC.
 * <p>
 * Runs server-side only, once per tick — unlike {@link ShipControlSystem},
 * firing is a discrete, one-shot event when the cooldown expires, not a
 * continuous force Box2D would clear between steps, so it doesn't need the
 * {@link PhysicsSystem#update(float, Runnable)} per-step treatment
 * {@link ShipControlSystem} needs.
 */
public class WeaponSystem extends IteratingSystem {

    /**
     * Attachment point name convention (design.md 2.4) for where projectiles
     * spawn — a ship can define more than one, e.g. an X-wing's four
     * cannons, and one projectile is fired per point each time the weapon is
     * off cooldown.
     */
    private static final String PROJECTILE_ATTACHMENT_NAME = "PROJECTILE";

    private static final Vector2 SPAWN_OFFSET = new Vector2();

    private final ComponentMapper<PhysicsBodyComponent> bodyMapper = ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<WeaponComponent> weaponMapper = ComponentMapper.getFor(WeaponComponent.class);
    private final ComponentMapper<NetworkInputComponent> inputMapper = ComponentMapper.getFor(NetworkInputComponent.class);
    private final ComponentMapper<PlayerIdComponent> playerIdMapper = ComponentMapper.getFor(PlayerIdComponent.class);
    private final ComponentMapper<ShipTypeComponent> shipTypeMapper = ComponentMapper.getFor(ShipTypeComponent.class);
    private final ComponentMapper<PowerDistributionComponent> powerMapper = ComponentMapper.getFor(PowerDistributionComponent.class);
    private final ComponentMapper<CombatTimerComponent> combatTimerMapper = ComponentMapper.getFor(CombatTimerComponent.class);

    private final Engine engine;
    private final World world;
    private final AtomicInteger nextProjectileId = new AtomicInteger();

    /**
     * Creates the weapon system.
     *
     * @param engine the Ashley engine to add fired projectiles to
     * @param world  the Box2D world to create fired projectiles' bodies in
     */
    public WeaponSystem(Engine engine, World world) {
        super(Family.all(PhysicsBodyComponent.class, WeaponComponent.class, NetworkInputComponent.class,
            PlayerIdComponent.class, ShipTypeComponent.class, PowerDistributionComponent.class,
            CombatTimerComponent.class).get());
        this.engine = engine;
        this.world = world;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        WeaponComponent weapon = weaponMapper.get(entity);
        weapon.tickCooldown(deltaTime);
        float weaponsMultiplier = powerMapper.get(entity).getDistribution().multiplierFor(PowerSystem.WEAPONS);
        weapon.rechargeCapacitor(deltaTime, weaponsMultiplier);

        NetworkInputComponent input = inputMapper.get(entity);
        if (!input.isFiring() || !weapon.canFire()) {
            return;
        }

        Body body = bodyMapper.get(entity).getBody();
        int ownerPlayerId = playerIdMapper.get(entity).getPlayerId();
        ShipStats shipStats = ShipStats.forType(shipTypeMapper.get(entity).getShipType());

        List<PixelPoint> spawnPoints = shipStats.getSpriteMetadata()
            .map(metadata -> metadata.getAttachmentPoints().get(PROJECTILE_ATTACHMENT_NAME))
            .orElse(null);

        if (spawnPoints == null || spawnPoints.isEmpty()) {
            fireFromDefaultOffset(body, ownerPlayerId, weapon, shipStats);
        } else {
            for (PixelPoint spawnPoint : spawnPoints) {
                fireFromAttachmentPoint(body, ownerPlayerId, weapon, spawnPoint, shipStats.getPixelsPerMeter());
            }
        }

        weapon.consumeShot();
        combatTimerMapper.get(entity).markFired();
    }

    // Spawn just ahead of the ship's own hull, not at its exact center - otherwise the
    // projectile starts out perfectly overlapping its shooter's own collision circle. Box2D
    // still generates a physical collision response for that overlap even though the hit is
    // ignored for damage (see GameNetworkServer's ownerId check) - and with zero separation
    // between two coincident circles, the push-apart direction is undefined and falls back to
    // an arbitrary fixed axis, which visibly redirected freshly-fired shots. A ContactFilter
    // in GameNetworkServer now also prevents this collision from being generated at all; this
    // offset is belt-and-suspenders (and just more correct - shots should originate from the
    // nose, not the center of mass).
    //
    // Used as a fallback for ships with no authored PROJECTILE attachment points yet (design.md
    // 2.4) - once a ship's metadata defines them, fireFromAttachmentPoint is used instead.
    private void fireFromDefaultOffset(Body body, int ownerPlayerId, WeaponComponent weapon, ShipStats shipStats) {
        float spawnDistance = shipStats.getRadiusMeters() + weapon.getStats().getProjectileRadiusMeters() + 0.1f;
        SPAWN_OFFSET.set(0, 1).rotateRad(body.getAngle()).scl(spawnDistance);

        ProjectileFactory.createProjectile(engine, world, nextProjectileId.getAndIncrement(), ownerPlayerId,
            body.getPosition().x + SPAWN_OFFSET.x, body.getPosition().y + SPAWN_OFFSET.y,
            body.getAngle(), weapon.getStats());
    }

    // The attachment point's sprite-local, center-origin, Y-up coordinates (see PixelPoint's
    // Javadoc) line up directly with the ship body's local frame - facing "up" (local +Y) is
    // exactly the direction WeaponSystem's own SPAWN_OFFSET fires along at angle 0 - so a spawn
    // point only needs converting from pixels to meters (at this ship type's own pixels-per-meter,
    // same reasoning as ShipFactory's hitbox polygon conversion - the point is authored in that
    // ship's own source-art pixel space, not a fixed global rate), then rotating by the ship's
    // current angle same as the default offset above.
    private void fireFromAttachmentPoint(Body body, int ownerPlayerId, WeaponComponent weapon, PixelPoint spawnPoint, float pixelsPerMeter) {
        SPAWN_OFFSET.set(spawnPoint.getX() / pixelsPerMeter, spawnPoint.getY() / pixelsPerMeter).rotateRad(body.getAngle());

        ProjectileFactory.createProjectile(engine, world, nextProjectileId.getAndIncrement(), ownerPlayerId,
            body.getPosition().x + SPAWN_OFFSET.x, body.getPosition().y + SPAWN_OFFSET.y,
            body.getAngle(), weapon.getStats());
    }
}
