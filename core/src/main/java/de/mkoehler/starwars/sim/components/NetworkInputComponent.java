package de.mkoehler.starwars.sim.components;

import com.badlogic.ashley.core.Component;

/**
 * Holds the most recently received input state for a server-side ship,
 * updated whenever a {@code PlayerInputMessage} arrives from that player's
 * client. Mutable and updated in place (rather than replaced) so
 * {@link de.mkoehler.starwars.sim.systems.ShipControlSystem} and
 * {@link de.mkoehler.starwars.sim.systems.WeaponSystem} always read whatever
 * the latest known state is.
 */
public class NetworkInputComponent implements Component {

    private boolean thrustForward;
    private boolean thrustReverse;
    private boolean turnLeft;
    private boolean turnRight;
    private boolean firing;

    /**
     * Replaces the currently held input state.
     *
     * @param thrustForward whether the forward-thrust input is held
     * @param thrustReverse whether the reverse-thrust input is held
     * @param turnLeft      whether the turn-left input is held
     * @param turnRight     whether the turn-right input is held
     * @param firing        whether the fire-weapon input is held
     */
    public void set(boolean thrustForward, boolean thrustReverse, boolean turnLeft, boolean turnRight, boolean firing) {
        this.thrustForward = thrustForward;
        this.thrustReverse = thrustReverse;
        this.turnLeft = turnLeft;
        this.turnRight = turnRight;
        this.firing = firing;
    }

    /**
     * Returns whether the forward-thrust input is currently held.
     *
     * @return {@code true} if forward thrust is held
     */
    public boolean isThrustForward() {
        return thrustForward;
    }

    /**
     * Returns whether the reverse-thrust input is currently held.
     *
     * @return {@code true} if reverse thrust is held
     */
    public boolean isThrustReverse() {
        return thrustReverse;
    }

    /**
     * Returns whether the turn-left input is currently held.
     *
     * @return {@code true} if turning left is held
     */
    public boolean isTurnLeft() {
        return turnLeft;
    }

    /**
     * Returns whether the turn-right input is currently held.
     *
     * @return {@code true} if turning right is held
     */
    public boolean isTurnRight() {
        return turnRight;
    }

    /**
     * Returns whether the fire-weapon input is currently held.
     *
     * @return {@code true} if the fire input is held
     */
    public boolean isFiring() {
        return firing;
    }
}
