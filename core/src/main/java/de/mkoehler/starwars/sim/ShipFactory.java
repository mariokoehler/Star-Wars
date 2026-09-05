package de.mkoehler.starwars.sim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.World;
import de.mkoehler.starwars.sim.components.PhysicsBodyComponent;
import de.mkoehler.starwars.sim.components.PlayerControlledComponent;
import de.mkoehler.starwars.sim.components.SpriteComponent;

/**
 * Assembles ship entities: a dynamic Box2D body plus the components needed to
 * drive and render it.
 * <p>
 * Only builds player-controlled, rendered ships for now (i.e. what a client
 * needs); a headless server building the same simulation would need a
 * variant that skips {@link SpriteComponent}, since it has no batch to draw
 * with — not yet split out, since there's only one caller so far.
 */
public final class ShipFactory {

    private ShipFactory() {
    }

    /**
     * Creates a player-controlled ship entity and adds it to the given engine.
     *
     * @param engine       the Ashley engine to add the entity to
     * @param world        the Box2D world to create the body in
     * @param x            spawn position, in meters
     * @param y            spawn position, in meters
     * @param region       the texture region to draw the ship as
     * @param widthMeters  the ship's draw/collision width, in meters
     * @param heightMeters the ship's draw/collision height, in meters
     * @param thrustForce  force, in newtons, applied while thrusting
     * @param turnTorque   torque, in newton-meters, applied while turning
     * @return the created entity
     */
    public static Entity createPlayerShip(Engine engine, World world, float x, float y,
                                           TextureRegion region, float widthMeters, float heightMeters,
                                           float thrustForce, float turnTorque) {
        Body body = createBody(world, x, y, Math.min(widthMeters, heightMeters) / 2f);

        Entity entity = new Entity();
        entity.add(new PhysicsBodyComponent(body));
        entity.add(new SpriteComponent(region, widthMeters, heightMeters));
        entity.add(new PlayerControlledComponent(thrustForce, turnTorque));
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
