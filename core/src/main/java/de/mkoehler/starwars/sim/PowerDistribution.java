package de.mkoehler.starwars.sim;

import java.util.EnumMap;
import java.util.Map;

/**
 * A ship's current power split across {@link PowerSystem#SHIELDS},
 * {@link PowerSystem#WEAPONS} and {@link PowerSystem#ENGINES} (design.md
 * 2.2) — immutable, so every adjustment ({@link #adjust(PowerSystem)},
 * {@link #reset()}) returns a new instance rather than mutating in place.
 * <p>
 * Deliberately pure and libGDX-free, so it's directly unit-testable and safe
 * to run identically on both the server (authoritative) and the owning
 * client (mirrored locally for instant HUD feedback and to scale local
 * engine-thrust prediction the same way the server will) — see
 * {@link de.mkoehler.starwars.sim.components.PowerDistributionComponent}'s
 * Javadoc for why the two copies never actually diverge.
 */
public final class PowerDistribution {

    /** Every system's default share when a ship spawns or the reset keybind is pressed. */
    public static final float BASELINE_FRACTION = 1f / 3f;
    /** No system may ever hold less than this fraction of total output (design.md 2.2). */
    public static final float FLOOR_FRACTION = 0.10f;

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
