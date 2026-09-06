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
 * Every value here needs a matching {@code case} in both
 * {@code Client.hullRegionName} and
 * {@code ShipSelectionScreen.descriptionRegionName} — both are exhaustive
 * switches over this enum specifically so the compiler catches a forgotten
 * one when a new ship type is added, rather than it silently rendering
 * nothing.
 */
public enum ShipType {

    XWING("xwing"),
    FALCON("falcon"),
    SNOWSPEEDER("snowspeeder"),
    STARDESTROYER("stardestroyer"),
    TIEFIGHTER("tiefighter"),
    TIEINTERCEPTOR("tieinterceptor"),
    AWING("awing");

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
