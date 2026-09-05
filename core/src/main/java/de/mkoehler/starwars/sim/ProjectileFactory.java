package de.mkoehler.starwars.sim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.World;
import de.mkoehler.starwars.sim.components.PhysicsBodyComponent;
import de.mkoehler.starwars.sim.components.ProjectileComponent;

/**
 * Builds a fired projectile's Box2D body and Ashley entity (server-only —
 * the dedicated server is the sole simulator of projectiles, same as ships,
 * design.md 3.5).
 */
public final class ProjectileFactory {

    private static final Vector2 DIRECTION = new Vector2();

    private ProjectileFactory() {
    }

    /**
     * Creates a projectile entity travelling in the direction of {@code angle}
     * at the weapon's configured speed, and adds it to the given engine. The
     * body's {@code userData} is set to the entity, so a Box2D
     * {@code ContactListener} can look it up on collision.
     *
     * @param engine        the Ashley engine to add the entity to
     * @param world         the Box2D world to create the body in
     * @param projectileId  this projectile's id, unique among currently-alive projectiles
     * @param ownerPlayerId the id of the player who fired it
     * @param x             spawn position, in meters
     * @param y             spawn position, in meters
     * @param angle         travel direction, in radians (0 = facing/travelling north, matching
     *                      {@link de.mkoehler.starwars.sim.systems.ShipControlSystem}'s convention)
     * @param stats         the weapon type's tuning values
     * @return the created entity
     */
    public static Entity createProjectile(Engine engine, World world, int projectileId, int ownerPlayerId,
                                           float x, float y, float angle, WeaponStats stats) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.DynamicBody;
        bodyDef.position.set(x, y);
        bodyDef.angle = angle;
        bodyDef.bullet = true; // small and fast - needs continuous collision detection to not tunnel through ships
        bodyDef.gravityScale = 0f;
        Body body = world.createBody(bodyDef);

        CircleShape shape = new CircleShape();
        shape.setRadius(stats.getProjectileRadiusMeters());

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.density = 0.01f;
        fixtureDef.friction = 0f;
        fixtureDef.restitution = 0f;
        fixtureDef.filter.categoryBits = CollisionCategories.PROJECTILE;
        fixtureDef.filter.maskBits = CollisionCategories.SHIP;
        body.createFixture(fixtureDef);
        shape.dispose();

        DIRECTION.set(0, 1).rotateRad(angle).scl(stats.getProjectileSpeed());
        body.setLinearVelocity(DIRECTION);

        Entity entity = new Entity();
        entity.add(new PhysicsBodyComponent(body));
        entity.add(new ProjectileComponent(projectileId, ownerPlayerId, stats.getDamage(), stats.getProjectileLifetimeSeconds()));
        engine.addEntity(entity);
        body.setUserData(entity);
        return entity;
    }
}
