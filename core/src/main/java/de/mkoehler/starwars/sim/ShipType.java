package de.mkoehler.starwars.sim;

/**
 * The distinct player-flyable ship types in the game. Each value is tied to
 * a {@code shipdata/<resourceName>.*} pair of classpath resources — a
 * {@code .stats.json} (balance/tuning numbers and HUD layout, see
 * {@link ShipTypeConfig}) and, optionally, a {@code .meta.json} (hitbox
 * polygon and attachment points, see
 * {@link de.mkoehler.starwars.sim.metadata.ShipSpriteMetadata}) — loaded by
 * {@link ShipStats}. Also the identifier the Ship Selection screen
 * (design.md 5.1) cycles through, using each type's {@code resourceName} to
 * find its packed sprite/portrait region (e.g. {@code "falcon/portrait"} in
 * {@code ships.atlas}) and its {@code textures/menu.atlas} description image.
 * <p>
 * **Only {@link #XWING} has a {@code .stats.json} authored yet** — the other
 * five have sprite art imported (2026-09-05) but no balance numbers, and
 * {@link ShipStats#forType} will throw if called for them before one exists.
 * The Ship Selection screen only needs each type's portrait/description
 * art, not its {@code ShipStats}, so this doesn't block it — but it does
 * mean starting a match currently still always flies the X-wing regardless
 * of which ship is shown selected, until per-ship stats (and non-square
 * sprite rendering support, needed for the Star Destroyer) exist.
 */
public enum ShipType {

    XWING("xwing"),
    FALCON("falcon"),
    SNOWSPEEDER("snowspeeder"),
    STARDESTROYER("stardestroyer"),
    TIEFIGHTER("tiefighter"),
    TIEINTERCEPTOR("tieinterceptor");

    private final String resourceName;

    ShipType(String resourceName) {
        this.resourceName = resourceName;
    }

    /**
     * Returns the base name used to derive this ship type's classpath
     * resource paths, e.g. {@code "xwing"} for
     * {@code shipdata/xwing.stats.json} and
     * {@code textures/hud/xwing_hull.png}.
     *
     * @return the resource base name
     */
    public String getResourceName() {
        return resourceName;
    }
}
