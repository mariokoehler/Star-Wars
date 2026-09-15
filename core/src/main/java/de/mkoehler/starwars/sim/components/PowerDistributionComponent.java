package de.mkoehler.starwars.sim.components;

import com.badlogic.ashley.core.Component;
import de.mkoehler.starwars.sim.PowerDistribution;
import de.mkoehler.starwars.sim.PowerSystem;

import java.util.EnumMap;
import java.util.Map;

/**
 * Tracks a ship's current power <em>priority</em> split (design.md 2.2) —
 * server-side authoritative state, adjusted whenever a {@code PowerAdjustMessage}
 * arrives from that player's client (see {@code GameNetworkServer}). Starts
 * every ship at the even baseline, same as a fresh {@code HullComponent}/
 * {@code ShieldComponent} starting full.
 * <p>
 * The owning client independently keeps its own priority {@link PowerDistribution}
 * in sync — for instant HUD feedback and, since the priority-based rework, only as
 * an input to computing its own local <em>effective</em> split (see below), not
 * used directly for prediction anymore — by applying the exact same pure
 * {@link PowerDistribution#adjust(PowerSystem)}/{@link PowerDistribution#reset()}
 * transition to every keypress it sends, over the reliable, ordered TCP
 * channel (see {@code PowerAdjustMessage}). Since both sides run the
 * identical deterministic function over the identical, in-order event
 * stream, the two priority copies can never actually diverge — unlike ship
 * physics, there's no reconciliation here, because this priority state has
 * no continuous floating-point/timing drift source to reconcile away.
 * <p>
 * That no-divergence guarantee does <em>not</em> extend to the <em>effective</em>
 * split ({@link #getEffectiveMultiplier(PowerSystem)}): which systems currently
 * have demand depends on continuous, server-authoritative sim state (shield
 * charge, weapon capacitor charge) the client only ever sees as a lagged
 * snapshot. {@code PowerAllocationSystem} recomputes and caches it here once
 * per tick, server-side, via {@link #setEffectiveMultipliers(Map)}; the owning
 * client does <em>not</em> recompute its own copy for anything physics-critical
 * — {@code ShipState} broadcasts the authoritative Engines/Weapons effective
 * multipliers every tick (same "predicted and authoritative physics must apply
 * identical rules" reasoning as the BOOST power-up's multiplier) for
 * {@code Client}'s local thrust/capacitor prediction to read instead. The
 * client's own locally-recomputed effective split is used only for cosmetic
 * HUD display (including Shields, which isn't broadcast since nothing
 * client-side predicts shield regen).
 * <p>
 * Engines/Weapons here are <em>never actually {@code 0}</em> even while idle —
 * {@code PowerAllocationSystem} caches {@link PowerDistribution#effectiveMultiplierIfDemanding}
 * for those two (an input-onset staleness fix, see that method's own Javadoc),
 * unlike Shields, which genuinely reads {@code 0} once full.
 */
public class PowerDistributionComponent implements Component {

    private PowerDistribution distribution = PowerDistribution.even();
    private Map<PowerSystem, Float> effectiveMultipliers = baselineMultipliers();

    private static Map<PowerSystem, Float> baselineMultipliers() {
        Map<PowerSystem, Float> multipliers = new EnumMap<>(PowerSystem.class);
        for (PowerSystem system : PowerSystem.values()) {
            // The even baseline's own multiplierFor every system is exactly 1x - matches what a
            // fresh ship (all systems genuinely idle, nothing demanding yet) should read until
            // PowerAllocationSystem's first tick actually recomputes it.
            multipliers.put(system, 1f);
        }
        return multipliers;
    }

    /**
     * Returns the current power priority split.
     *
     * @return the current distribution
     */
    public PowerDistribution getDistribution() {
        return distribution;
    }

    /**
     * Replaces this tick's cached effective multipliers — called once per
     * tick by {@code PowerAllocationSystem}, before {@code ShipControlSystem}/
     * {@code WeaponSystem}/{@code ShieldRegenSystem} read
     * {@link #getEffectiveMultiplier(PowerSystem)} for this same tick.
     *
     * @param effectiveMultipliers each system's effective multiplier for this tick
     */
    public void setEffectiveMultipliers(Map<PowerSystem, Float> effectiveMultipliers) {
        this.effectiveMultipliers = effectiveMultipliers;
    }

    /**
     * Returns a system's current effective multiplier — the redistributed
     * counterpart of {@link PowerDistribution#multiplierFor(PowerSystem)},
     * and what every consumer system ({@code ShipControlSystem},
     * {@code WeaponSystem}, {@code ShieldRegenSystem}) should actually scale
     * thrust/torque/capacitor-recharge/shield-regen by.
     *
     * @param system the system to query
     * @return the effective multiplier
     */
    public float getEffectiveMultiplier(PowerSystem system) {
        return effectiveMultipliers.get(system);
    }

    /**
     * Shifts power toward {@code target}, per {@link PowerDistribution#adjust(PowerSystem)}.
     *
     * @param target the system to shift power toward
     */
    public void adjust(PowerSystem target) {
        distribution = distribution.adjust(target);
    }

    /**
     * Jumps straight to the theoretical maximum for {@code target}, per
     * {@link PowerDistribution#maximize(PowerSystem)}.
     *
     * @param target the system to max out
     */
    public void maximize(PowerSystem target) {
        distribution = distribution.maximize(target);
    }

    /**
     * Resets the power split to the even baseline.
     */
    public void reset() {
        distribution = distribution.reset();
    }
}
