package de.mkoehler.starwars.sim;

/**
 * Computes the XP a kill is worth (design.md — kill XP), as a pure function
 * of the killed and killing ships' {@link ShipType#getTier() tiers}: a
 * standalone, unit-testable piece of logic, the same convention as
 * {@link ShipDamage} and the power-distribution clamping algorithm.
 * <p>
 * {@code XP = BASE_XP * tierMultiplier * rankDisparityMultiplier}, where
 * {@code tierMultiplier} is the killed ship's tier (a bigger ship is worth
 * more) and {@code rankDisparityMultiplier} is the killed ship's tier
 * divided by the killer's (rewarding an underdog kill, penalizing a
 * lopsided one) — e.g. a tier-1 Snowspeeder downing a tier-4 Star
 * Destroyer earns {@code 10 * 4 * (4/1) = 160} XP, while the reverse earns
 * {@code 10 * 1 * (1/4) = 2.5} XP (rounded to the nearest whole XP).
 */
public final class KillXp {

    /** XP a same-tier kill (tier 1 vs. tier 1) is worth, before any tier weighting. */
    public static final int BASE_XP = 10;

    private KillXp() {
    }

    /**
     * Computes the XP a kill is worth.
     *
     * @param killedShipType  the ship type that was destroyed
     * @param killerShipType  the ship type of whoever destroyed it
     * @return the XP earned, rounded to the nearest whole number
     */
    public static int calculate(ShipType killedShipType, ShipType killerShipType) {
        int killedTier = killedShipType.getTier();
        int killerTier = killerShipType.getTier();
        float tierMultiplier = killedTier;
        float rankDisparityMultiplier = (float) killedTier / killerTier;
        return Math.round(BASE_XP * tierMultiplier * rankDisparityMultiplier);
    }
}
