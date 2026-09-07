package de.mkoehler.starwars.sim;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The two faction unlock branches from design.md's "Ship Tree" idea, now
 * actually enforced: every non-{@link ShipType#SNOWSPEEDER} ship has exactly
 * one prerequisite — the previous ship in its own branch — that must already
 * be unlocked before this one can be, on top of the XP affordability check
 * in {@link ShipUnlocks}.
 * <p>
 * <b>Imperial:</b> {@link ShipType#TIEFIGHTER} &rarr; {@link ShipType#TIEINTERCEPTOR}
 * &rarr; {@link ShipType#STARDESTROYER}.<br>
 * <b>Rebel:</b> {@link ShipType#AWING} &rarr; {@link ShipType#XWING} &rarr;
 * {@link ShipType#FALCON}.<br>
 * {@link ShipType#SNOWSPEEDER} has no prerequisite — it's always unlocked
 * (see {@link ShipUnlocks#isUnlocked}) and isn't part of either branch.
 * <p>
 * Both branches climb the same {@link ShipType#getTier() tiers} (2 &rarr; 3
 * &rarr; 4) in lockstep, so the tree is expressible purely as this fixed
 * prerequisite table, with no separate per-branch/tier bookkeeping needed.
 * Nothing here is account- or account-store-specific — it's pure topology,
 * usable identically by the client (which padlock to show) and the server
 * (the actual authoritative check), same split as {@link ShipUnlocks}.
 */
public final class ShipTree {

    private static final Map<ShipType, ShipType> PREREQUISITE = new EnumMap<>(ShipType.class);

    static {
        PREREQUISITE.put(ShipType.TIEINTERCEPTOR, ShipType.TIEFIGHTER);
        PREREQUISITE.put(ShipType.STARDESTROYER, ShipType.TIEINTERCEPTOR);
        PREREQUISITE.put(ShipType.XWING, ShipType.AWING);
        PREREQUISITE.put(ShipType.FALCON, ShipType.XWING);
    }

    private ShipTree() {
    }

    /**
     * Returns {@code type}'s prerequisite ship — the previous ship in its
     * branch that must already be unlocked before {@code type} can be — or
     * empty if it has none (every tier-2 ship, and {@link ShipType#SNOWSPEEDER}).
     *
     * @param type the ship type to look up
     * @return the prerequisite ship type, if any
     */
    public static Optional<ShipType> prerequisiteOf(ShipType type) {
        return Optional.ofNullable(PREREQUISITE.get(type));
    }

    /**
     * Returns whether {@code type}'s prerequisite, if it has one, is already
     * in {@code unlockedShips} — {@code true} unconditionally for a ship
     * with no prerequisite. Does not consider XP affordability at all; see
     * {@link ShipUnlocks#availableXp} for that separate check.
     *
     * @param type          the ship type to check
     * @param unlockedShips the account's currently unlocked ship types
     * @return {@code true} if the tree itself doesn't block unlocking {@code type}
     */
    public static boolean prerequisiteMet(ShipType type, Set<ShipType> unlockedShips) {
        return prerequisiteOf(type).map(prerequisite -> ShipUnlocks.isUnlocked(prerequisite, unlockedShips)).orElse(true);
    }
}
