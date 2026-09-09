package de.mkoehler.starwars.sim;

import de.mkoehler.starwars.sim.metadata.ShipSpriteMetadata;
import de.mkoehler.starwars.sim.metadata.ShipSpriteMetadataLoader;

import java.util.EnumMap;
import java.util.Map;

/**
 * An asteroid type's runtime stats: its authored hitbox polygon (design.md
 * — asteroids), loaded from {@code asteroids/<name>.meta.json}. Shared
 * between the server (which needs it to build a Box2D body) and the client
 * (which needs it to size the drawn sprite consistently with the server) —
 * same "one instance per type, cached in {@link #forType}" shape as
 * {@link ShipStats}.
 * <p>
 * Unlike {@link ShipStats}, the metadata here is <b>required</b>, not
 * optional — every {@link AsteroidType} was authored with a real hitbox
 * from the start (the user's own {@code .meta.json} files, created
 * alongside the art itself), so there's no legacy "not authored yet"
 * circle-fallback case to support.
 */
public final class AsteroidStats {

    private static final Map<AsteroidType, AsteroidStats> BY_TYPE = new EnumMap<>(AsteroidType.class);

    private final AsteroidType type;
    private final ShipSpriteMetadata spriteMetadata;

    private AsteroidStats(AsteroidType type) {
        this.type = type;
        String resourceName = type.getResourceName();
        this.spriteMetadata = ShipSpriteMetadataLoader.loadFromClasspath("asteroids/" + resourceName + ".meta.json")
            .orElseThrow(() -> new IllegalStateException(
                "Missing required asteroids/" + resourceName + ".meta.json for asteroid type " + type));
    }

    /**
     * Returns the (cached) stats for an asteroid type, loading them on
     * first use.
     *
     * @param type the asteroid type
     * @return that type's stats
     */
    public static AsteroidStats forType(AsteroidType type) {
        return BY_TYPE.computeIfAbsent(type, AsteroidStats::new);
    }

    /**
     * Returns this asteroid's type.
     *
     * @return the asteroid type
     */
    public AsteroidType getType() {
        return type;
    }

    /**
     * Returns this asteroid's authored sprite metadata (hitbox polygon —
     * design.md — asteroids).
     *
     * @return the metadata
     */
    public ShipSpriteMetadata getSpriteMetadata() {
        return spriteMetadata;
    }
}
