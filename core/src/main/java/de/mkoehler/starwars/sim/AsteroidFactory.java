package de.mkoehler.starwars.sim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import de.mkoehler.starwars.sim.components.AsteroidComponent;
import de.mkoehler.starwars.sim.components.PhysicsBodyComponent;
import de.mkoehler.starwars.sim.metadata.PixelPoint;

import java.util.List;

/**
 * Builds an asteroid's Box2D body and Ashley entity (server-only, same as
 * {@link ProjectileFactory}/{@link MissileFactory}) — a large, dense,
 * indestructible obstacle ships can collide with (design.md — asteroids).
 * <p>
 * Deliberately has no linear/angular damping at all — the user's own spec:
 * once spawned, an asteroid keeps its initial speed and spin forever,
 * unlike a ship (which is actively piloted and damped for controllability).
 * Its fixture is masked to collide with ships and projectiles only, never
 * the arena boundary or other asteroids ({@link CollisionCategories}) — it
 * simply drifts until it leaves the arena, then despawns and respawns
 * elsewhere ({@code GameNetworkServer#tickAsteroids}).
 */
public final class AsteroidFactory {

    /**
     * Deliberately much lower than a ship's own {@code density=1f} might
     * suggest is "safe" to just multiply up from — an asteroid's hitbox
     * polygon is far larger in area than a ship's (up to ~20m across vs. a
     * ship's ~4m), so even this modest density already gives an asteroid
     * many times a ship's mass (design.md — asteroids: "react to ship
     * impacts but not be easily bounced around", not "immovable"). Untuned
     * starting point, flagged for the user's own feel-testing like every
     * other physics tuning number in this project.
     */
    private static final float DENSITY = 3f;

    /** Near-zero, same "a clean reflection of velocity, not chaotic spin" reasoning as {@code ArenaBounds}. */
    private static final float FRICTION = 0f;

    /**
     * Low but nonzero — a ship visibly bounces off an asteroid on impact
     * (the user's own "react to ship impacts" spec) without it feeling like
     * an elastic wall bounce (see {@code ArenaBounds#WALL_RESTITUTION},
     * much higher). Untuned starting point.
     */
    private static final float RESTITUTION = 0.1f;

    private AsteroidFactory() {
    }

    /**
     * Creates an asteroid entity with the given initial motion, adds it to
     * the given engine, and sets the body's {@code userData} to the entity
     * — same convention as every other physics body in this project, so
     * {@code GameNetworkServer}'s existing {@code ContactListener} can look
     * it up on collision with no asteroid-specific body-identity tracking
     * needed.
     *
     * @param engine          the Ashley engine to add the entity to
     * @param world           the Box2D world to create the body in
     * @param asteroidId      this asteroid's id, unique among currently-alive asteroids
     * @param type            this asteroid's texture/hitbox type
     * @param x               spawn position, in meters
     * @param y               spawn position, in meters
     * @param angle           initial rotation, in radians
     * @param velocityX       initial velocity, in meters/second
     * @param velocityY       initial velocity, in meters/second
     * @param angularVelocity initial rotation speed, in radians/second
     * @return the created entity
     */
    public static Entity createAsteroid(Engine engine, World world, int asteroidId, AsteroidType type,
                                         float x, float y, float angle,
                                         float velocityX, float velocityY, float angularVelocity) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.DynamicBody;
        bodyDef.position.set(x, y);
        bodyDef.angle = angle;
        bodyDef.gravityScale = 0f;
        bodyDef.linearDamping = 0f;
        bodyDef.angularDamping = 0f;
        Body body = world.createBody(bodyDef);

        PolygonShape shape = createHitboxShape(AsteroidStats.forType(type));

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.density = DENSITY;
        fixtureDef.friction = FRICTION;
        fixtureDef.restitution = RESTITUTION;
        fixtureDef.filter.categoryBits = CollisionCategories.ASTEROID;
        // POWERUP added alongside SHIP/PROJECTILE (design.md - power-ups): an asteroid physically
        // bounces a power-up drifting into it, same as any other dynamic obstacle here.
        fixtureDef.filter.maskBits = (short) (CollisionCategories.SHIP | CollisionCategories.PROJECTILE
            | CollisionCategories.POWERUP);
        body.createFixture(fixtureDef);
        shape.dispose();

        body.setLinearVelocity(velocityX, velocityY);
        body.setAngularVelocity(angularVelocity);

        Entity entity = new Entity();
        entity.add(new PhysicsBodyComponent(body));
        entity.add(new AsteroidComponent(asteroidId, type));
        engine.addEntity(entity);
        body.setUserData(entity);
        return entity;
    }

    /**
     * Creates a client-side-only "puppet" body mirroring one server-
     * authoritative asteroid, for the client's own local-prediction
     * {@code World} (design.md — asteroids' addendum) — same reasoning
     * {@link ShipFactory#createBody}'s own Javadoc already documents for
     * why the client needs a real Box2D body here at all: without one, the
     * client's locally-predicted ship body would fly straight through an
     * asteroid the server is actually bouncing it off, and every such
     * contact would manufacture a large, growing reconciliation error the
     * instant the next snapshot arrived — the same "predicted and
     * authoritative physics must not diverge for reasons other than input"
     * rule the arena boundary was already built to satisfy.
     * <p>
     * A {@link BodyDef.BodyType#KinematicBody}, not a full dynamic mirror —
     * the client has no reason to simulate <i>how</i> an asteroid moves
     * (it always knows the true answer, from the next {@code AsteroidState}),
     * and a kinematic body can't be pushed around by the client's own
     * predicted ship, which is a reasonable approximation of the server's
     * real physics given an asteroid outweighs a ship by roughly 30-60×
     * ({@link #DENSITY}'s own Javadoc). The caller is expected to call
     * {@link Body#setTransform} / {@link Body#setLinearVelocity} /
     * {@link Body#setAngularVelocity} on the returned body every time a
     * fresh {@code AsteroidState} arrives, so it never drifts from the
     * server's own truth.
     *
     * @param world the client's local-prediction Box2D world to create the body in
     * @param type  this asteroid's texture/hitbox type
     * @param x     initial position, in meters
     * @param y     initial position, in meters
     * @param angle initial rotation, in radians
     * @return the created body
     */
    public static Body createLocalMirrorBody(World world, AsteroidType type, float x, float y, float angle) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.KinematicBody;
        bodyDef.position.set(x, y);
        bodyDef.angle = angle;
        Body body = world.createBody(bodyDef);

        PolygonShape shape = createHitboxShape(AsteroidStats.forType(type));
        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.filter.categoryBits = CollisionCategories.ASTEROID;
        // Only SHIP, not PROJECTILE - the client's local-prediction world never contains
        // projectile bodies at all (shots are only ever rendered, never physically simulated
        // client-side), so masking PROJECTILE in here would be dead weight.
        fixtureDef.filter.maskBits = CollisionCategories.SHIP;
        body.createFixture(fixtureDef);
        shape.dispose();
        return body;
    }

    private static PolygonShape createHitboxShape(AsteroidStats stats) {
        List<PixelPoint> hitboxPolygon = stats.getSpriteMetadata().getHitboxPolygon();
        float pixelsPerMeter = stats.getType().getPixelsPerMeter();
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
