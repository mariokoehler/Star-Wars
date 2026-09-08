package de.mkoehler.starwars.sim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.Shape;
import com.badlogic.gdx.physics.box2d.World;
import de.mkoehler.starwars.sim.components.CombatTimerComponent;
import de.mkoehler.starwars.sim.components.HullComponent;
import de.mkoehler.starwars.sim.components.MissileLockComponent;
import de.mkoehler.starwars.sim.components.NetworkInputComponent;
import de.mkoehler.starwars.sim.components.PhysicsBodyComponent;
import de.mkoehler.starwars.sim.components.PlayerControlledComponent;
import de.mkoehler.starwars.sim.components.PlayerIdComponent;
import de.mkoehler.starwars.sim.components.PowerDistributionComponent;
import de.mkoehler.starwars.sim.components.RadarComponent;
import de.mkoehler.starwars.sim.components.ShieldComponent;
import de.mkoehler.starwars.sim.components.ShipTypeComponent;
import de.mkoehler.starwars.sim.components.TurretComponent;
import de.mkoehler.starwars.sim.components.WeaponComponent;
import de.mkoehler.starwars.sim.metadata.PixelPoint;
import de.mkoehler.starwars.sim.metadata.TurretConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds ship Box2D bodies, and (server-side) full Ashley entities wrapping
 * one.
 * <p>
 * {@link #createBody} is also used directly by the client for its local
 * prediction body (design.md 3.5) — the client doesn't use Ashley at all, it
 * just needs a body built with the exact same shape/damping the server uses,
 * so predicted and authoritative physics never diverge for reasons other
 * than differing input.
 */
public final class ShipFactory {

    private ShipFactory() {
    }

    /**
     * Creates a player's ship entity (server-side) and adds it to the given
     * engine. The body's {@code userData} is set to the entity, so a Box2D
     * {@code ContactListener} can look up which entity a colliding body
     * belongs to (used for projectile hit detection).
     *
     * @param engine   the Ashley engine to add the entity to
     * @param world    the Box2D world to create the body in
     * @param playerId the id of the player this ship belongs to
     * @param x        spawn position, in meters
     * @param y        spawn position, in meters
     * @param stats    the ship type's tuning values
     * @return the created entity
     */
    public static Entity createShip(Engine engine, World world, int playerId, float x, float y, ShipStats stats) {
        Body body = createBody(world, x, y, stats);

        Entity entity = new Entity();
        entity.add(new PlayerIdComponent(playerId));
        entity.add(new ShipTypeComponent(stats.getType()));
        entity.add(new PhysicsBodyComponent(body));
        entity.add(new PlayerControlledComponent(stats.getThrustForce(), stats.getTurnTorque(),
            stats.getEngineTurnResponseExponent()));
        entity.add(new NetworkInputComponent());
        entity.add(new HullComponent(stats.getMaxHealth()));
        entity.add(new ShieldComponent(stats.getShieldMaxCapacity(), stats.getShieldRechargePerSecond()));
        entity.add(new WeaponComponent(WeaponStats.BLASTER));
        entity.add(new PowerDistributionComponent());
        entity.add(new CombatTimerComponent());
        entity.add(new RadarComponent());
        createTurretComponent(stats).ifPresent(entity::add);
        if (stats.isMissileEnabled()) {
            entity.add(new MissileLockComponent(stats.getMissileStartingCount()));
        }
        engine.addEntity(entity);
        body.setUserData(entity);
        return entity;
    }

    /**
     * Builds this ship type's {@link TurretComponent}, one {@link
     * TurretComponent.TurretMount} per authored {@code "TURRET"} attachment
     * point (design.md — turret weapons) — empty if the ship type has no
     * such points, or no {@link TurretConfig} to tune them with (both must
     * be present; currently only the Falcon and Star Destroyer have either).
     *
     * @param stats the ship type's tuning values
     * @return the turret component to add, or empty for a ship with no turrets
     */
    private static Optional<TurretComponent> createTurretComponent(ShipStats stats) {
        return stats.getSpriteMetadata().flatMap(metadata -> {
            List<PixelPoint> turretPoints = metadata.getAttachmentPoints().get(TurretConfig.ATTACHMENT_NAME);
            TurretConfig config = metadata.getTurretConfig();
            if (turretPoints == null || turretPoints.isEmpty() || config == null) {
                return Optional.empty();
            }
            float pixelsPerMeter = stats.getPixelsPerMeter();
            List<TurretComponent.TurretMount> mounts = new ArrayList<>();
            for (PixelPoint point : turretPoints) {
                Vector2 localOffsetMeters = new Vector2(point.getX() / pixelsPerMeter, point.getY() / pixelsPerMeter);
                mounts.add(new TurretComponent.TurretMount(localOffsetMeters));
            }
            return Optional.of(new TurretComponent(mounts, config));
        });
    }

    /**
     * Creates a ship's Box2D body, with no Ashley entity around it. The
     * fixture is set up to collide with both other ships and projectiles
     * (see {@link CollisionCategories}).
     * <p>
     * Uses a convex polygon built from {@link ShipStats#getSpriteMetadata()}'s
     * hitbox points (design.md 2.4) when at least 3 have been authored via
     * the {@code dev-tools} sprite metadata editor, for a closer-fitting
     * collision shape than a circle — most useful for ship-vs-ship
     * collisions. Falls back to the original {@link CircleShape} (using
     * {@link ShipStats#getRadiusMeters()}) when no metadata/hitbox has been
     * authored yet, so ships without a {@code .meta.json} keep working
     * exactly as before.
     *
     * @param world the Box2D world to create the body in
     * @param x     spawn position, in meters
     * @param y     spawn position, in meters
     * @param stats the ship type's tuning values
     * @return the created body
     */
    public static Body createBody(World world, float x, float y, ShipStats stats) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.DynamicBody;
        bodyDef.position.set(x, y);
        bodyDef.linearDamping = 0.6f;
        bodyDef.angularDamping = 2f;
        Body body = world.createBody(bodyDef);

        Shape shape = createHitboxShape(stats);

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.density = 1f;
        fixtureDef.friction = 0f;
        fixtureDef.restitution = 0.2f;
        fixtureDef.filter.categoryBits = CollisionCategories.SHIP;
        fixtureDef.filter.maskBits = (short) (CollisionCategories.SHIP | CollisionCategories.PROJECTILE);
        body.createFixture(fixtureDef);

        shape.dispose();
        return body;
    }

    private static Shape createHitboxShape(ShipStats stats) {
        List<PixelPoint> hitboxPolygon = stats.getSpriteMetadata()
            .map(metadata -> metadata.getHitboxPolygon())
            .orElse(List.of());

        if (hitboxPolygon.size() >= 3) {
            // PolygonShape#set computes the convex hull of the given points itself, and requires
            // at most 8 vertices (a Box2D limit) - the editor already enforces that cap when
            // authoring points, see SpriteCanvas.MAX_HITBOX_POINTS.
            // Each ship type's own pixels-per-meter (ShipTypeConfig#getPixelsPerMeter()), not the
            // global PhysicsConstants.PIXELS_PER_METER - the polygon's pixel coordinates are in
            // that ship's own source-art pixel space, which doesn't necessarily match the global
            // conversion rate (e.g. a ship imported at 2x resolution purely for crisper art still
            // needs to convert to the same real-world size as one imported at 1x).
            float pixelsPerMeter = stats.getPixelsPerMeter();
            Vector2[] vertices = new Vector2[hitboxPolygon.size()];
            for (int i = 0; i < hitboxPolygon.size(); i++) {
                PixelPoint point = hitboxPolygon.get(i);
                vertices[i] = new Vector2(point.getX() / pixelsPerMeter, point.getY() / pixelsPerMeter);
            }
            PolygonShape polygon = new PolygonShape();
            polygon.set(vertices);
            return polygon;
        }

        CircleShape circle = new CircleShape();
        circle.setRadius(stats.getRadiusMeters());
        return circle;
    }
}
