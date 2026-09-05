package de.mkoehler.starwars.sim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.World;
import de.mkoehler.starwars.sim.components.NetworkInputComponent;
import de.mkoehler.starwars.sim.components.PhysicsBodyComponent;
import de.mkoehler.starwars.sim.components.PlayerControlledComponent;
import de.mkoehler.starwars.sim.components.PlayerIdComponent;

/**
 * Assembles server-side ship entities: a dynamic Box2D body plus the
 * components {@link de.mkoehler.starwars.sim.systems.ShipControlSystem} needs
 * to drive it from network-received input.
 * <p>
 * Server-only — has no rendering-related components, since the dedicated
 * server never draws anything (design.md 3.2).
 */
public final class ShipFactory {

    private ShipFactory() {
    }

    /**
     * Creates a player's ship entity and adds it to the given engine.
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
        Body body = createBody(world, x, y, stats.getRadiusMeters());

        Entity entity = new Entity();
        entity.add(new PlayerIdComponent(playerId));
        entity.add(new PhysicsBodyComponent(body));
        entity.add(new PlayerControlledComponent(stats.getThrustForce(), stats.getTurnTorque()));
        entity.add(new NetworkInputComponent());
        engine.addEntity(entity);
        return entity;
    }

    private static Body createBody(World world, float x, float y, float radiusMeters) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.DynamicBody;
        bodyDef.position.set(x, y);
        bodyDef.linearDamping = 0.6f;
        bodyDef.angularDamping = 2f;
        Body body = world.createBody(bodyDef);

        CircleShape shape = new CircleShape();
        shape.setRadius(radiusMeters);

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.density = 1f;
        fixtureDef.friction = 0f;
        fixtureDef.restitution = 0.2f;
        body.createFixture(fixtureDef);

        shape.dispose();
        return body;
    }
}
