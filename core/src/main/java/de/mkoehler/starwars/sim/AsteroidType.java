package de.mkoehler.starwars.sim;

/**
 * The distinct asteroid textures/hitboxes available to spawn (design.md —
 * asteroids). Each value is tied to an {@code asteroids/<resourceName>}
 * pair of classpath resources — a {@code .meta.json} (hitbox polygon, see
 * {@link de.mkoehler.starwars.sim.metadata.ShipSpriteMetadata}), loaded by
 * {@link AsteroidStats}, and a packed {@code textures/asteroids.atlas}
 * region of the same name — plus a hardcoded {@link #getPixelsPerMeter()}.
 * <p>
 * Unlike a ship type, an asteroid's pixels-per-meter isn't authored
 * per-type in a {@code .stats.json} — it's derived here, once, from each
 * source PNG's actual largest pixel dimension (measured directly from the
 * art) scaled to {@link #TARGET_LARGEST_DIMENSION_METERS} for every type
 * alike, same "hardcode a per-type pixels-per-meter rather than derive it
 * at runtime" reasoning {@code ShipTypeConfig#getPixelsPerMeter()} already
 * documents — the headless server has no GL context to ever read a
 * texture's real pixel dimensions itself.
 */
public enum AsteroidType {

    ASTEROID("asteroid", 500),
    ASTEROID2("asteroid2", 184),
    ASTEROID3("asteroid3", 185),
    ASTEROID4("asteroid4", 210),
    ASTEROID5("asteroid5", 297),
    ASTEROID6("asteroid6", 286),
    ASTEROID7("asteroid7", 178),
    ASTEROID8("asteroid8", 267);

    /**
     * Every asteroid type's largest source-art pixel dimension is scaled to
     * this real-world size — a "large, hulking obstacle" the user's own
     * spec calls for: clearly bigger than a ship (4m real diameter) while
     * still small relative to the 500m arena. Untuned starting point, same
     * as every other size/tuning number in this project.
     */
    public static final float TARGET_LARGEST_DIMENSION_METERS = 20f;

    private final String resourceName;
    private final float pixelsPerMeter;

    AsteroidType(String resourceName, int largestSourcePixelDimension) {
        this.resourceName = resourceName;
        this.pixelsPerMeter = largestSourcePixelDimension / TARGET_LARGEST_DIMENSION_METERS;
    }

    /**
     * Returns the base name used to derive this asteroid type's classpath
     * resource paths, e.g. {@code "asteroid2"} for
     * {@code asteroids/asteroid2.meta.json} and the packed atlas region
     * {@code "asteroid2"} in {@code textures/asteroids.atlas}.
     *
     * @return the resource base name
     */
    public String getResourceName() {
        return resourceName;
    }

    /**
     * Returns this asteroid type's pixels-per-meter — see this class's own
     * Javadoc for how it's derived.
     *
     * @return this asteroid type's pixels-per-meter
     */
    public float getPixelsPerMeter() {
        return pixelsPerMeter;
    }
}
