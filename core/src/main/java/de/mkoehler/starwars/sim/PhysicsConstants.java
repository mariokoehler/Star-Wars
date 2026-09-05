package de.mkoehler.starwars.sim;

/**
 * Fixed values relating the Box2D simulation (which works in meters) to the
 * pixel-space sprites drawn on screen.
 */
public final class PhysicsConstants {

    /**
     * Conversion factor between Box2D meters and screen pixels. Proposed
     * default, not yet tuned against a real scene; see design.md 3.3.
     */
    public static final float PIXELS_PER_METER = 32f;

    /**
     * Fixed timestep, in seconds, used to step the Box2D world. Stepping at a
     * fixed rate rather than the variable frame delta keeps the simulation
     * deterministic, which matters once it needs to be replayed/reconciled
     * over the network.
     */
    public static final float TIME_STEP = 1f / 60f;

    /**
     * Maximum number of fixed steps to run in a single {@code render()} call,
     * to avoid a "spiral of death" if a frame takes unusually long.
     */
    public static final int MAX_STEPS_PER_FRAME = 5;

    private PhysicsConstants() {
    }
}
