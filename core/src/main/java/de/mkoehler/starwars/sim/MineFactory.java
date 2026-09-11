package de.mkoehler.starwars.sim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.World;
import de.mkoehler.starwars.sim.components.MineComponent;
import de.mkoehler.starwars.sim.components.PhysicsBodyComponent;

/**
 * Builds a mine's Box2D body and Ashley entity (server-only, same as
 * {@link PowerUpFactory}/{@link AsteroidFactory}) — spawned exclusively as
 * a result of a ship picking up the BOMB power-up (design.md — mines),
 * never on its own.
 * <p>
 * A {@link BodyDef.BodyType#StaticBody} — the simplest and strongest way
 * to satisfy the user's own spec ("mines spawn stationary - no movement,
 * no rotation"): a static body is immovable and non-rotating by Box2D's
 * own definition, with no need for {@link BodyDef#fixedRotation} (that
 * flag exists for a body that's otherwise free to rotate; a static one
 * already can't, regardless of it) or any zero-velocity bookkeeping to
 * maintain afterward. Box2D still generates contacts between a static
 * body and every dynamic body that overlaps it (ships/projectiles/
 * missiles/asteroids/power-ups here), so detonation-on-touch works
 * exactly the same as it would on a dynamic body.
 * <p>
 * The one fixture is a <b>sensor</b>, deliberately — a mine is destroyed
 * the instant anything touches it (design.md — mines), so there's no
 * point (and a real visual downside: a static, infinite-mass body would
 * otherwise impart a hard, one-physics-step "bounce" to whatever hit it,
 * a beat before the explosion) in giving it real collision response
 * first.
 */
public final class MineFactory {

    /** The mine sprite sheet's pixel density (design.md — mines: the user's own spec). */
    public static final float PIXELS_PER_METER = 32f;

    /**
     * Half the user's specified 64×64 pixel sprite at {@link #PIXELS_PER_METER}
     * (design.md — mines) — a 2m-diameter circle, "about half the size of
     * an X-wing" (4m diameter) per the user's own comparison.
     */
    public static final float RADIUS_METERS = 64f / 2f / PIXELS_PER_METER;

    private MineFactory() {
    }

    /**
     * Creates a mine entity at a fixed point, adds it to the given engine,
     * and sets the body's {@code userData} to the entity — same convention
     * as every other physics body in this project, so
     * {@code GameNetworkServer}'s existing {@code ContactListener} can look
     * it up on collision with no mine-specific body-identity tracking
     * needed.
     *
     * @param engine the Ashley engine to add the entity to
     * @param world  the Box2D world to create the body in
     * @param mineId this mine's id, unique among currently-active mines
     * @param x      spawn position, in meters
     * @param y      spawn position, in meters
     * @return the created entity
     */
    public static Entity createMine(Engine engine, World world, int mineId, float x, float y) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.StaticBody;
        bodyDef.position.set(x, y);
        Body body = world.createBody(bodyDef);

        CircleShape shape = new CircleShape();
        shape.setRadius(RADIUS_METERS);

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.isSensor = true;
        fixtureDef.filter.categoryBits = CollisionCategories.MINE;
        fixtureDef.filter.maskBits = (short) (CollisionCategories.SHIP | CollisionCategories.PROJECTILE
            | CollisionCategories.ASTEROID | CollisionCategories.POWERUP);
        body.createFixture(fixtureDef);
        shape.dispose();

        Entity entity = new Entity();
        entity.add(new PhysicsBodyComponent(body));
        entity.add(new MineComponent(mineId));
        engine.addEntity(entity);
        body.setUserData(entity);
        return entity;
    }
}
