package de.mkoehler.starwars.net.messages;

/**
 * Sent repeatedly by a client to report which movement inputs are currently
 * held, so the server can apply them to that player's ship. Reflects the
 * v1 default control scheme (design.md 5.3); not yet driven by remappable
 * keybinds.
 */
public class PlayerInputMessage {

    private boolean thrustForward;
    private boolean thrustReverse;
    private boolean turnLeft;
    private boolean turnRight;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public PlayerInputMessage() {
    }

    /**
     * Creates an input message.
     *
     * @param thrustForward whether the forward-thrust input is currently held
     * @param thrustReverse whether the reverse-thrust input is currently held
     * @param turnLeft      whether the turn-left input is currently held
     * @param turnRight     whether the turn-right input is currently held
     */
    public PlayerInputMessage(boolean thrustForward, boolean thrustReverse, boolean turnLeft, boolean turnRight) {
        this.thrustForward = thrustForward;
        this.thrustReverse = thrustReverse;
        this.turnLeft = turnLeft;
        this.turnRight = turnRight;
    }

    /**
     * Returns whether the forward-thrust input is held.
     *
     * @return {@code true} if forward thrust is currently held
     */
    public boolean isThrustForward() {
        return thrustForward;
    }

    /**
     * Returns whether the reverse-thrust input is held.
     *
     * @return {@code true} if reverse thrust is currently held
     */
    public boolean isThrustReverse() {
        return thrustReverse;
    }

    /**
     * Returns whether the turn-left input is held.
     *
     * @return {@code true} if turning left is currently held
     */
    public boolean isTurnLeft() {
        return turnLeft;
    }

    /**
     * Returns whether the turn-right input is held.
     *
     * @return {@code true} if turning right is currently held
     */
    public boolean isTurnRight() {
        return turnRight;
    }
}
