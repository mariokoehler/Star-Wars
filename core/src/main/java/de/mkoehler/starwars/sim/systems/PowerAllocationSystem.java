package de.mkoehler.starwars.sim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import de.mkoehler.starwars.sim.PowerDistribution;
import de.mkoehler.starwars.sim.PowerSystem;
import de.mkoehler.starwars.sim.components.NetworkInputComponent;
import de.mkoehler.starwars.sim.components.PowerDistributionComponent;
import de.mkoehler.starwars.sim.components.ShieldComponent;
import de.mkoehler.starwars.sim.components.WeaponComponent;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/**
 * Once per tick, decides which of a ship's three power systems currently
 * have genuine demand (design.md 2.2's priority-based rework) and caches the
 * resulting effective multipliers on that ship's {@link PowerDistributionComponent}
 * ({@link PowerDistributionComponent#setEffectiveMultipliers}), for
 * {@link ShipControlSystem}/{@link WeaponSystem}/{@link ShieldRegenSystem} to
 * read afterward this same tick:
 * <ul>
 *     <li>{@link PowerSystem#ENGINES} — demanding while thrusting or turning this tick.</li>
 *     <li>{@link PowerSystem#SHIELDS} — demanding while current shield strength is below max.</li>
 *     <li>{@link PowerSystem#WEAPONS} — demanding while the weapon capacitor is below max charge.</li>
 * </ul>
 * A system with no demand frees its priority share for the other two to draw
 * on instead (see {@link PowerDistribution#effectiveFractions}), rather than
 * that share going to waste.
 * <p>
 * Engines and Weapons are cached via {@link PowerDistribution#effectiveMultiplierIfDemanding} —
 * <em>as if</em> currently demanding, regardless of whether they genuinely are this tick — not
 * plain {@link PowerDistribution#effectiveFractions}; see that method's own Javadoc for why a
 * genuine {@code 0} there would desync client-side thrust/capacitor prediction from real input
 * onset. Shields has no such onset and is cached from the genuine demand set, reading a real
 * {@code 0} once full.
 * <p>
 * Runs server-side, right after this tick's input (real players' and NPCs')
 * has been applied and before {@code PhysicsSystem}'s stepping loop first
 * consumes the Engines share. Reads Shields/Weapons demand as of the start
 * of the tick — before this same tick's hit resolution/capacitor draw can
 * change it — a deliberate one-tick lag, same shape as {@code WeaponSystem}'s
 * own recharge-then-drain ordering within a single tick.
 */
public class PowerAllocationSystem extends IteratingSystem {

    private final ComponentMapper<PowerDistributionComponent> powerMapper = ComponentMapper.getFor(PowerDistributionComponent.class);
    private final ComponentMapper<NetworkInputComponent> inputMapper = ComponentMapper.getFor(NetworkInputComponent.class);
    private final ComponentMapper<ShieldComponent> shieldMapper = ComponentMapper.getFor(ShieldComponent.class);
    private final ComponentMapper<WeaponComponent> weaponMapper = ComponentMapper.getFor(WeaponComponent.class);

    /**
     * Creates the power allocation system.
     */
    public PowerAllocationSystem() {
        super(Family.all(PowerDistributionComponent.class, NetworkInputComponent.class,
            ShieldComponent.class, WeaponComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        NetworkInputComponent input = inputMapper.get(entity);
        ShieldComponent shield = shieldMapper.get(entity);
        WeaponComponent weapon = weaponMapper.get(entity);
        PowerDistributionComponent power = powerMapper.get(entity);
        PowerDistribution distribution = power.getDistribution();

        EnumSet<PowerSystem> demanding = EnumSet.noneOf(PowerSystem.class);
        if (input.isThrustForward() || input.isTurnLeft() || input.isTurnRight()) {
            demanding.add(PowerSystem.ENGINES);
        }
        if (shield.getCurrent() < shield.getMax()) {
            demanding.add(PowerSystem.SHIELDS);
        }
        if (weapon.getCurrentCharge() < weapon.getStats().getCapacitorMaxCharge()) {
            demanding.add(PowerSystem.WEAPONS);
        }

        Map<PowerSystem, Float> effectiveMultipliers = new EnumMap<>(PowerSystem.class);
        effectiveMultipliers.put(PowerSystem.SHIELDS,
            distribution.effectiveFractions(demanding).get(PowerSystem.SHIELDS) / PowerDistribution.BASELINE_FRACTION);
        effectiveMultipliers.put(PowerSystem.ENGINES,
            distribution.effectiveMultiplierIfDemanding(PowerSystem.ENGINES, demanding));
        effectiveMultipliers.put(PowerSystem.WEAPONS,
            distribution.effectiveMultiplierIfDemanding(PowerSystem.WEAPONS, demanding));

        power.setEffectiveMultipliers(effectiveMultipliers);
    }
}
