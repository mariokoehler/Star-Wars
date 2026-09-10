package de.mkoehler.starwars.sim;

import de.mkoehler.starwars.sim.components.HullComponent;
import de.mkoehler.starwars.sim.components.ShieldComponent;

/**
 * Splits a hit's damage between a ship's shield and hull (design.md 2.5).
 * <p>
 * The shield absorbs a share of the damage equal to its current fraction of
 * capacity — at 100% shield, it takes the full hit; at 90%, it takes 90%
 * and the remaining 10% bleeds straight through to the hull; at 0%, the
 * hull takes it all. This is a pure function of the shield/hull state at
 * the moment of the hit, with no dependency on the order hits are applied
 * in — extracted out of {@code GameNetworkServer} specifically so it's a
 * standalone, unit-testable piece of logic (design.md/CLAUDE.md's
 * "core, logic-heavy components should have tests" convention), the same
 * way the power-distribution clamping algorithm is expected to be.
 */
public final class ShipDamage {

    private ShipDamage() {
    }

    /**
     * Applies one hit's damage to a ship's shield and hull.
     * <p>
     * If the shield's designated share exceeds what's actually left of it
     * (only possible once the shield is nearly depleted), the excess isn't
     * absorbed for free — it bleeds through to the hull on top of the
     * hull's own designated share, so a hit is never partially "lost" to a
     * shield that can't fully pay its portion. This overflow rule isn't
     * separately specified by the shield model above, just the natural
     * reading of "proportional split" that doesn't let damage vanish.
     *
     * @param shield the ship's shield
     * @param hull   the ship's hull
     * @param damage the damage dealt by the hit
     */
    public static void apply(ShieldComponent shield, HullComponent hull, float damage) {
        float shieldFraction = shield.getMax() > 0f ? shield.getCurrent() / shield.getMax() : 0f;
        float shieldPortion = damage * shieldFraction;
        float hullPortion = damage - shieldPortion;

        float shieldOverflow = Math.max(0f, shieldPortion - shield.getCurrent());

        shield.damage(shieldPortion);
        hull.damage(hullPortion + shieldOverflow);
    }

    /**
     * Applies the same total damage as {@link #apply}, but as
     * {@code chunkCount} equal sub-hits in sequence instead of one lump sum
     * (design.md — missiles' damage-application addendum). Because the
     * shield/hull split is recomputed fresh from the shield's *current*
     * fraction before each sub-hit, splitting a hit up front-loads real hull
     * damage sooner than a single application of the same total damage
     * would: at full shield, one lump 100-damage hit into a 100/100 ship
     * leaves the hull completely untouched (the shield's 100% fraction
     * absorbs it all in one step); the identical 100 damage applied as ten
     * 10-damage sub-hits leaves the ship at roughly 35 shield / 65 hull,
     * since the shield fraction — and therefore how much of each subsequent
     * sub-hit still gets absorbed — drops a little after every sub-hit.
     * {@code chunkCount <= 1} degrades to a single call to {@link #apply}
     * with the full {@code totalDamage} (no pointless loop, and no
     * divide-by-zero for a misconfigured {@code chunkCount} of 0).
     *
     * @param shield      the ship's shield
     * @param hull        the ship's hull
     * @param totalDamage the total damage dealt by the hit, split evenly
     *                    across every sub-hit
     * @param chunkCount  how many equal sub-hits to split {@code totalDamage} into
     */
    public static void applyChunked(ShieldComponent shield, HullComponent hull, float totalDamage, int chunkCount) {
        if (chunkCount <= 1) {
            apply(shield, hull, totalDamage);
            return;
        }
        float perChunk = totalDamage / chunkCount;
        for (int i = 0; i < chunkCount; i++) {
            apply(shield, hull, perChunk);
        }
    }
}
