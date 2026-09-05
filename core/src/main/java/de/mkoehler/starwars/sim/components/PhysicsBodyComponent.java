package de.mkoehler.starwars.sim.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;

/**
 * Associates an entity with the Box2D {@link Body} that simulates its
 * position, velocity and rotation.
 * <p>
 * Also tracks the body's position/angle as of the start of the current
 * fixed-timestep physics update, so a renderer can interpolate between that
 * and the body's latest (post-step) state instead of snapping directly to it
 * — needed because Box2D is stepped at a fixed rate (see
 * {@link de.mkoehler.starwars.sim.systems.PhysicsSystem}) that generally
 * doesn't line up with the variable render frame rate; drawing the raw,
 * unsmoothed body position produces visible jitter, most noticeably at
 * higher speeds.
 */
public class PhysicsBodyComponent implements Component {

    private final Body body;
    private final Vector2 previousPosition = new Vector2();
    private float previousAngle;

    /**
     * Creates a component wrapping the given body.
     *
     * @param body the entity's physics body
     */
    public PhysicsBodyComponent(Body body) {
        this.body = body;
        previousPosition.set(body.getPosition());
        previousAngle = body.getAngle();
    }

    /**
     * Returns the Box2D body backing this entity.
     *
     * @return the entity's physics body
     */
    public Body getBody() {
        return body;
    }

    /**
     * Records the body's current position/angle as the "previous" state used
     * for interpolation. Called once per render frame, before any fixed
     * physics steps are taken for that frame.
     */
    public void snapshotPrevious() {
        previousPosition.set(body.getPosition());
        previousAngle = body.getAngle();
    }

    /**
     * Returns the body's position interpolated between the last snapshot and
     * its current (possibly stepped-past-that-point) position.
     *
     * @param alpha how far between the previous and current physics state to
     *              interpolate, in {@code [0, 1]}
     * @return the interpolated position, in meters
     */
    public Vector2 getInterpolatedPosition(float alpha) {
        Vector2 current = body.getPosition();
        return new Vector2(
            MathUtils.lerp(previousPosition.x, current.x, alpha),
            MathUtils.lerp(previousPosition.y, current.y, alpha));
    }

    /**
     * Returns the body's angle interpolated between the last snapshot and its
     * current (possibly stepped-past-that-point) angle.
     *
     * @param alpha how far between the previous and current physics state to
     *              interpolate, in {@code [0, 1]}
     * @return the interpolated angle, in radians
     */
    public float getInterpolatedAngle(float alpha) {
        return MathUtils.lerpAngle(previousAngle, body.getAngle(), alpha);
    }
}
