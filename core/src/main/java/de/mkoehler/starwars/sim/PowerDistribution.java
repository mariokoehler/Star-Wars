package de.mkoehler.starwars.sim;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * A ship's current power <em>priority</em> split across {@link PowerSystem#SHIELDS},
 * {@link PowerSystem#WEAPONS} and {@link PowerSystem#ENGINES} (design.md
 * 2.2) — immutable, so every adjustment ({@link #adjust(PowerSystem)},
 * {@link #reset()}) returns a new instance rather than mutating in place.
 * <p>
 * Since design.md 2.2's priority-based rework, a system's raw
 * {@link #getFraction(PowerSystem)}/{@link #multiplierFor(PowerSystem)} is
 * only what it's <em>entitled to</em>, not what it actually receives — a
 * system with no current demand (idle engines, a full shield, a full weapon
 * capacitor) frees its share for the other two, in priority order. See
 * {@link #effectiveFractions(Set)} for that redistribution; every ship's
 * three consumer systems ({@code ShipControlSystem}, {@code WeaponSystem},
 * {@code ShieldRegenSystem}) read the redistributed value, cached each tick
 * on {@link de.mkoehler.starwars.sim.components.PowerDistributionComponent},
 * not this class's own raw fraction directly.
 * <p>
 * Deliberately pure and libGDX-free, so it's directly unit-testable and safe
 * to run identically on both the server (authoritative) and the owning
 * client (mirrored locally for instant HUD feedback and to scale local
 * engine-thrust prediction the same way the server will) — see
 * {@link de.mkoehler.starwars.sim.components.PowerDistributionComponent}'s
 * Javadoc for why the two priority copies never actually diverge, and why
 * the redistributed/effective value is a separate story.
 */
public final class PowerDistribution {

    /** Every system's default share when a ship spawns or the reset keybind is pressed. */
    public static final float BASELINE_FRACTION = 1f / 3f;
    /** No system may ever hold less than this fraction of total output (design.md 2.2). */
    public static final float FLOOR_FRACTION = 0.10f;
    /**
     * The highest fraction redistribution can ever push a single demanding system to —
     * {@link #maximize(PowerSystem)}'s own ceiling (leaving the other two at the floor), reused
     * here so idle-power reuse can never out-earn committing to a system outright (design.md 2.2).
     */
    public static final float REDISTRIBUTION_CAP_FRACTION = 1f - 2f * FLOOR_FRACTION;

    private static final float INCREMENT_FRACTION = 0.05f;
    private static final float DECREMENT_FRACTION = 0.025f;
    // Absorbs float rounding from repeated adjustments so a value that should be exactly at the
    // floor (e.g. after several presses) doesn't spuriously fail a ">= FLOOR_FRACTION" check.
    private static final float EPSILON = 1e-4f;

    private final Map<PowerSystem, Float> fractions;

    private PowerDistribution(Map<PowerSystem, Float> fractions) {
        this.fractions = fractions;
    }

    /**
     * Returns the even baseline split: {@link #BASELINE_FRACTION} to every system.
     *
     * @return the baseline distribution
     */
    public static PowerDistribution even() {
        Map<PowerSystem, Float> fractions = new EnumMap<>(PowerSystem.class);
        for (PowerSystem system : PowerSystem.values()) {
            fractions.put(system, BASELINE_FRACTION);
        }
        return new PowerDistribution(fractions);
    }

    /**
     * Returns a system's current share of total power output.
     *
     * @param system the system to query
     * @return the fraction, always {@code >= FLOOR_FRACTION}
     */
    public float getFraction(PowerSystem system) {
        return fractions.get(system);
    }

    /**
     * Returns a system's current effect multiplier relative to the even
     * baseline (1.0 at baseline, scaling linearly with its fraction) — e.g.
     * {@link de.mkoehler.starwars.sim.systems.ShipControlSystem} multiplies
     * thrust/torque by {@link PowerSystem#ENGINES}'s multiplier,
     * {@code ShieldRegenSystem} multiplies shield regen by
     * {@link PowerSystem#SHIELDS}'s. design.md 2.2 doesn't specify a formula
     * for this — this linear one was the agreed default (see CLAUDE.md).
     *
     * @param system the system to query
     * @return the multiplier, {@code fraction / BASELINE_FRACTION}
     */
    public float multiplierFor(PowerSystem system) {
        return getFraction(system) / BASELINE_FRACTION;
    }

    /**
     * Redistributes this tick's power among only the systems that currently
     * have genuine demand (design.md 2.2's priority-based rework), renormalizing
     * their priority fractions so they sum to the whole output instead of just
     * their own slice — a demanding system's effective fraction is always
     * {@code >=} its raw {@link #getFraction(PowerSystem)}, and a non-demanding
     * one's is always {@code 0}. Water-fills: no single system's effective
     * fraction ever exceeds {@link #REDISTRIBUTION_CAP_FRACTION} (the same
     * ceiling committing to it outright via {@link #maximize(PowerSystem)}
     * would give it) — power that would have pushed it past that cap instead
     * flows on to whichever other demanding system(s) can still use it, and
     * only goes fully unused if none can (e.g. a single demanding system whose
     * priority share alone already exceeds the cap).
     * <p>
     * Pure function of this distribution's priorities and the given demand
     * set — callers (a new demand snapshot is computed once per tick,
     * server-side) decide what counts as "demanding" for each system; see
     * {@code PowerAllocationSystem}.
     *
     * @param demanding the systems that currently need power; empty is valid
     *                  (returns every system at {@code 0})
     * @return each system's effective fraction of this tick's output, summing
     * to at most {@code 1} (less than {@code 1} only when capping left some
     * power with no demanding system left to absorb it)
     */
    public Map<PowerSystem, Float> effectiveFractions(Set<PowerSystem> demanding) {
        Map<PowerSystem, Float> effective = new EnumMap<>(PowerSystem.class);
        for (PowerSystem system : PowerSystem.values()) {
            effective.put(system, 0f);
        }
        if (demanding.isEmpty()) {
            return effective;
        }

        Set<PowerSystem> remaining = EnumSet.copyOf(demanding);
        float pool = 1f;
        // At most one system can be capped-and-removed per pass, so this converges in at most
        // as many passes as there are demanding systems (3, worst case).
        while (!remaining.isEmpty()) {
            float remainingPriority = 0f;
            for (PowerSystem system : remaining) {
                remainingPriority += getFraction(system);
            }

            PowerSystem overCapped = null;
            for (PowerSystem system : remaining) {
                float share = pool * (getFraction(system) / remainingPriority);
                if (share > REDISTRIBUTION_CAP_FRACTION + EPSILON) {
                    overCapped = system;
                    break;
                }
            }

            if (overCapped == null) {
                for (PowerSystem system : remaining) {
                    effective.put(system, pool * (getFraction(system) / remainingPriority));
                }
                break;
            }

            effective.put(overCapped, REDISTRIBUTION_CAP_FRACTION);
            pool -= REDISTRIBUTION_CAP_FRACTION;
            remaining.remove(overCapped);
        }

        return effective;
    }

    /**
     * Convenience over {@link #effectiveFractions(Set)} for a single system,
     * forcing it into the demand set regardless of whether it's genuinely
     * demanding right now — "what would my multiplier be at this instant if
     * I needed power" rather than "what is it." This is what
     * {@code PowerAllocationSystem} actually caches for {@link PowerSystem#ENGINES}/
     * {@link PowerSystem#WEAPONS} (never {@link PowerSystem#SHIELDS} — see below),
     * for both server-side consumption and the client-broadcast value.
     * <p>
     * The reason: Engines/Weapons demand has a player-input-driven <em>onset</em>
     * (pressing thrust/turn; firing while the capacitor isn't already full) that
     * genuinely gating this multiplier at {@code 0} until the <em>next</em> tick's
     * demand snapshot would desync from — the very first physics step of a fresh
     * thrust/turn/shot would predict zero effect purely because the previous
     * tick's snapshot (still {@code 0}, correctly, from a moment ago) hadn't
     * caught up yet. That's the same "flying in slow motion" bug class this
     * project's tick-ordering notes already warn about elsewhere, just relocated
     * from force-reapplication timing to power-multiplier staleness.
     * <p>
     * Safe precisely because the two real gates already live elsewhere:
     * {@code ShipControlSystem#applyInput} only applies force at all while input
     * is actually held (so a non-zero multiplier sitting unused while idle costs
     * nothing), and {@code WeaponComponent#rechargeCapacitor}'s own
     * {@code Math.min} clamp makes a non-zero rate applied to an already-full
     * capacitor a no-op. And when {@code system} genuinely <em>is</em> already in
     * {@code otherwiseDemanding}, forcing it in again is a no-op too, so this
     * exactly matches {@link #effectiveFractions(Set)}'s own answer in that case.
     * {@link PowerSystem#SHIELDS} has no such input-driven onset (nothing the
     * player presses starts shield regen; it's purely "current < max") and should
     * keep reading a genuine {@code 0} once full — read {@link #effectiveFractions(Set)}
     * directly for it instead.
     *
     * @param system             the system to force into demand, and to read back
     * @param otherwiseDemanding the <em>other</em> systems' genuine current demand
     *                           (harmless to pass a set that already contains {@code system})
     * @return {@code system}'s effective multiplier, as if it were currently demanding power
     */
    public float effectiveMultiplierIfDemanding(PowerSystem system, Set<PowerSystem> otherwiseDemanding) {
        EnumSet<PowerSystem> demanding = otherwiseDemanding.isEmpty()
            ? EnumSet.noneOf(PowerSystem.class) : EnumSet.copyOf(otherwiseDemanding);
        demanding.add(system);
        return effectiveFractions(demanding).get(system) / BASELINE_FRACTION;
    }

    /**
     * Applies one keypress worth of adjustment toward {@code target}, per
     * design.md 2.2's clamping algorithm:
     * <ol>
     *     <li><b>Normal case:</b> {@code target} gains
     *     {@value #INCREMENT_FRACTION} and each of the other two systems
     *     loses {@value #DECREMENT_FRACTION}, if both can afford that
     *     without dropping below {@link #FLOOR_FRACTION}.</li>
     *     <li><b>Redirect case:</b> if exactly one of the other two would
     *     drop below the floor under the normal split but the other can
     *     absorb the full {@value #INCREMENT_FRACTION} and stay at/above
     *     it, take the whole amount from that one instead, leaving the
     *     constrained system unchanged.</li>
     *     <li><b>No-op case:</b> if neither option keeps every system at or
     *     above the floor, the keypress has no effect.</li>
     * </ol>
     *
     * @param target the system to shift power toward
     * @return the resulting distribution — a new instance, or this same
     * instance unchanged in the no-op case
     */
    public PowerDistribution adjust(PowerSystem target) {
        PowerSystem[] others = otherTwo(target);
        PowerSystem b = others[0];
        PowerSystem c = others[1];

        float bNormal = getFraction(b) - DECREMENT_FRACTION;
        float cNormal = getFraction(c) - DECREMENT_FRACTION;
        if (isAtOrAboveFloor(bNormal) && isAtOrAboveFloor(cNormal)) {
            return with(target, getFraction(target) + INCREMENT_FRACTION, b, bNormal, c, cNormal);
        }

        boolean bViolates = !isAtOrAboveFloor(bNormal);
        boolean cViolates = !isAtOrAboveFloor(cNormal);
        if (bViolates != cViolates) {
            PowerSystem constrained = bViolates ? b : c;
            PowerSystem absorbing = bViolates ? c : b;
            float absorbingAfterFullShift = getFraction(absorbing) - INCREMENT_FRACTION;
            if (isAtOrAboveFloor(absorbingAfterFullShift)) {
                return with(target, getFraction(target) + INCREMENT_FRACTION,
                    constrained, getFraction(constrained), absorbing, absorbingAfterFullShift);
            }
        }

        return this; // neither the normal split nor a redirect keeps every system at/above the floor
    }

    /**
     * Returns the even baseline split, as if the reset keybind was pressed.
     *
     * @return {@link #even()}
     */
    public PowerDistribution reset() {
        return even();
    }

    /**
     * Jumps straight to the theoretical maximum for {@code target} —
     * {@code target} at {@code 1 - 2 * FLOOR_FRACTION} (as high as it can
     * go while leaving the other two at exactly the floor) and both other
     * systems at {@link #FLOOR_FRACTION}. Unlike {@link #adjust(PowerSystem)},
     * this is an absolute jump, not bound by the per-keypress increment
     * algorithm — it always succeeds regardless of the current split, the
     * same way {@link #reset()} unconditionally returns to baseline. Driven
     * by holding a power-distribution keybind for a moment (design.md 2.2)
     * rather than tapping it repeatedly.
     *
     * @param target the system to max out
     * @return the resulting distribution
     */
    public PowerDistribution maximize(PowerSystem target) {
        Map<PowerSystem, Float> next = new EnumMap<>(PowerSystem.class);
        for (PowerSystem system : PowerSystem.values()) {
            next.put(system, system == target ? 1f - 2f * FLOOR_FRACTION : FLOOR_FRACTION);
        }
        return new PowerDistribution(next);
    }

    private static boolean isAtOrAboveFloor(float value) {
        return value >= FLOOR_FRACTION - EPSILON;
    }

    private static PowerSystem[] otherTwo(PowerSystem target) {
        PowerSystem[] others = new PowerSystem[2];
        int i = 0;
        for (PowerSystem system : PowerSystem.values()) {
            if (system != target) {
                others[i++] = system;
            }
        }
        return others;
    }

    private PowerDistribution with(PowerSystem s1, float v1, PowerSystem s2, float v2, PowerSystem s3, float v3) {
        Map<PowerSystem, Float> next = new EnumMap<>(fractions);
        next.put(s1, v1);
        next.put(s2, v2);
        next.put(s3, v3);
        return new PowerDistribution(next);
    }
}
