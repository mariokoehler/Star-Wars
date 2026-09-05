package de.mkoehler.starwars.sim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import de.mkoehler.starwars.sim.components.NetworkInputComponent;
import de.mkoehler.starwars.sim.components.PhysicsBodyComponent;
import de.mkoehler.starwars.sim.components.PlayerControlledComponent;

/**
 * Applies thrust/turn forces to every entity with a {@link PhysicsBodyComponent},
 * a {@link PlayerControlledComponent} and a {@link NetworkInputComponent},
 * based on that entity's currently held input state.
 * <p>
 * Runs server-side only: the server is the sole simulator of ship physics
 * (design.md 3.5), driven by input received over the network
 * ({@code PlayerInputMessage}) rather than local {@code Gdx.input} — this
 * class has no libGDX-input/graphics dependency, only Box2D/Ashley, so it
 * works unchanged in the headless server process.
 */
public class ShipControlSystem extends IteratingSystem {

    private static final Vector2 FORWARD = new Vector2();

    private final ComponentMapper<PhysicsBodyComponent> bodyMapper = ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<PlayerControlledComponent> controlMapper = ComponentMapper.getFor(PlayerControlledComponent.class);
    private final ComponentMapper<NetworkInputComponent> inputMapper = ComponentMapper.getFor(NetworkInputComponent.class);

    /**
     * Creates the ship control system.
     */
    public ShipControlSystem() {
        super(Family.all(PhysicsBodyComponent.class, PlayerControlledComponent.class, NetworkInputComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Body body = bodyMapper.get(entity).getBody();
        PlayerControlledComponent control = controlMapper.get(entity);
        NetworkInputComponent input = inputMapper.get(entity);

        if (input.isTurnLeft()) {
            body.applyTorque(control.getTurnTorque(), true);
        }
        if (input.isTurnRight()) {
            body.applyTorque(-control.getTurnTorque(), true);
        }

        if (input.isThrustForward()) {
            applyThrust(body, control.getThrustForce());
        }
        if (input.isThrustReverse()) {
            applyThrust(body, -control.getThrustForce());
        }
    }

    private static void applyThrust(Body body, float force) {
        FORWARD.set(0, 1).rotateRad(body.getAngle()).scl(force);
        body.applyForceToCenter(FORWARD, true);
    }
}
