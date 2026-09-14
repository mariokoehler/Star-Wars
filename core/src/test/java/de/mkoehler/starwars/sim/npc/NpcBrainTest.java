package de.mkoehler.starwars.sim.npc;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link NpcBrain}'s plain get/set behavior and
 * {@link NpcBrain#resetDecision()}'s exact scope (clears the decision
 * output, leaves the target alone).
 */
class NpcBrainTest {

    @Test
    void startsWithNoTargetAndNoDecision() {
        NpcBrain brain = new NpcBrain(new Entity());

        assertNull(brain.getTarget());
        assertFalse(brain.isThrustForward());
        assertFalse(brain.isTurnLeft());
        assertFalse(brain.isTurnRight());
        assertFalse(brain.isFiring());
    }

    @Test
    void getEntityReturnsTheOwningEntity() {
        Entity entity = new Entity();
        NpcBrain brain = new NpcBrain(entity);

        assertSame(entity, brain.getEntity());
    }

    @Test
    void decisionSettersAreReflectedByTheirGetters() {
        NpcBrain brain = new NpcBrain(new Entity());

        brain.setThrustForward(true);
        brain.setTurnLeft(true);
        brain.setFiring(true);

        assertTrue(brain.isThrustForward());
        assertTrue(brain.isTurnLeft());
        assertFalse(brain.isTurnRight());
        assertTrue(brain.isFiring());
    }

    @Test
    void resetDecisionClearsEveryDecisionField() {
        NpcBrain brain = new NpcBrain(new Entity());
        brain.setThrustForward(true);
        brain.setTurnLeft(true);
        brain.setTurnRight(true);
        brain.setFiring(true);

        brain.resetDecision();

        assertFalse(brain.isThrustForward());
        assertFalse(brain.isTurnLeft());
        assertFalse(brain.isTurnRight());
        assertFalse(brain.isFiring());
    }

    @Test
    void resetDecisionLeavesTheTargetUntouched() {
        NpcBrain brain = new NpcBrain(new Entity());
        Entity target = new Entity();
        brain.setTarget(target);

        brain.resetDecision();

        assertSame(target, brain.getTarget());
    }
}
