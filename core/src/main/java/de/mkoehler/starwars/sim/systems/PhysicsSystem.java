package de.mkoehler.starwars.sim.systems;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.physics.box2d.World;
import de.mkoehler.starwars.sim.PhysicsConstants;

/**
 * Steps a Box2D {@link World} at a fixed timestep, accumulating leftover
 * frame time between calls so the simulation rate is independent of the
 * caller's update rate.
 * <p>
 * Runs server-side only (see design.md 3.5) — the server is the sole owner of
 * ship physics; clients render server-broadcast snapshots instead of
 * simulating locally, so they have no need for this system.
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
        accumulator += deltaTime;
        int steps = 0;
        while (accumulator >= PhysicsConstants.TIME_STEP && steps < PhysicsConstants.MAX_STEPS_PER_FRAME) {
            world.step(PhysicsConstants.TIME_STEP, 6, 2);
            accumulator -= PhysicsConstants.TIME_STEP;
            steps++;
        }
    }
}
