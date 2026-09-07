package de.mkoehler.starwars.remote;

/**
 * Tracks whichever {@link RemoteControllable} screen is currently showing —
 * at most one at a time, matching how {@link com.badlogic.gdx.Game} only
 * ever has one active {@link com.badlogic.gdx.Screen}. A screen registers
 * itself in {@code show()} and clears itself in {@code dispose()}
 * (design.md 3.13).
 * <p>
 * Read from the embedded MCP server's own thread(s) but only ever written
 * from the render thread (where every {@code show()}/{@code dispose()} call
 * happens) — {@link #active} is {@code volatile} so a read from another
 * thread always sees the latest value, without needing a full lock for what
 * is otherwise a single plain reference swap.
 */
public final class RemoteControlRegistry {

    private static volatile RemoteControllable active;

    private RemoteControlRegistry() {
    }

    /**
     * Registers {@code screen} as the currently active remote-controllable
     * screen, replacing whatever was previously registered.
     *
     * @param screen the screen that just became active
     */
    public static void setActive(RemoteControllable screen) {
        active = screen;
    }

    /**
     * Clears the active screen, but only if it's still exactly
     * {@code screen} — guards against a screen's own delayed/out-of-order
     * {@code dispose()} clearing a different screen that has since become
     * active.
     *
     * @param screen the screen that's being disposed
     */
    public static void clearIfActive(RemoteControllable screen) {
        if (active == screen) {
            active = null;
        }
    }

    /**
     * Returns the currently active remote-controllable screen, or
     * {@code null} if none is (either no screen implements
     * {@link RemoteControllable} yet, or the app hasn't started rendering
     * any screen at all).
     *
     * @return the active screen, or {@code null}
     */
    public static RemoteControllable getActive() {
        return active;
    }
}
