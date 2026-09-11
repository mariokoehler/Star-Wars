package de.mkoehler.starwars.sim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import de.mkoehler.starwars.sim.components.MissileComponent;
import de.mkoehler.starwars.sim.components.PhysicsBodyComponent;
import de.mkoehler.starwars.sim.components.ProjectileComponent;
import de.mkoehler.starwars.sim.metadata.PixelPoint;

import java.util.List;

/**
 * Builds a fired missile's Box2D body and Ashley entity (server-only, same
 * as {@link ProjectileFactory}). Not built on top of {@link ProjectileFactory}
 * — a missile's body differs enough (its own authored hitbox polygon rather
 * than a plain circle, no muzzle-speed boost since it accelerates under its
 * own thrust instead, different damping) that sharing the method would need
 * more parameters threading around the differences than it would save.
 */
public final class MissileFactory {

    private MissileFactory() {
    }

    /**
     * Creates a missile entity at a shooter's current position/facing,
     * inheriting only the shooter's current velocity (no added muzzle speed
     * — {@link de.mkoehler.starwars.sim.systems.MissileGuidanceSystem}
     * accelerates it from there). Adds it to the given engine; the body's
     * {@code userData} is set to the entity, same as every other physics
     * body in this project, so the existing generic hit-resolution path
     * ({@code GameNetworkServer#resolvePendingHits}) picks it up with no
     * missile-specific code.
     *
     * @param engine        the Ashley engine to add the entity to
     * @param world         the Box2D world to create the body in
     * @param projectileId  this missile's id, unique among currently-alive projectiles
     * @param ownerPlayerId the id of the player who fired it
     * @param x             spawn position, in meters
     * @param y             spawn position, in meters
     * @param angle         initial facing, in radians (0 = facing north, this project's convention)
     * @param shooterVelX   the firing ship's own velocity at the moment of firing, in meters/second
     * @param shooterVelY   the firing ship's own velocity at the moment of firing, in meters/second
     * @param targetEntity  the entity this missile tracks ({@link MissileComponent#getTarget()})
     * @param targetPlayerId the target's player id (wire-broadcast as {@link ProjectileComponent#getTrackedTargetPlayerId()})
     * @return the created entity
     */
    public static Entity createMissile(Engine engine, World world, int projectileId, int ownerPlayerId,
                                        float x, float y, float angle, float shooterVelX, float shooterVelY,
                                        Entity targetEntity, int targetPlayerId) {
        MissileStats stats = MissileStats.INSTANCE;

        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.DynamicBody;
        bodyDef.position.set(x, y);
        bodyDef.angle = angle;
        bodyDef.bullet = true;
        bodyDef.gravityScale = 0f;
        bodyDef.linearDamping = 0f;
        bodyDef.angularDamping = 3f;
        Body body = world.createBody(bodyDef);

        PolygonShape shape = createHitboxShape(stats);

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.density = 1f;
        fixtureDef.friction = 0f;
        fixtureDef.restitution = 0f;
        fixtureDef.filter.categoryBits = CollisionCategories.PROJECTILE;
        // POWERUP added alongside SHIP/ASTEROID (design.md - power-ups), same reasoning as
        // ProjectileFactory's own mask - a missile hitting a power-up imparts a real (much larger,
        // given its mass) impulse before being destroyed. MINE added the same way (design.md -
        // mines): a missile touching a mine's sensor fixture detonates it, same as a blaster bolt.
        fixtureDef.filter.maskBits = (short) (CollisionCategories.SHIP | CollisionCategories.ASTEROID
            | CollisionCategories.POWERUP | CollisionCategories.MINE);
        body.createFixture(fixtureDef);
        shape.dispose();

        body.setLinearVelocity(shooterVelX, shooterVelY);

        Entity entity = new Entity();
        entity.add(new PhysicsBodyComponent(body));
        entity.add(new ProjectileComponent(projectileId, ownerPlayerId, stats.getDamage(),
            stats.getFlightSeconds(), targetPlayerId, false));
        entity.add(new MissileComponent(targetEntity));
        engine.addEntity(entity);
        body.setUserData(entity);
        return entity;
    }

    private static PolygonShape createHitboxShape(MissileStats stats) {
        List<PixelPoint> hitboxPolygon = stats.getSpriteMetadata().getHitboxPolygon();
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
}
