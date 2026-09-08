package de.mkoehler.starwars.sim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import de.mkoehler.starwars.sim.PowerSystem;
import de.mkoehler.starwars.sim.TurnResponseCurve;
import de.mkoehler.starwars.sim.components.NetworkInputComponent;
import de.mkoehler.starwars.sim.components.PhysicsBodyComponent;
import de.mkoehler.starwars.sim.components.PlayerControlledComponent;
import de.mkoehler.starwars.sim.components.PowerDistributionComponent;

/**
 * Applies thrust/turn forces to every entity with a {@link PhysicsBodyComponent},
 * a {@link PlayerControlledComponent}, a {@link NetworkInputComponent} and a
 * {@link PowerDistributionComponent}, based on that entity's currently held
 * input state and its current {@link PowerSystem#ENGINES} power allocation
 * (design.md 2.2 — more Engines power means more thrust/torque).
 * <p>
 * Runs server-side, driven by input received over the network
 * ({@code PlayerInputMessage}) rather than local {@code Gdx.input} — this
 * class has no libGDX-input/graphics dependency, only Box2D/Ashley, so it
 * works unchanged in the headless server process. {@link #applyInput} is
 * exposed statically so the client can call the exact same force/torque math
 * directly on its local prediction body (design.md 3.5) — predicted and
 * authoritative physics must apply identical rules, or the client would
 * constantly need correcting for reasons other than differing input. The
 * caller (here and the client) is responsible for pre-multiplying
 * thrust/torque by the Engines power multiplier before calling it, since a
 * plain static method has no entity/component to read that from itself.
 */
public class ShipControlSystem extends IteratingSystem {

    private static final Vector2 FORWARD = new Vector2();

    private final ComponentMapper<PhysicsBodyComponent> bodyMapper = ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<PlayerControlledComponent> controlMapper = ComponentMapper.getFor(PlayerControlledComponent.class);
    private final ComponentMapper<NetworkInputComponent> inputMapper = ComponentMapper.getFor(NetworkInputComponent.class);
    private final ComponentMapper<PowerDistributionComponent> powerMapper = ComponentMapper.getFor(PowerDistributionComponent.class);

    /**
     * Creates the ship control system.
     */
    public ShipControlSystem() {
        super(Family.all(PhysicsBodyComponent.class, PlayerControlledComponent.class,
            NetworkInputComponent.class, PowerDistributionComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Body body = bodyMapper.get(entity).getBody();
        PlayerControlledComponent control = controlMapper.get(entity);
        NetworkInputComponent input = inputMapper.get(entity);
        float enginesMultiplier = powerMapper.get(entity).getDistribution().multiplierFor(PowerSystem.ENGINES);
        // Thrust stays on the plain linear multiplier; only torque goes through the per-ship-type
        // response curve (design.md 2.2's addendum) - see TurnResponseCurve's own Javadoc for why.
        float turnMultiplier = TurnResponseCurve.apply(enginesMultiplier, control.getEngineTurnResponseExponent());

        applyInput(body, control.getThrustForce() * enginesMultiplier, control.getTurnTorque() * turnMultiplier,
            input.isThrustForward(), input.isThrustReverse(), input.isTurnLeft(), input.isTurnRight());
    }

    /**
     * Applies one frame's worth of thrust/turn forces to a body, given a
     * held input state. The single place this project's ship control math
     * lives, called both from {@link #processEntity} (server, via
     * {@link NetworkInputComponent}) and directly by the client (its own
     * locally-held {@code Gdx.input} state, for prediction).
     *
     * @param body          the body to apply forces to
     * @param thrustForce   force, in newtons, applied while thrusting
     * @param turnTorque    torque, in newton-meters, applied while turning
     * @param thrustForward whether the forward-thrust input is held
     * @param thrustReverse whether the reverse-thrust input is held
     * @param turnLeft      whether the turn-left input is held
     * @param turnRight     whether the turn-right input is held
     */
    public static void applyInput(Body body, float thrustForce, float turnTorque,
                                   boolean thrustForward, boolean thrustReverse,
                                   boolean turnLeft, boolean turnRight) {
        if (turnLeft) {
            body.applyTorque(turnTorque, true);
        }
        if (turnRight) {
            body.applyTorque(-turnTorque, true);
        }

        if (thrustForward) {
            applyThrust(body, thrustForce);
        }
        if (thrustReverse) {
            applyThrust(body, -thrustForce);
        }
    }

    private static void applyThrust(Body body, float force) {
        FORWARD.set(0, 1).rotateRad(body.getAngle()).scl(force);
        body.applyForceToCenter(FORWARD, true);
    }
}
