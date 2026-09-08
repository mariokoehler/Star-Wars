package de.mkoehler.starwars.sim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.physics.box2d.Body;
import de.mkoehler.starwars.sim.RadarDetection;
import de.mkoehler.starwars.sim.ShipStats;
import de.mkoehler.starwars.sim.components.HullComponent;
import de.mkoehler.starwars.sim.components.MissileLockComponent;
import de.mkoehler.starwars.sim.components.PhysicsBodyComponent;
import de.mkoehler.starwars.sim.components.PlayerIdComponent;
import de.mkoehler.starwars.sim.components.ShipTypeComponent;

/**
 * Drives every missile-capable ship's lock-acquisition state machine
 * (design.md — missiles), server-side only, same "runs once per tick"
 * placement as {@code TurretSystem}/{@code RadarSystem}.
 * <p>
 * Per ship, each tick: if it has a current lock target, drop the lock
 * entirely (reset to no target/zero progress/not acquired) the instant that
 * target either dies or leaves this ship's own
 * {@link RadarDetection#isWithinCone cone radar} — deliberately strict,
 * matching the spec's "the lock is immediately lost if the enemy manages to
 * leave the radar cone" applied at every pre-fire stage, not just during
 * acquisition (flagged in design.md as an interpretation, with a named
 * alternative: a short grace period before a cone exit actually resets
 * progress, if this feels too twitchy in play). If there's no current
 * target <em>and at least one missile remains</em>, scan for the closest
 * enemy within cone and lock onto it — sticky once chosen, per the spec
 * ("it will keep trying to get a lock on that enemy even if another enemy
 * gets closer"). A ship with zero missiles left never starts a new
 * acquisition at all (design.md — missiles' addendum), which also doubles
 * as the player's only current signal of running out, since the lock
 * reticle simply stops appearing. With a target and not yet acquired,
 * accumulate lock progress; once it reaches the ship type's configured
 * duration, the lock is acquired (and stays acquired for as long as cone
 * visibility holds).
 */
public class MissileLockSystem extends IteratingSystem {

    private static final Family LIVE_SHIP_FAMILY =
        Family.all(PhysicsBodyComponent.class, PlayerIdComponent.class, HullComponent.class).get();

    private final ComponentMapper<MissileLockComponent> lockMapper = ComponentMapper.getFor(MissileLockComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> bodyMapper = ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<PlayerIdComponent> playerIdMapper = ComponentMapper.getFor(PlayerIdComponent.class);
    private final ComponentMapper<ShipTypeComponent> shipTypeMapper = ComponentMapper.getFor(ShipTypeComponent.class);

    private final Engine engine;

    private ImmutableArray<Entity> liveShips;

    /**
     * Creates the missile lock system.
     *
     * @param engine the Ashley engine to scan for live ships every tick
     */
    public MissileLockSystem(Engine engine) {
        super(Family.all(MissileLockComponent.class, PhysicsBodyComponent.class, PlayerIdComponent.class,
            ShipTypeComponent.class).get());
        this.engine = engine;
    }

    @Override
    public void update(float deltaTime) {
        liveShips = engine.getEntitiesFor(LIVE_SHIP_FAMILY);
        super.update(deltaTime);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        MissileLockComponent lock = lockMapper.get(entity);
        Body ownBody = bodyMapper.get(entity).getBody();
        int ownerPlayerId = playerIdMapper.get(entity).getPlayerId();
        ShipStats stats = ShipStats.forType(shipTypeMapper.get(entity).getShipType());

        float ownX = ownBody.getPosition().x;
        float ownY = ownBody.getPosition().y;
        float ownAngle = ownBody.getAngle();
        float coneRangeMeters = stats.getRadarConeRangeMeters();
        float coneHalfAngleDegrees = stats.getRadarConeHalfAngleDegrees();

        if (lock.getLockTarget() != null
            && !isWithinConeAndAlive(lock.getLockTarget(), ownX, ownY, ownAngle, coneRangeMeters, coneHalfAngleDegrees)) {
            lock.resetLock();
        }

        // No missiles left, nothing to fire, so nothing to start acquiring a lock for - also
        // doubles as the player's only current signal of running out (design.md - missiles' open
        // point: no explicit ammo-count HUD indicator yet), since the reticle simply never starts
        // appearing once this hits zero.
        if (lock.getLockTarget() == null && lock.getMissileCount() > 0) {
            Entity closest = findClosestInCone(ownerPlayerId, ownX, ownY, ownAngle, coneRangeMeters, coneHalfAngleDegrees);
            if (closest != null) {
                lock.setLockTarget(closest);
                lock.setLockProgressSeconds(0f);
            }
        }

        if (lock.getLockTarget() != null && !lock.isLockAcquired()) {
            lock.setLockProgressSeconds(lock.getLockProgressSeconds() + deltaTime);
            if (lock.getLockProgressSeconds() >= stats.getMissileLockDurationSeconds()) {
                lock.setLockAcquired(true);
            }
        }
    }

    private boolean isWithinConeAndAlive(Entity target, float ownX, float ownY, float ownAngle,
                                          float coneRangeMeters, float coneHalfAngleDegrees) {
        if (!liveShips.contains(target, true)) {
            return false;
        }
        Body targetBody = bodyMapper.get(target).getBody();
        return RadarDetection.isWithinCone(ownX, ownY, ownAngle,
            targetBody.getPosition().x, targetBody.getPosition().y, coneRangeMeters, coneHalfAngleDegrees);
    }

    private Entity findClosestInCone(int ownerPlayerId, float ownX, float ownY, float ownAngle,
                                      float coneRangeMeters, float coneHalfAngleDegrees) {
        Entity closest = null;
        float closestDistanceSq = Float.MAX_VALUE;
        for (Entity candidate : liveShips) {
            if (playerIdMapper.get(candidate).getPlayerId() == ownerPlayerId) {
                continue; // never lock your own ship
            }
            Body candidateBody = bodyMapper.get(candidate).getBody();
            float candidateX = candidateBody.getPosition().x;
            float candidateY = candidateBody.getPosition().y;
            if (!RadarDetection.isWithinCone(ownX, ownY, ownAngle, candidateX, candidateY,
                coneRangeMeters, coneHalfAngleDegrees)) {
                continue;
            }
            float dx = candidateX - ownX;
            float dy = candidateY - ownY;
            float distanceSq = dx * dx + dy * dy;
            if (distanceSq < closestDistanceSq) {
                closest = candidate;
                closestDistanceSq = distanceSq;
            }
        }
        return closest;
    }
}
