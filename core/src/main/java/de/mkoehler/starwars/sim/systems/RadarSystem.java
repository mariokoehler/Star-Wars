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
import de.mkoehler.starwars.sim.components.PhysicsBodyComponent;
import de.mkoehler.starwars.sim.components.PlayerIdComponent;
import de.mkoehler.starwars.sim.components.RadarComponent;
import de.mkoehler.starwars.sim.components.ShipTypeComponent;

/**
 * Recomputes every ship's set of currently-detected enemies every tick
 * (design.md 2.14). Server-side only.
 * <p>
 * For each live ship (the observer), checks every <em>other</em> live ship
 * (the target) against {@link RadarDetection} using the observer's own
 * ship-type radar config (base/cone/pulse-active), then additionally treats
 * the target as detected regardless of that check if the target's own pulse
 * is currently active — a pulsing ship is visible to everyone, independent
 * of the observer's own equipment or range. The result replaces
 * {@link RadarComponent#getDetectedPlayerIds()} wholesale each tick;
 * {@code GameNetworkServer.broadcastSnapshot()} reads it to build each
 * player's personalized {@code WorldSnapshotMessage}.
 * <p>
 * Also ticks every ship's pulse cooldown/active-duration timers
 * ({@link RadarComponent#tickCooldowns(float)}) — done here rather than a
 * separate system, since both need the exact same per-ship iteration this
 * system already does.
 */
public class RadarSystem extends IteratingSystem {

    private static final Family LIVE_SHIP_FAMILY = Family.all(
        PhysicsBodyComponent.class, PlayerIdComponent.class, RadarComponent.class, ShipTypeComponent.class).get();

    private final ComponentMapper<PhysicsBodyComponent> bodyMapper = ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<PlayerIdComponent> playerIdMapper = ComponentMapper.getFor(PlayerIdComponent.class);
    private final ComponentMapper<RadarComponent> radarMapper = ComponentMapper.getFor(RadarComponent.class);
    private final ComponentMapper<ShipTypeComponent> shipTypeMapper = ComponentMapper.getFor(ShipTypeComponent.class);

    private final Engine engine;

    private ImmutableArray<Entity> liveShips;

    /**
     * Creates the radar system.
     *
     * @param engine the Ashley engine to read every live ship from
     */
    public RadarSystem(Engine engine) {
        super(LIVE_SHIP_FAMILY);
        this.engine = engine;
    }

    @Override
    public void update(float deltaTime) {
        // Refreshed once per tick, not per ship - every observer's detection check reads from
        // this same snapshot of "who's currently alive," same reasoning as TurretSystem's own
        // liveShips snapshot.
        liveShips = engine.getEntitiesFor(LIVE_SHIP_FAMILY);
        for (Entity ship : liveShips) {
            radarMapper.get(ship).tickCooldowns(deltaTime);
        }
        super.update(deltaTime);
    }

    @Override
    protected void processEntity(Entity observer, float deltaTime) {
        RadarComponent observerRadar = radarMapper.get(observer);
        observerRadar.getDetectedPlayerIds().clear();

        Body observerBody = bodyMapper.get(observer).getBody();
        ShipStats observerStats = ShipStats.forType(shipTypeMapper.get(observer).getShipType());
        boolean observerPulseActive = observerStats.isRadarPulseEnabled() && observerRadar.isPulseActive();

        for (Entity target : liveShips) {
            if (target == observer) {
                continue;
            }
            int targetPlayerId = playerIdMapper.get(target).getPlayerId();
            RadarComponent targetRadar = radarMapper.get(target);
            Body targetBody = bodyMapper.get(target).getBody();

            boolean detected = targetRadar.isPulseActive()
                || RadarDetection.detects(
                    observerBody.getPosition().x, observerBody.getPosition().y, observerBody.getAngle(),
                    targetBody.getPosition().x, targetBody.getPosition().y,
                    observerStats.isRadarBaseEnabled(), observerStats.getRadarBaseRangeMeters(),
                    observerStats.isRadarConeEnabled(), observerStats.getRadarConeRangeMeters(),
                    observerStats.getRadarConeHalfAngleDegrees(),
                    observerPulseActive, observerStats.getRadarPulseRangeMeters());

            if (detected) {
                observerRadar.getDetectedPlayerIds().add(targetPlayerId);
            }
        }
    }
}
