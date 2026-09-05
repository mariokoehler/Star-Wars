package de.mkoehler.starwars.sim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.physics.box2d.World;
import de.mkoehler.starwars.sim.PhysicsConstants;
import de.mkoehler.starwars.sim.components.PhysicsBodyComponent;

/**
 * Steps a Box2D {@link World} at a fixed timestep, accumulating leftover
 * frame time between calls so the simulation rate is independent of the
 * rendering frame rate.
 * <p>
 * Also snapshots every physics entity's pre-step state each frame (see
 * {@link PhysicsBodyComponent#snapshotPrevious()}) and exposes
 * {@link #getAlpha()}, together letting a renderer interpolate positions
 * instead of drawing the raw, fixed-step body position directly — see
 * {@link PhysicsBodyComponent} for why that matters.
 */
public class PhysicsSystem extends IteratingSystem {

    private final ComponentMapper<PhysicsBodyComponent> bodyMapper = ComponentMapper.getFor(PhysicsBodyComponent.class);

    private final World world;
    private float accumulator;

    /**
     * Creates a physics system stepping the given world.
     *
     * @param world the Box2D world to step
     */
    public PhysicsSystem(World world) {
        super(Family.all(PhysicsBodyComponent.class).get());
        this.world = world;
    }

    @Override
    public void update(float deltaTime) {
        // Snapshot every entity's pre-step state before stepping, so it can later be
        // interpolated against the post-step state below (see processEntity).
        super.update(deltaTime);

        accumulator += deltaTime;
        int steps = 0;
        while (accumulator >= PhysicsConstants.TIME_STEP && steps < PhysicsConstants.MAX_STEPS_PER_FRAME) {
            world.step(PhysicsConstants.TIME_STEP, 6, 2);
            accumulator -= PhysicsConstants.TIME_STEP;
            steps++;
        }
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        bodyMapper.get(entity).snapshotPrevious();
    }

    /**
     * Returns how far the accumulator is toward the next fixed step, for
     * interpolating rendered positions between the last two physics states.
     *
     * @return a value in {@code [0, 1)}: {@code 0} right after a step,
     * approaching {@code 1} just before the next one
     */
    public float getAlpha() {
        return accumulator / PhysicsConstants.TIME_STEP;
    }
}
