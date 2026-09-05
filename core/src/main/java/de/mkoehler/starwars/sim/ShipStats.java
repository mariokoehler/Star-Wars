package de.mkoehler.starwars.sim;

/**
 * Per-ship-type tuning values shared between the server (which needs them to
 * build a Box2D body and apply forces) and the client (which needs the same
 * radius to size the drawn sprite consistently with the server's collision
 * shape).
 * <p>
 * A single hardcoded constant for now, standing in for a future data-driven
 * ship roster (see design.md 6's "Ship roster" TODO).
 */
public final class ShipStats {

    /**
     * Stats for the (currently only) X-wing, derived from its 128px atlas
     * frame at {@link PhysicsConstants#PIXELS_PER_METER}.
     */
    public static final ShipStats XWING = new ShipStats(2f, 30f, 22.5f);

    private final float radiusMeters;
    private final float thrustForce;
    private final float turnTorque;

    private ShipStats(float radiusMeters, float thrustForce, float turnTorque) {
        this.radiusMeters = radiusMeters;
        this.thrustForce = thrustForce;
        this.turnTorque = turnTorque;
    }

    /**
     * Returns the ship's collision/draw radius.
     *
     * @return the radius, in meters
     */
    public float getRadiusMeters() {
        return radiusMeters;
    }

    /**
     * Returns the force applied while thrusting.
     *
     * @return the thrust force, in newtons
     */
    public float getThrustForce() {
        return thrustForce;
    }

    /**
     * Returns the torque applied while turning.
     *
     * @return the turn torque, in newton-meters
     */
    public float getTurnTorque() {
        return turnTorque;
    }
}
