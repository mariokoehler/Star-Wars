package de.mkoehler.starwars.sim;

import java.util.Set;

/**
 * Pure functions for the ship-unlock system (design.md — ship unlocks): a
 * standalone, unit-testable piece of logic, the same convention as
 * {@link KillXp} and {@link ShipDamage}.
 * <p>
 * A player's unlocked ships are tracked as a growing set on their account
 * (never shrinks, never trades one unlock back for another); the XP those
 * unlocks "cost" is never actually deducted from the account's own
 * {@code xp} total (which stays a lifetime-earned figure, same spirit as
 * design.md 2.11's kills/deaths). Instead, {@link #availableXp} derives a
 * spendable balance on the fly by subtracting the summed
 * {@link ShipTypeConfig#getUnlockCostXp() cost} of every already-unlocked
 * ship from that total — so unlocking a ship is really just "the set grows,
 * and the same subtraction now includes one more term."
 */
public final class ShipUnlocks {

    private ShipUnlocks() {
    }

    /**
     * Returns whether {@code type} is flyable for a player with
     * {@code unlockedShips} — {@link ShipType#SNOWSPEEDER} always is,
     * regardless of what's in the set (design.md — every player starts with
     * it unlocked; its {@link ShipTypeConfig#getUnlockCostXp()} is never
     * consulted at all), every other type only once it's actually in the set.
     *
     * @param type          the ship type to check
     * @param unlockedShips the player's currently-unlocked ship types
     * @return {@code true} if the player may fly this ship type
     */
    public static boolean isUnlocked(ShipType type, Set<ShipType> unlockedShips) {
        return type == ShipType.SNOWSPEEDER || unlockedShips.contains(type);
    }

    /**
     * Returns a player's spendable XP balance: their total account XP minus
     * the summed unlock cost of every ship type already in
     * {@code unlockedShips} — this is what's compared against a locked
     * ship's own cost to decide whether it can be unlocked next.
     *
     * @param totalXp       the player's total (never-decremented) account XP
     * @param unlockedShips the player's currently-unlocked ship types
     * @return the player's available (spendable) XP
     */
    public static int availableXp(int totalXp, Set<ShipType> unlockedShips) {
        int spent = 0;
        for (ShipType type : unlockedShips) {
            spent += ShipStats.forType(type).getUnlockCostXp();
        }
        return totalXp - spent;
    }
}
