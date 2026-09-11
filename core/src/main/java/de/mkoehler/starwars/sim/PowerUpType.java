package de.mkoehler.starwars.sim;

/**
 * The four world-pickup effects (design.md — power-ups). Each value is tied
 * to a {@code powerups/<resourceName>} packed {@code textures/powerups.atlas}
 * region of the same name — unlike {@link AsteroidType}, there's no per-type
 * {@code .meta.json}/hitbox: every power-up texture is the same 256×256
 * pixel size at the same {@link #PIXELS_PER_METER}, with a fixed circular
 * hitbox ({@code PowerUpFactory#HITBOX_RADIUS_METERS}), so nothing here
 * varies by type except the art itself and what {@code GameNetworkServer}'s
 * effect-application code does on pickup.
 */
public enum PowerUpType {

    /** Repairs up to 50% of the picking-up ship's max hull (see {@code GameNetworkServer#applyPowerUpEffect}). */
    REPAIR("powerup_repair"),
    /** Doubles the picking-up ship's power generation for 15 seconds (see {@link de.mkoehler.starwars.sim.components.PowerBoostComponent}). */
    BOOST("powerup_boost"),
    /** Grants one extra missile; a no-op for a ship type with no missile capability. */
    MISSILE("powerup_missile"),
    /** Currently a no-op placeholder — the actual mine/bomb spawn is a future milestone. */
    BOMB("powerup_bomb");

    /**
     * Every power-up texture's pixels-per-meter (the user's own spec) — a
     * single shared rate, unlike {@link AsteroidType#getPixelsPerMeter()},
     * since every power-up sprite is authored at the same 256×256 size.
     */
    public static final float PIXELS_PER_METER = 64f;

    private final String resourceName;

    PowerUpType(String resourceName) {
        this.resourceName = resourceName;
    }

    /**
     * Returns the base name used to derive this power-up type's packed atlas
     * region, e.g. {@code "powerup_boost"} in {@code textures/powerups.atlas}.
     *
     * @return the resource base name
     */
    public String getResourceName() {
        return resourceName;
    }
}
