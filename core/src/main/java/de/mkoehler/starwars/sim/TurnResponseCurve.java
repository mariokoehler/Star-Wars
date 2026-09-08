package de.mkoehler.starwars.sim;

/**
 * A power-law curve (design.md 2.2's addendum) applied to the Engines power
 * multiplier before it scales turn torque — thrust always stays on the
 * plain linear multiplier {@link PowerDistribution#multiplierFor} already
 * provides; only turn torque goes through this. No Ashley/Box2D dependency,
 * so it's directly unit-testable, matching this project's convention of
 * keeping logic-heavy pure functions separate from system wiring (see
 * {@code TurretAiming}/{@code RadarDetection}/{@link PowerDistribution}
 * itself for the same split) — kept as its own small class rather than a
 * method on {@link PowerDistribution}, since that class represents the
 * 3-way power split itself and has no reason to know about a torque-specific
 * curve layered on top of it.
 * <p>
 * {@code exponent = 1.0} reproduces the plain linear multiplier unchanged —
 * every ship type defaults to this. {@code exponent < 1.0} gives
 * diminishing returns at both extremes (a direct consequence of a power-law
 * curve always evaluating to exactly {@code 1.0} at the baseline
 * multiplier, regardless of the exponent): a light ship's "ridiculously"
 * agile turning with Engines maxed out gets tamed, at the cost of also
 * softening how punishing a starved-Engines split is. A piecewise
 * "only compress above baseline" alternative was considered and rejected —
 * see design.md 2.2's addendum for the full reasoning.
 */
public final class TurnResponseCurve {

    private TurnResponseCurve() {
    }

    /**
     * Applies the curve.
     *
     * @param linearEnginesMultiplier the plain linear Engines power
     *                                multiplier ({@link PowerDistribution#multiplierFor})
     * @param exponent                this ship type's own
     *                                {@link ShipTypeConfig#getEngineTurnResponseExponent()}
     * @return the curved multiplier to scale turn torque by
     */
    public static float apply(float linearEnginesMultiplier, float exponent) {
        return (float) Math.pow(linearEnginesMultiplier, exponent);
    }
}
