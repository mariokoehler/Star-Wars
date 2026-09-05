package de.mkoehler.starwars.sim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.World;
import de.mkoehler.starwars.sim.ProjectileFactory;
import de.mkoehler.starwars.sim.ShipStats;
import de.mkoehler.starwars.sim.components.NetworkInputComponent;
import de.mkoehler.starwars.sim.components.PhysicsBodyComponent;
import de.mkoehler.starwars.sim.components.PlayerIdComponent;
import de.mkoehler.starwars.sim.components.WeaponComponent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fires a projectile for every ship whose fire input is held and whose
 * weapon is off cooldown, resetting that cooldown each time.
 * <p>
 * Runs server-side only, once per tick — unlike {@link ShipControlSystem},
 * firing is a discrete, one-shot event when the cooldown expires, not a
 * continuous force Box2D would clear between steps, so it doesn't need the
 * {@link PhysicsSystem#update(float, Runnable)} per-step treatment
 * {@link ShipControlSystem} needs.
 */
public class WeaponSystem extends IteratingSystem {

    private static final Vector2 SPAWN_OFFSET = new Vector2();

    private final ComponentMapper<PhysicsBodyComponent> bodyMapper = ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<WeaponComponent> weaponMapper = ComponentMapper.getFor(WeaponComponent.class);
    private final ComponentMapper<NetworkInputComponent> inputMapper = ComponentMapper.getFor(NetworkInputComponent.class);
    private final ComponentMapper<PlayerIdComponent> playerIdMapper = ComponentMapper.getFor(PlayerIdComponent.class);

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
        super(Family.all(PhysicsBodyComponent.class, WeaponComponent.class, NetworkInputComponent.class, PlayerIdComponent.class).get());
        this.engine = engine;
        this.world = world;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        WeaponComponent weapon = weaponMapper.get(entity);
        weapon.tickCooldown(deltaTime);

        NetworkInputComponent input = inputMapper.get(entity);
        if (!input.isFiring() || weapon.getCooldownRemaining() > 0f) {
            return;
        }

        Body body = bodyMapper.get(entity).getBody();
        int ownerPlayerId = playerIdMapper.get(entity).getPlayerId();

        // Spawn just ahead of the ship's own hull, not at its exact center - otherwise the
        // projectile starts out perfectly overlapping its shooter's own collision circle. Box2D
        // still generates a physical collision response for that overlap even though the hit is
        // ignored for damage (see GameNetworkServer's ownerId check) - and with zero separation
        // between two coincident circles, the push-apart direction is undefined and falls back to
        // an arbitrary fixed axis, which visibly redirected freshly-fired shots. A ContactFilter
        // in GameNetworkServer now also prevents this collision from being generated at all; this
        // offset is belt-and-suspenders (and just more correct - shots should originate from the
        // nose, not the center of mass).
        float spawnDistance = ShipStats.XWING.getRadiusMeters() + weapon.getStats().getProjectileRadiusMeters() + 0.1f;
        SPAWN_OFFSET.set(0, 1).rotateRad(body.getAngle()).scl(spawnDistance);

        ProjectileFactory.createProjectile(engine, world, nextProjectileId.getAndIncrement(), ownerPlayerId,
            body.getPosition().x + SPAWN_OFFSET.x, body.getPosition().y + SPAWN_OFFSET.y,
            body.getAngle(), weapon.getStats());

        weapon.resetCooldown();
    }
}
