package de.mkoehler.starwars.sim.npc;

import com.badlogic.ashley.core.Entity;

/**
 * The gdx-ai behavior tree blackboard for one NPC ship (design.md — NPC
 * ships): the data its leaf tasks read and write. Deliberately dumb — all
 * Ashley/Box2D access lives in {@code NpcBrainSystem}, reached by the leaf
 * tasks that need it, keeping this class a plain per-NPC state holder.
 * <p>
 * {@link #resetDecision()} is called once at the start of every brain tick,
 * before the tree steps — every leaf task in the tree either runs to
 * completion or fails in that same step (none ever return {@code RUNNING}),
 * so each tick's decision is a fresh, complete re-evaluation rather than an
 * incremental update. The decision fields below therefore reflect only the
 * current tick's outcome; {@code NpcBrainSystem} applies them to the ship's
 * {@link de.mkoehler.starwars.sim.components.NetworkInputComponent} in one
 * call once the tree finishes, so no leaf task can apply a partial decision.
 */
public class NpcBrain {

    private final Entity entity;

    private Entity target;
    private boolean thrustForward;
    private boolean turnLeft;
    private boolean turnRight;
    private boolean firing;

    /**
     * Creates a blackboard for the given NPC ship entity.
     *
     * @param entity the NPC's own ship entity
     */
    public NpcBrain(Entity entity) {
        this.entity = entity;
    }

    /**
     * Returns the NPC's own ship entity.
     *
     * @return the entity this blackboard belongs to
     */
    public Entity getEntity() {
        return entity;
    }

    /**
     * Returns the enemy ship currently being pursued/engaged, as (re)decided
     * by {@code HasValidTargetCondition} this tick.
     *
     * @return the current target, or {@code null} if none was found
     */
    public Entity getTarget() {
        return target;
    }

    /**
     * Sets the enemy ship currently being pursued/engaged.
     *
     * @param target the new target, or {@code null} for none
     */
    public void setTarget(Entity target) {
        this.target = target;
    }

    /**
     * Clears this tick's decision output back to "do nothing," leaving
     * {@link #getTarget()} untouched (it's set fresh by
     * {@code HasValidTargetCondition} at the start of every tick's
     * evaluation regardless). Called once by {@code NpcBrainSystem} before
     * stepping the tree.
     */
    public void resetDecision() {
        thrustForward = false;
        turnLeft = false;
        turnRight = false;
        firing = false;
    }

    /**
     * Returns whether this tick's decision holds forward thrust.
     *
     * @return {@code true} if forward thrust should be applied
     */
    public boolean isThrustForward() {
        return thrustForward;
    }

    /**
     * Sets whether this tick's decision holds forward thrust.
     *
     * @param thrustForward {@code true} to thrust forward
     */
    public void setThrustForward(boolean thrustForward) {
        this.thrustForward = thrustForward;
    }

    /**
     * Returns whether this tick's decision turns left.
     *
     * @return {@code true} if turning left
     */
    public boolean isTurnLeft() {
        return turnLeft;
    }

    /**
     * Sets whether this tick's decision turns left.
     *
     * @param turnLeft {@code true} to turn left
     */
    public void setTurnLeft(boolean turnLeft) {
        this.turnLeft = turnLeft;
    }

    /**
     * Returns whether this tick's decision turns right.
     *
     * @return {@code true} if turning right
     */
    public boolean isTurnRight() {
        return turnRight;
    }

    /**
     * Sets whether this tick's decision turns right.
     *
     * @param turnRight {@code true} to turn right
     */
    public void setTurnRight(boolean turnRight) {
        this.turnRight = turnRight;
    }

    /**
     * Returns whether this tick's decision fires the weapon.
     *
     * @return {@code true} if firing
     */
    public boolean isFiring() {
        return firing;
    }

    /**
     * Sets whether this tick's decision fires the weapon.
     *
     * @param firing {@code true} to fire
     */
    public void setFiring(boolean firing) {
        this.firing = firing;
    }
}
