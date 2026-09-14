package de.mkoehler.starwars.sim.npc;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.physics.box2d.Body;
import de.mkoehler.starwars.sim.systems.NpcBrainSystem;

/**
 * Root condition of the "engage" sequence: scans for the closest live enemy
 * ship within {@link NpcBrainSystem#DETECTION_RANGE_METERS} and stores it on
 * the blackboard as {@link NpcBrain#setTarget(Entity)}. There's no separate
 * "is my previous target still valid" check — since brain ticks are already
 * coarse (~200ms), simply re-scanning for the closest enemy every tick is
 * both simpler and effectively equivalent to sticky per-tick tracking.
 * <p>
 * This tree is built directly in Java once per NPC (design.md — NPC ships
 * explicitly skips gdx-ai's {@code .tree} text DSL), so {@link #copyTo}
 * is never actually invoked — {@link Task#cloneTask()} is only reachable
 * through that DSL loader or an explicit clone call, neither of which this
 * codebase uses.
 */
public class HasValidTargetCondition extends LeafTask<NpcBrain> {

    private final NpcBrainSystem system;

    /**
     * Creates the condition.
     *
     * @param system the brain system, for its Ashley/Box2D-backed target scan
     */
    public HasValidTargetCondition(NpcBrainSystem system) {
        this.system = system;
    }

    @Override
    public Status execute() {
        NpcBrain brain = getObject();
        Body ownBody = system.bodyOf(brain.getEntity());
        int ownerPlayerId = system.playerIdOf(brain.getEntity());

        Entity target = system.findClosestEnemy(ownerPlayerId, ownBody.getPosition().x, ownBody.getPosition().y);
        brain.setTarget(target);
        return target != null ? Status.SUCCEEDED : Status.FAILED;
    }

    @Override
    protected Task<NpcBrain> copyTo(Task<NpcBrain> task) {
        return task;
    }
}
