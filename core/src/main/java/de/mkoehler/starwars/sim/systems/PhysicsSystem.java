package de.mkoehler.starwars.sim.systems;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.physics.box2d.World;
import de.mkoehler.starwars.sim.PhysicsConstants;

/**
 * Steps a Box2D {@link World} at a fixed timestep, accumulating leftover
 * frame time between calls so the simulation rate is independent of the
 * caller's update rate.
 * <p>
 * Used server-side to step the authoritative world (design.md 3.5), and
 * client-side to step the local prediction world for the player's own ship
 * (design.md 3.5's client-side prediction milestone) — in both cases driven
 * directly via {@link #update(float)}, never through an Ashley
 * {@code Engine}, so it works standalone with no entity list involved.
 */
public class PhysicsSystem extends EntitySystem {

    private final World world;
    private float accumulator;

    /**
     * Creates a physics system stepping the given world.
     *
     * @param world the Box2D world to step
     */
    public PhysicsSystem(World world) {
        this.world = world;
    }

    @Override
    public void update(float deltaTime) {
        update(deltaTime, null);
    }

    /**
     * Steps the world exactly as {@link #update(float)} does, but invokes
     * {@code beforeEachStep} immediately before every individual
     * {@code world.step(...)} call, not just once per call to this method.
     * <p>
     * This matters whenever the caller's update rate is slower than the
     * fixed timestep (e.g. a 30Hz server tick stepping a 60Hz physics rate —
     * design.md 3.5) and needs to apply continuously-held input (thrust/turn
     * forces): Box2D clears a body's applied forces/torques after every
     * {@code step()} call, so force applied once per {@code update()} call
     * only survives into the *first* of however many steps actually run —
     * any further steps in that same call would see no force at all,
     * silently halving (or worse) the effective thrust/turn rate. Found by
     * play-testing a "much weaker than the single-player prototype, and
     * jittery" feeling that turned out to be exactly this.
     *
     * @param deltaTime      time since the last call, in seconds
     * @param beforeEachStep called once per fixed step, immediately before
     *                       it — typically re-applying every controlled
     *                       body's current input; may be {@code null}
     */
    public void update(float deltaTime, Runnable beforeEachStep) {
        accumulator += deltaTime;
        int steps = 0;
        while (accumulator >= PhysicsConstants.TIME_STEP && steps < PhysicsConstants.MAX_STEPS_PER_FRAME) {
            if (beforeEachStep != null) {
                beforeEachStep.run();
            }
            world.step(PhysicsConstants.TIME_STEP, 6, 2);
            accumulator -= PhysicsConstants.TIME_STEP;
            steps++;
        }
    }

    /**
     * Returns how far the accumulator is toward the next fixed step, for
     * interpolating a rendered position between the last two physics states.
     * <p>
     * Normally in {@code [0, 1)}: {@code 0} right after a step, approaching
     * {@code 1} just before the next one. <b>Not clamped there</b>, though —
     * if {@code deltaTime} passed to {@link #update(float, Runnable)} is
     * large enough that {@link PhysicsConstants#MAX_STEPS_PER_FRAME} caps
     * how many steps that one call can drain (e.g. after a render-thread
     * stall), leftover time stays in the accumulator and this can return a
     * value well past {@code 1} until subsequent calls catch it back up. A
     * caller that lerps between two states using this as the blend factor
     * (as {@code Client.drawLocalShip} does) will then extrapolate past the
     * current state rather than interpolate (design.md 3.5's addendum).
     *
     * @return normally a value in {@code [0, 1)}; see above for when it isn't
     */
    public float getAlpha() {
        return accumulator / PhysicsConstants.TIME_STEP;
    }
}
