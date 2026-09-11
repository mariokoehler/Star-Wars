package de.mkoehler.starwars.sim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.World;
import de.mkoehler.starwars.sim.components.PhysicsBodyComponent;
import de.mkoehler.starwars.sim.components.PowerUpComponent;

/**
 * Builds a power-up's Box2D body and Ashley entity (server-only, same as
 * {@link AsteroidFactory}/{@link ProjectileFactory}) — a world pickup a ship
 * collects on contact (design.md — power-ups).
 * <p>
 * Each power-up body carries <b>two</b> fixtures, deliberately: a physical
 * one (real mass, bounces off the arena boundary/an asteroid/a projectile or
 * missile) and a non-physical sensor one used only to detect a ship touching
 * it. The physical fixture's mask never includes {@link CollisionCategories#SHIP}
 * — a body this light would otherwise get flung around by simple contact,
 * making "touch to pick up" feel twitchy (design.md's own open question,
 * resolved this way) — while the sensor fixture's mask is exactly
 * {@code SHIP}, so Box2D still fires a {@code beginContact} the instant a
 * ship overlaps it (for {@code GameNetworkServer} to act on), but never
 * applies any collision response for that pair, regardless of either
 * fixture's restitution/friction.
 */
public final class PowerUpFactory {

    /** Half of the user's specified 118×118 pixel hitbox, converted at {@link PowerUpType#PIXELS_PER_METER}. */
    public static final float HITBOX_RADIUS_METERS = 59f / PowerUpType.PIXELS_PER_METER;

    /**
     * Deliberately far lighter than even an asteroid's already-light density
     * (design.md — power-ups: "very light, so projectiles will be able to
     * impart some meaningful momentum") — a blaster bolt already nudges a
     * power-up by roughly a meter/second of Δv at this mass, a missile by
     * far more (see {@code GameNetworkServer#clampPowerUpSpeed}, the
     * companion safety valve for that latter case). Untuned starting point.
     */
    private static final float DENSITY = 0.05f;

    /** Zero, same "clean reflection, no induced spin" reasoning as {@link ArenaBounds#createBoundary}. */
    private static final float FRICTION = 0f;

    /**
     * High — the user's own spec ("make them real bouncy so they don't lose
     * a lot of energy when they bounce off of things"). Box2D mixes two
     * fixtures' restitution via {@code max(...)} by default, so this one
     * value alone determines how bouncy every power-up collision looks,
     * independent of the other body's own restitution (same reasoning
     * {@link ArenaBounds#createBoundary}'s own Javadoc documents for
     * {@code WALL_RESTITUTION}). Untuned starting point.
     */
    private static final float RESTITUTION = 0.85f;

    private PowerUpFactory() {
    }

    /**
     * Creates a power-up entity with the given initial motion, adds it to
     * the given engine, and sets the body's {@code userData} to the entity —
     * same convention as every other physics body in this project, so
     * {@code GameNetworkServer}'s existing {@code ContactListener} can look
     * it up on collision with no power-up-specific body-identity tracking
     * needed.
     *
     * @param engine          the Ashley engine to add the entity to
     * @param world           the Box2D world to create the body in
     * @param powerUpId       this power-up's id, unique among currently-active power-ups
     * @param type            this power-up's effect type
     * @param x               spawn position, in meters
     * @param y               spawn position, in meters
     * @param angle           initial rotation, in radians
     * @param angularVelocity initial rotation speed, in radians/second (the user's own spec:
     *                        stationary but with a slight rotation — linear velocity starts at zero)
     * @return the created entity
     */
    public static Entity createPowerUp(Engine engine, World world, int powerUpId, PowerUpType type,
                                        float x, float y, float angle, float angularVelocity) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.DynamicBody;
        bodyDef.position.set(x, y);
        bodyDef.angle = angle;
        bodyDef.gravityScale = 0f;
        // No damping, same as an asteroid (design.md — power-ups): once set in motion by a shot,
        // a power-up keeps drifting rather than coasting to a stop.
        bodyDef.linearDamping = 0f;
        bodyDef.angularDamping = 0f;
        Body body = world.createBody(bodyDef);

        CircleShape shape = new CircleShape();
        shape.setRadius(HITBOX_RADIUS_METERS);

        FixtureDef physical = new FixtureDef();
        physical.shape = shape;
        physical.density = DENSITY;
        physical.friction = FRICTION;
        physical.restitution = RESTITUTION;
        physical.filter.categoryBits = CollisionCategories.POWERUP;
        physical.filter.maskBits = (short) (CollisionCategories.ARENA_BOUNDARY | CollisionCategories.ASTEROID
            | CollisionCategories.PROJECTILE);
        body.createFixture(physical);

        FixtureDef sensor = new FixtureDef();
        sensor.shape = shape;
        sensor.isSensor = true;
        // Zero density - this fixture must contribute nothing to the body's mass, which the
        // physical fixture above already establishes on its own.
        sensor.density = 0f;
        sensor.filter.categoryBits = CollisionCategories.POWERUP;
        sensor.filter.maskBits = CollisionCategories.SHIP;
        body.createFixture(sensor);

        shape.dispose();

        body.setAngularVelocity(angularVelocity);

        Entity entity = new Entity();
        entity.add(new PhysicsBodyComponent(body));
        entity.add(new PowerUpComponent(powerUpId, type));
        engine.addEntity(entity);
        body.setUserData(entity);
        return entity;
    }
}
