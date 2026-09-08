package de.mkoehler.starwars.sim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import de.mkoehler.starwars.sim.MissileStats;
import de.mkoehler.starwars.sim.TurretAiming;
import de.mkoehler.starwars.sim.components.HullComponent;
import de.mkoehler.starwars.sim.components.MissileComponent;
import de.mkoehler.starwars.sim.components.PhysicsBodyComponent;
import de.mkoehler.starwars.sim.components.PlayerIdComponent;

/**
 * Steers every in-flight missile toward its {@link MissileComponent#getTarget()},
 * server-side only. Unlike most systems in this project, this one must run
 * inside {@code PhysicsSystem}'s per-physics-step callback (the same
 * "beforeEachStep" placement {@code ShipControlSystem} already needs), not
 * once per tick — Box2D clears a body's applied forces/torques after every
 * {@code world.step(...)}, so a continuously-applied thrust/steering torque
 * has to be reapplied every individual step, or it silently weakens on ticks
 * that run more than one step (design.md 3.5's "Important Box2D gotcha").
 * <p>
 * Steering is a simple bang-bang controller: aim at the target's <em>current</em>
 * position (plain pursuit, not a lead/intercept solve like
 * {@link TurretAiming#computeLeadAngle} — deliberately simplified, since the
 * missile's own limited turn torque already makes overshoot a real risk, and
 * a lead solve would only make a too-sharp turn even sharper). Applies full
 * turn torque in whichever direction reduces the angular difference to the
 * target's bearing, within a small deadzone to avoid endlessly re-applying
 * torque once already well-aligned. Forward thrust is unconditional — a
 * missile always burns its fuel, whether or not it currently has a live
 * target to steer toward.
 * <p>
 * If the target is no longer alive (checked against a live-ships snapshot
 * refreshed once per physics step, same pattern {@code TurretSystem} uses
 * per tick), the missile simply stops steering and flies straight on its
 * last heading until its own fuel/lifetime expires
 * ({@code ProjectileLifetimeSystem}) — no early self-destruct, no
 * retargeting (design.md — missiles).
 */
public class MissileGuidanceSystem extends IteratingSystem {

    private static final Family LIVE_SHIP_FAMILY =
        Family.all(PhysicsBodyComponent.class, PlayerIdComponent.class, HullComponent.class).get();

    /** Below this angular error, stop applying steering torque - avoids endless jitter once aligned. */
    private static final float STEERING_DEADZONE_RADIANS = (float) Math.toRadians(2.0);

    private static final Vector2 FORWARD = new Vector2();

    private final ComponentMapper<PhysicsBodyComponent> bodyMapper = ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<MissileComponent> missileMapper = ComponentMapper.getFor(MissileComponent.class);

    private final Engine engine;

    private ImmutableArray<Entity> liveShips;

    /**
     * Creates the missile guidance system.
     *
     * @param engine the Ashley engine to scan for live ships every physics step
     */
    public MissileGuidanceSystem(Engine engine) {
        super(Family.all(PhysicsBodyComponent.class, MissileComponent.class).get());
        this.engine = engine;
    }

    @Override
    public void update(float deltaTime) {
        liveShips = engine.getEntitiesFor(LIVE_SHIP_FAMILY);
        super.update(deltaTime);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Body body = bodyMapper.get(entity).getBody();
        Entity target = missileMapper.get(entity).getTarget();

        if (target != null && liveShips.contains(target, true)) {
            Body targetBody = bodyMapper.get(target).getBody();
            float dx = targetBody.getPosition().x - body.getPosition().x;
            float dy = targetBody.getPosition().y - body.getPosition().y;
            if (dx != 0f || dy != 0f) {
                float desiredBearing = MathUtils.atan2(-dx, dy);
                float diff = TurretAiming.angularDifference(desiredBearing, body.getAngle());
                if (diff > STEERING_DEADZONE_RADIANS) {
                    body.applyTorque(MissileStats.INSTANCE.getTurnTorque(), true);
                } else if (diff < -STEERING_DEADZONE_RADIANS) {
                    body.applyTorque(-MissileStats.INSTANCE.getTurnTorque(), true);
                }
            }
        }

        FORWARD.set(0, 1).rotateRad(body.getAngle()).scl(MissileStats.INSTANCE.getThrustForce());
        body.applyForceToCenter(FORWARD, true);
    }
}
