package de.mkoehler.starwars.sim.components;

import com.badlogic.ashley.core.Component;

/**
 * Marks an entity as reading keyboard input directly, and carries the
 * per-ship tuning values that input is turned into. Placeholder values for
 * now — see design.md 5's "Ship roster" TODO for making these data-driven
 * per ship type instead of hardcoded per instance.
 */
public class PlayerControlledComponent implements Component {

    private final float thrustForce;
    private final float turnTorque;
    private final float engineTurnResponseExponent;

    /**
     * Creates a player-controlled component.
     *
     * @param thrustForce                force, in newtons, applied along the ship's facing
     *                                   direction while the thrust-forward input is held
     * @param turnTorque                 torque, in newton-meters, applied while a turn input
     *                                   is held
     * @param engineTurnResponseExponent this ship type's own
     *                                   {@link de.mkoehler.starwars.sim.ShipTypeConfig#getEngineTurnResponseExponent()}
     *                                   (design.md 2.2's addendum)
     */
    public PlayerControlledComponent(float thrustForce, float turnTorque, float engineTurnResponseExponent) {
        this.thrustForce = thrustForce;
        this.turnTorque = turnTorque;
        this.engineTurnResponseExponent = engineTurnResponseExponent;
    }

    /**
     * Returns the thrust force applied while thrusting forward.
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

    /**
     * Returns the exponent applied to the Engines power multiplier before
     * it scales {@link #getTurnTorque()} (design.md 2.2's addendum).
     *
     * @return the turn-response exponent
     */
    public float getEngineTurnResponseExponent() {
        return engineTurnResponseExponent;
    }
}
