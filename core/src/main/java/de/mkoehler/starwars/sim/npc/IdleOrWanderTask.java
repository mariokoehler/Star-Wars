package de.mkoehler.starwars.sim.npc;

import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;

/**
 * Root selector's fallback for whenever {@link HasValidTargetCondition}
 * finds no enemy in range: a fixed, gentle turning circle (thrust forward,
 * always turn left, never fire) rather than sitting motionless — an
 * untuned v1 placeholder (design.md — NPC ships), likely the first thing
 * worth revisiting once actually playable.
 * <p>
 * See {@link HasValidTargetCondition} for why {@link #copyTo} is a no-op.
 */
public class IdleOrWanderTask extends LeafTask<NpcBrain> {

    @Override
    public Status execute() {
        NpcBrain brain = getObject();
        brain.setThrustForward(true);
        brain.setTurnLeft(true);
        brain.setTurnRight(false);
        brain.setFiring(false);
        return Status.SUCCEEDED;
    }

    @Override
    protected Task<NpcBrain> copyTo(Task<NpcBrain> task) {
        return task;
    }
}
