package de.mkoehler.starwars.sim.components;

import com.badlogic.ashley.core.Component;
import de.mkoehler.starwars.sim.PowerDistribution;
import de.mkoehler.starwars.sim.PowerSystem;

/**
 * Tracks a ship's current power split (design.md 2.2) — server-side
 * authoritative state, adjusted whenever a {@code PowerAdjustMessage}
 * arrives from that player's client (see {@code GameNetworkServer}).
 * Starts every ship at the even baseline, same as a fresh {@code HullComponent}/
 * {@code ShieldComponent} starting full.
 * <p>
 * The owning client independently keeps its own {@link PowerDistribution} in
 * sync — for instant HUD feedback and to scale its local engine-thrust
 * prediction identically to the server — by applying the exact same pure
 * {@link PowerDistribution#adjust(PowerSystem)}/{@link PowerDistribution#reset()}
 * transition to every keypress it sends, over the reliable, ordered TCP
 * channel (see {@code PowerAdjustMessage}). Since both sides run the
 * identical deterministic function over the identical, in-order event
 * stream, the two copies can never actually diverge — unlike ship physics,
 * there's no reconciliation here, because this state has no continuous
 * floating-point/timing drift source to reconcile away.
 */
public class PowerDistributionComponent implements Component {

    private PowerDistribution distribution = PowerDistribution.even();

    /**
     * Returns the current power split.
     *
     * @return the current distribution
     */
    public PowerDistribution getDistribution() {
        return distribution;
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
