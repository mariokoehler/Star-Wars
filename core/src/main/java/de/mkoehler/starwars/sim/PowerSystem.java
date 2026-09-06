package de.mkoehler.starwars.sim;

/**
 * The three systems a ship's power core output is split between (design.md
 * 2.2): {@link #SHIELDS} (faster shield regeneration), {@link #WEAPONS}
 * (faster weapon capacitor recharge, see {@link WeaponStats}), and
 * {@link #ENGINES} (more thrust and torque). See {@link PowerDistribution}
 * for the actual split and how a keypress adjusts it.
 */
public enum PowerSystem {
    SHIELDS,
    WEAPONS,
    ENGINES
}
