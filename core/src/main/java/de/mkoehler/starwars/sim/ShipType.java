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
 * <p>
 * Also carries a {@link #getTier() progression tier} (design.md — kill XP,
 * §7's "Ship Tree" idea): the faction-neutral {@link #SNOWSPEEDER} is tier
 * 1, then each faction branch climbs tiers 2-4 in lockstep
 * ({@link #TIEFIGHTER}/{@link #AWING} at 2, {@link #XWING}/
 * {@link #TIEINTERCEPTOR} at 3, {@link #STARDESTROYER}/{@link #FALCON} at
 * 4) — used to weigh kill XP by how outmatched (or not) the killer was.
 */
public enum ShipType {

    XWING("xwing", 3),
    FALCON("falcon", 4),
    SNOWSPEEDER("snowspeeder", 1),
    STARDESTROYER("stardestroyer", 4),
    TIEFIGHTER("tiefighter", 2),
    TIEINTERCEPTOR("tieinterceptor", 3),
    AWING("awing", 2);

    private final String resourceName;
    private final int tier;

    ShipType(String resourceName, int tier) {
        this.resourceName = resourceName;
        this.tier = tier;
    }

    /**
     * Returns a human-readable name for this ship type (e.g.
     * {@code "TIE Fighter"}), used in the Ship Selection screen's unlock
     * tooltip ({@link de.mkoehler.starwars.render.Tooltip}) — nowhere else
     * in this codebase needs a display name today, since every other
     * ship-facing label (descriptions, HUD art) is pre-baked art rather
     * than live text.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return switch (this) {
            case XWING -> "X-wing";
            case FALCON -> "Falcon";
            case SNOWSPEEDER -> "Snowspeeder";
            case STARDESTROYER -> "Star Destroyer";
            case TIEFIGHTER -> "TIE Fighter";
            case TIEINTERCEPTOR -> "TIE Interceptor";
            case AWING -> "A-Wing";
        };
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

    /**
     * Returns this ship type's progression tier (1-4, see the class
     * Javadoc), used to weigh kill XP by {@link KillXp}.
     *
     * @return the progression tier
     */
    public int getTier() {
        return tier;
    }
}
