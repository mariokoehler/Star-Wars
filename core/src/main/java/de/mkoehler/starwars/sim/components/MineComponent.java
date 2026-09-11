package de.mkoehler.starwars.sim.components;

import com.badlogic.ashley.core.Component;

/**
 * Marks an entity as a mine (design.md — mines): its id, unique among
 * currently-active mines, broadcast so clients can track it across
 * snapshots the same way a power-up's/asteroid's id already does. Carries
 * no other state — a mine has no health of its own (it's destroyed
 * outright the instant anything touches it, not damaged incrementally)
 * and which player (if any) placed it is deliberately not tracked
 * (design.md — mines: a mine detonation credits no kill and doesn't mark
 * combat, same treatment as a wall/asteroid impact).
 */
public class MineComponent implements Component {

    private final int mineId;

    /**
     * Creates a mine component.
     *
     * @param mineId this mine's id, unique among currently-active mines
     */
    public MineComponent(int mineId) {
        this.mineId = mineId;
    }

    /**
     * Returns this mine's id.
     *
     * @return the mine id
     */
    public int getMineId() {
        return mineId;
    }
}
