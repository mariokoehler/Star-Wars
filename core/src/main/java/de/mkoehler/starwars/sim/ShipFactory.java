package de.mkoehler.starwars.sim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.World;
import de.mkoehler.starwars.sim.components.HealthComponent;
import de.mkoehler.starwars.sim.components.NetworkInputComponent;
import de.mkoehler.starwars.sim.components.PhysicsBodyComponent;
import de.mkoehler.starwars.sim.components.PlayerControlledComponent;
import de.mkoehler.starwars.sim.components.PlayerIdComponent;
import de.mkoehler.starwars.sim.components.WeaponComponent;

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
        entity.add(new PhysicsBodyComponent(body));
        entity.add(new PlayerControlledComponent(stats.getThrustForce(), stats.getTurnTorque()));
        entity.add(new NetworkInputComponent());
        entity.add(new HealthComponent(stats.getMaxHealth()));
        entity.add(new WeaponComponent(WeaponStats.BLASTER));
        engine.addEntity(entity);
        body.setUserData(entity);
        return entity;
    }

    /**
     * Creates a ship's Box2D body, with no Ashley entity around it. The
     * fixture is set up to collide with both other ships and projectiles
     * (see {@link CollisionCategories}).
     *
     * @param world the Box2D world to create the body in
     * @param x     spawn position, in meters
     * @param y     spawn position, in meters
     * @param stats the ship type's tuning values (only the radius is used)
     * @return the created body
     */
    public static Body createBody(World world, float x, float y, ShipStats stats) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.DynamicBody;
        bodyDef.position.set(x, y);
        bodyDef.linearDamping = 0.6f;
        bodyDef.angularDamping = 2f;
        Body body = world.createBody(bodyDef);

        CircleShape shape = new CircleShape();
        shape.setRadius(stats.getRadiusMeters());

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
}
