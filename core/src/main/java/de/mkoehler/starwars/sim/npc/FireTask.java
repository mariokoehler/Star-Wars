package de.mkoehler.starwars.sim.npc;

import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;

/**
 * Fires the NPC's weapon — only reached once {@link IsAlignedForShotCondition}
 * has already succeeded this tick. Whether the shot actually leaves the gun
 * (enough capacitor charge, cooldown expired) is decided the same way it is
 * for a human player, by {@code WeaponSystem} reading the resulting {@link
 * de.mkoehler.starwars.sim.components.NetworkInputComponent#isFiring()}.
 * <p>
 * Also keeps thrusting forward while firing — {@link NpcBrain#resetDecision()}
 * clears every decision field to false at the start of each brain tick, and
 * since the "act-on-target" selector runs this task <em>instead of</em>
 * {@link PursueTargetTask} (never both in the same tick), an aligned,
 * firing NPC would otherwise stall in place for the whole ~200ms until its
 * next brain tick.
 * <p>
 * See {@link HasValidTargetCondition} for why {@link #copyTo} is a no-op.
 */
public class FireTask extends LeafTask<NpcBrain> {

    @Override
    public Status execute() {
        NpcBrain brain = getObject();
        brain.setThrustForward(true);
        brain.setFiring(true);
        return Status.SUCCEEDED;
    }

    @Override
    protected Task<NpcBrain> copyTo(Task<NpcBrain> task) {
        return task;
    }
}
