package de.mkoehler.starwars.sim.npc;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.physics.box2d.Body;
import de.mkoehler.starwars.sim.TurretAiming;
import de.mkoehler.starwars.sim.components.WeaponComponent;
import de.mkoehler.starwars.sim.systems.NpcBrainSystem;

/**
 * Checks whether the NPC's own current heading is close enough to its
 * firing solution — the same lead-angle intercept math a turret mount uses
 * ({@link TurretAiming#computeLeadAngle}), compared against the ship's own
 * {@link Body#getAngle()} instead of a turret's separate aim angle. Guards
 * the "fire" sequence: only succeeds (letting {@link FireTask} run) within
 * {@link NpcBrainSystem#FIRING_ALIGNMENT_TOLERANCE_RADIANS} of that solution.
 * <p>
 * See {@link HasValidTargetCondition} for why {@link #copyTo} is a no-op.
 */
public class IsAlignedForShotCondition extends LeafTask<NpcBrain> {

    private final NpcBrainSystem system;

    /**
     * Creates the condition.
     *
     * @param system the brain system, for reading ship bodies/weapon stats
     */
    public IsAlignedForShotCondition(NpcBrainSystem system) {
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

        boolean aligned = Math.abs(TurretAiming.angularDifference(ownBody.getAngle(), desiredAngle))
            <= NpcBrainSystem.FIRING_ALIGNMENT_TOLERANCE_RADIANS;
        return aligned ? Status.SUCCEEDED : Status.FAILED;
    }

    @Override
    protected Task<NpcBrain> copyTo(Task<NpcBrain> task) {
        return task;
    }
}
