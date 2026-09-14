package de.mkoehler.starwars.sim.npc;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.physics.box2d.Body;
import de.mkoehler.starwars.sim.TurretAiming;
import de.mkoehler.starwars.sim.components.WeaponComponent;
import de.mkoehler.starwars.sim.systems.NpcBrainSystem;

/**
 * Turns the NPC's ship body toward its target's firing solution and thrusts
 * forward — the "act-on-target" selector's fallback for whenever {@link
 * IsAlignedForShotCondition} isn't satisfied yet. Only ever reached with a
 * valid {@link NpcBrain#getTarget()} (guarded by the enclosing "engage"
 * sequence), so heading toward it always makes progress toward eventually
 * satisfying that alignment check.
 * <p>
 * Unlike a turret mount, the ship's actual heading is driven by physical
 * torque ({@code ShipControlSystem}), not by directly overwriting an aim
 * angle — so this task only needs to decide a turn <em>direction</em>
 * (left/right), not a turn <em>rate</em>; the ship's own tuned turn torque
 * takes care of the rest, same as a human player holding a turn key.
 * <p>
 * Deliberately steers toward the same lead angle {@link IsAlignedForShotCondition}
 * checks against, not the target's plain bearing — steering toward bearing
 * instead would fight alignment (the NPC would converge on a heading the
 * fire sequence never accepts) whenever the target is moving. The known
 * trade-off (design.md — NPC ships): at long range against a fast-moving
 * target, the lead angle can diverge substantially from the target's actual
 * bearing, so a distant NPC can visibly fly toward empty space rather than
 * toward the target it's pursuing. Untuned for v1; a range-gated switch
 * (steer by bearing outside effective weapon range, lead only once closer)
 * would fix this but is out of scope here.
 * <p>
 * See {@link HasValidTargetCondition} for why {@link #copyTo} is a no-op.
 */
public class PursueTargetTask extends LeafTask<NpcBrain> {

    private final NpcBrainSystem system;

    /**
     * Creates the task.
     *
     * @param system the brain system, for reading ship bodies/weapon stats
     */
    public PursueTargetTask(NpcBrainSystem system) {
        this.system = system;
    }

    @Override
    public Status execute() {
        NpcBrain brain = getObject();
        Entity target = brain.getTarget();
        if (target == null) {
            return Status.FAILED; // guarded by HasValidTargetCondition earlier in the sequence, but defensive
        }

        Body ownBody = system.bodyOf(brain.getEntity());
        Body targetBody = system.bodyOf(target);
        WeaponComponent weapon = system.weaponOf(brain.getEntity());

        float desiredAngle = TurretAiming.computeLeadAngle(
            ownBody.getPosition().x, ownBody.getPosition().y,
            targetBody.getPosition().x, targetBody.getPosition().y,
            targetBody.getLinearVelocity().x, targetBody.getLinearVelocity().y,
            weapon.getStats().getProjectileSpeed());

        float diff = TurretAiming.angularDifference(desiredAngle, ownBody.getAngle());
        brain.setTurnLeft(diff > 0f);
        brain.setTurnRight(diff < 0f);
        brain.setThrustForward(true);
        return Status.SUCCEEDED;
    }

    @Override
    protected Task<NpcBrain> copyTo(Task<NpcBrain> task) {
        return task;
    }
}
