package de.mkoehler.starwars.sim.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.ai.btree.BehaviorTree;
import de.mkoehler.starwars.sim.npc.NpcBrain;

/**
 * Marks a ship entity as controlled by an AI behavior tree rather than a
 * connected player's input (design.md — NPC ships), and carries that tree
 * instance and its stagger offset. An entity with this component is never
 * registered in the server's per-connection/per-account maps, which is what
 * structurally keeps an NPC from ever gaining XP, appearing on the
 * scoreboard, or receiving its own personalized snapshot — it's a plain
 * omission, not special-cased logic.
 * <p>
 * The tree/blackboard here is tied to this specific entity instance (see
 * {@link NpcBrain#getEntity()}) — a respawn creates a brand-new entity, so
 * {@code GameNetworkServer} builds and attaches a fresh
 * {@link NpcControlledComponent} (via {@code NpcBrainSystem#createBehaviorTree})
 * rather than carrying this one over.
 */
public class NpcControlledComponent implements Component {

    private final BehaviorTree<NpcBrain> tree;
    private final int tickPhaseOffset;

    /**
     * Creates an NPC-controlled marker component.
     *
     * @param tree            this NPC's behavior tree, already wrapping a
     *                        {@link NpcBrain} blackboard bound to this same entity
     * @param tickPhaseOffset which server tick (modulo
     *                        {@code NpcBrainSystem#BRAIN_TICK_INTERVAL_TICKS}) this NPC's tree
     *                        re-evaluates on, staggering NPCs so they don't all decide on the
     *                        same tick
     */
    public NpcControlledComponent(BehaviorTree<NpcBrain> tree, int tickPhaseOffset) {
        this.tree = tree;
        this.tickPhaseOffset = tickPhaseOffset;
    }

    /**
     * Returns this NPC's behavior tree.
     *
     * @return the behavior tree
     */
    public BehaviorTree<NpcBrain> getTree() {
        return tree;
    }

    /**
     * Returns which server tick (modulo the brain tick interval) this NPC's
     * tree re-evaluates on.
     *
     * @return the tick stagger offset
     */
    public int getTickPhaseOffset() {
        return tickPhaseOffset;
    }
}
