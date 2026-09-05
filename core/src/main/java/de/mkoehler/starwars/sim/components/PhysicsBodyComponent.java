package de.mkoehler.starwars.sim.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.physics.box2d.Body;

/**
 * Associates an entity with the Box2D {@link Body} that simulates its
 * position, velocity and rotation.
 */
public class PhysicsBodyComponent implements Component {

    private final Body body;

    /**
     * Creates a component wrapping the given body.
     *
     * @param body the entity's physics body
     */
    public PhysicsBodyComponent(Body body) {
        this.body = body;
    }

    /**
     * Returns the Box2D body backing this entity.
     *
     * @return the entity's physics body
     */
    public Body getBody() {
        return body;
    }
}
