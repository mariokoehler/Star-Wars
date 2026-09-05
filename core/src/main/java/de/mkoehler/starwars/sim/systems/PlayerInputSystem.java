package de.mkoehler.starwars.sim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import de.mkoehler.starwars.sim.components.PhysicsBodyComponent;
import de.mkoehler.starwars.sim.components.PlayerControlledComponent;

/**
 * Reads keyboard input directly and applies thrust/turn forces to every
 * entity with both a {@link PhysicsBodyComponent} and a
 * {@link PlayerControlledComponent}.
 * <p>
 * Uses the v1 default control scheme from design.md 5.3 (W/S thrust,
 * A/D turn); not yet driven by the remappable keybinds config, which doesn't
 * exist yet (see design.md 3.8).
 */
public class PlayerInputSystem extends IteratingSystem {

    private static final Vector2 FORWARD = new Vector2();

    private final ComponentMapper<PhysicsBodyComponent> bodyMapper = ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<PlayerControlledComponent> controlMapper = ComponentMapper.getFor(PlayerControlledComponent.class);

    /**
     * Creates the input system.
     */
    public PlayerInputSystem() {
        super(Family.all(PhysicsBodyComponent.class, PlayerControlledComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Body body = bodyMapper.get(entity).getBody();
        PlayerControlledComponent control = controlMapper.get(entity);

        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            body.applyTorque(control.getTurnTorque(), true);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            body.applyTorque(-control.getTurnTorque(), true);
        }

        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            applyThrust(body, control.getThrustForce());
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            applyThrust(body, -control.getThrustForce());
        }
    }

    private static void applyThrust(Body body, float force) {
        FORWARD.set(0, 1).rotateRad(body.getAngle()).scl(force);
        body.applyForceToCenter(FORWARD, true);
    }
}
