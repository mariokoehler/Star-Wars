package de.mkoehler.starwars.sim;

/**
 * The distinct player-flyable ship types in the game. Each value is tied to
 * a {@code shipdata/<resourceName>.*} pair of classpath resources — a
 * {@code .stats.json} (balance/tuning numbers and HUD layout, see
 * {@link ShipTypeConfig}) and, optionally, a {@code .meta.json} (hitbox
 * polygon and attachment points, see
 * {@link de.mkoehler.starwars.sim.metadata.ShipSpriteMetadata}) — loaded by
 * {@link ShipStats}.
 * <p>
 * A single value for now, standing in for the still-open "ship roster" TODO
 * (design.md 6) — introduced ahead of a second ship type existing so that
 * ship-type-keyed data (stats, sprite metadata, HUD art) has one shared
 * identifier to hang off of, rather than every consumer inventing its own
 * per-ship lookup.
 */
public enum ShipType {

    XWING("xwing");

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
