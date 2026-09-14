package de.mkoehler.starwars.sim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.ai.btree.BehaviorTree;
import com.badlogic.gdx.ai.btree.branch.Selector;
import com.badlogic.gdx.ai.btree.branch.Sequence;
import com.badlogic.gdx.physics.box2d.Body;
import de.mkoehler.starwars.sim.TargetFinder;
import de.mkoehler.starwars.sim.components.HullComponent;
import de.mkoehler.starwars.sim.components.NetworkInputComponent;
import de.mkoehler.starwars.sim.components.NpcControlledComponent;
import de.mkoehler.starwars.sim.components.PhysicsBodyComponent;
import de.mkoehler.starwars.sim.components.PlayerIdComponent;
import de.mkoehler.starwars.sim.components.WeaponComponent;
import de.mkoehler.starwars.sim.npc.FireTask;
import de.mkoehler.starwars.sim.npc.HasValidTargetCondition;
import de.mkoehler.starwars.sim.npc.IdleOrWanderTask;
import de.mkoehler.starwars.sim.npc.IsAlignedForShotCondition;
import de.mkoehler.starwars.sim.npc.NpcBrain;
import de.mkoehler.starwars.sim.npc.PursueTargetTask;

/**
 * Drives every NPC ship's gdx-ai behavior tree (design.md — NPC ships),
 * server-side only, same "runs once per tick" placement as {@code
 * TurretSystem}/{@code MissileLockSystem}.
 * <p>
 * Each NPC's tree steps far less often than the ~30Hz server tick — every
 * {@link #BRAIN_TICK_INTERVAL_TICKS} ticks (~200ms), staggered per-NPC via
 * {@link NpcControlledComponent#getTickPhaseOffset()} so many NPCs don't all
 * re-decide on the same tick. Between an NPC's own brain ticks, its {@link
 * NetworkInputComponent} is left untouched — the previously-decided
 * thrust/turn/fire booleans simply persist, exactly like a human player
 * holding a key between input packets. Every leaf task in the tree runs to
 * completion or fails within one {@link BehaviorTree#step()} call (none
 * ever return {@code RUNNING}), so each brain tick is a fresh, complete
 * top-to-bottom re-evaluation, not an incremental one.
 * <p>
 * This system centralizes every bit of Ashley/Box2D access the tree's leaf
 * tasks need (finding the closest enemy, reading a ship's body/weapon) —
 * {@link NpcBrain}, the blackboard, stays a plain data holder, matching how
 * every other system in this codebase keeps component classes free of
 * engine/world coupling.
 */
public class NpcBrainSystem extends IteratingSystem {

    private static final Family LIVE_SHIP_FAMILY =
        Family.all(PhysicsBodyComponent.class, PlayerIdComponent.class, HullComponent.class).get();

    /** How often (in server ticks) each individual NPC's tree re-evaluates - ~200ms at 30Hz, an untuned placeholder (design.md - NPC ships). */
    public static final int BRAIN_TICK_INTERVAL_TICKS = 6;

    /**
     * How far an NPC will look for an enemy to engage, in meters - deliberately larger than the
     * 500x500m arena's diagonal, i.e. "unrestricted" in practice, unlike the radar-cone-gated
     * detection a human player's UI applies to itself (design.md - NPC ships' proposed default).
     */
    public static final float DETECTION_RANGE_METERS = 1000f;

    /** How close an NPC's heading must be to its firing solution before it's allowed to shoot - untuned placeholder, matching {@code TurretSystem}'s own tolerance. */
    public static final float FIRING_ALIGNMENT_TOLERANCE_RADIANS = (float) Math.toRadians(5.0);

    private final ComponentMapper<NpcControlledComponent> npcMapper = ComponentMapper.getFor(NpcControlledComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> bodyMapper = ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<PlayerIdComponent> playerIdMapper = ComponentMapper.getFor(PlayerIdComponent.class);
    private final ComponentMapper<WeaponComponent> weaponMapper = ComponentMapper.getFor(WeaponComponent.class);
    private final ComponentMapper<NetworkInputComponent> inputMapper = ComponentMapper.getFor(NetworkInputComponent.class);

    private final Engine engine;

    private ImmutableArray<Entity> liveShips;
    private long tickCounter;

    /**
     * Creates the NPC brain system.
     *
     * @param engine the Ashley engine to scan for live ships every tick
     */
    public NpcBrainSystem(Engine engine) {
        super(Family.all(NpcControlledComponent.class, NetworkInputComponent.class, PhysicsBodyComponent.class,
            PlayerIdComponent.class, WeaponComponent.class).get());
        this.engine = engine;
    }

    /**
     * Builds a fresh behavior tree + blackboard for one NPC ship entity —
     * called once when an NPC first spawns and again every time it
     * respawns (a respawn creates a brand-new entity, so the old tree's
     * blackboard would otherwise point at a destroyed one).
     *
     * @param entity the NPC ship entity the new tree will control
     * @return the tree, ready to be stored on that entity's {@link NpcControlledComponent}
     */
    public BehaviorTree<NpcBrain> createBehaviorTree(Entity entity) {
        NpcBrain brain = new NpcBrain(entity);
        Sequence<NpcBrain> fire = new Sequence<>(new IsAlignedForShotCondition(this), new FireTask());
        Selector<NpcBrain> actOnTarget = new Selector<>(fire, new PursueTargetTask(this));
        Sequence<NpcBrain> engage = new Sequence<>(new HasValidTargetCondition(this), actOnTarget);
        Selector<NpcBrain> root = new Selector<>(engage, new IdleOrWanderTask());
        return new BehaviorTree<>(root, brain);
    }

    @Override
    public void update(float deltaTime) {
        // Refreshed once per tick, not per NPC - every NPC's target scan reads from this same
        // snapshot of "who's currently alive," same reasoning as TurretSystem/MissileLockSystem.
        liveShips = engine.getEntitiesFor(LIVE_SHIP_FAMILY);
        tickCounter++;
        super.update(deltaTime);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        NpcControlledComponent npc = npcMapper.get(entity);
        if ((tickCounter + npc.getTickPhaseOffset()) % BRAIN_TICK_INTERVAL_TICKS != 0) {
            return; // not this NPC's tick to re-decide - its NetworkInputComponent keeps holding its last decision
        }

        NpcBrain brain = npc.getTree().getObject();
        brain.resetDecision();
        npc.getTree().step();

        NetworkInputComponent input = inputMapper.get(entity);
        input.set(brain.isThrustForward(), brain.isTurnLeft(), brain.isTurnRight(), brain.isFiring());
    }

    /**
     * Finds the closest live enemy ship within {@link #DETECTION_RANGE_METERS}, for
     * {@code HasValidTargetCondition}.
     *
     * @param ownerPlayerId the searching NPC's own player id, never returned
     * @param fromX         the search origin's X position, in meters
     * @param fromY         the search origin's Y position, in meters
     * @return the closest enemy ship, or {@code null} if none are in range
     */
    public Entity findClosestEnemy(int ownerPlayerId, float fromX, float fromY) {
        return TargetFinder.findClosest(liveShips, this::candidateOwnerPlayerId, this::candidateX, this::candidateY,
            ownerPlayerId, fromX, fromY, TargetFinder.withinRange(fromX, fromY, DETECTION_RANGE_METERS));
    }

    /**
     * Returns the given ship entity's Box2D body, for a leaf task that needs
     * to read its position/velocity/angle.
     *
     * @param entity a ship entity (the NPC itself or its target)
     * @return the entity's physics body
     */
    public Body bodyOf(Entity entity) {
        return bodyMapper.get(entity).getBody();
    }

    /**
     * Returns the given ship entity's weapon component, for a leaf task
     * that needs its projectile speed (for a lead-angle solve) or firing
     * state.
     *
     * @param entity a ship entity
     * @return the entity's weapon component
     */
    public WeaponComponent weaponOf(Entity entity) {
        return weaponMapper.get(entity);
    }

    /**
     * Returns the given ship entity's owning player id.
     *
     * @param entity a ship entity
     * @return the owning player id
     */
    public int playerIdOf(Entity entity) {
        return playerIdMapper.get(entity).getPlayerId();
    }

    private int candidateOwnerPlayerId(Entity candidate) {
        return playerIdMapper.get(candidate).getPlayerId();
    }

    private float candidateX(Entity candidate) {
        return bodyMapper.get(candidate).getBody().getPosition().x;
    }

    private float candidateY(Entity candidate) {
        return bodyMapper.get(candidate).getBody().getPosition().y;
    }
}
