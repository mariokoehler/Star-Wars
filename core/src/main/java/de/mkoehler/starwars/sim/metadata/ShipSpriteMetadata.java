package de.mkoehler.starwars.sim.metadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-ship-type data authored visually against a ship's sprite, rather than
 * derived from a formula: a collision hitbox polygon, and named attachment
 * points (e.g. {@code "PROJECTILE"} for where a weapon's shots originate,
 * {@code "ENGINE"} for engine glow, {@code "LIGHT"}/{@code "DAMAGE_SMOKE"}
 * for future particle effects — design.md 2.4/4.3).
 * <p>
 * Authored with the sprite metadata editor (the {@code dev-tools} module,
 * not shipped in the game), stored as one JSON file per ship under
 * {@code assets/shipdata/} (loaded via {@link ShipSpriteMetadataLoader}).
 * Every value is one entry short of what {@link PixelPoint} expects; see its
 * Javadoc for the coordinate convention.
 * <p>
 * Each attachment name maps to a **list** of points, not a single point, so
 * a ship with multiple guns (e.g. twin-linked cannons) can have more than
 * one {@code "PROJECTILE"} point — how that's actually used (fire from all
 * at once, alternate, etc.) is a decision for whatever consumes this data,
 * not something this class enforces. A ship's {@code "TURRET"} points work
 * the same way — one independently-tracking turret per point, tuned by the
 * shared {@link #getTurretConfig()}.
 * <p>
 * Plain mutable bean (public no-arg constructor, getters and setters) so
 * Jackson can (de)serialize it with no extra configuration.
 */
public class ShipSpriteMetadata {

    private List<PixelPoint> hitboxPolygon = new ArrayList<>();
    private Map<String, List<PixelPoint>> attachmentPoints = new LinkedHashMap<>();
    private TurretConfig turretConfig;

    /**
     * Returns the collision hitbox's vertices, in sprite-local pixel space
     * (see {@link PixelPoint}). Box2D's {@code PolygonShape} computes the
     * convex hull of whatever points are given, so this doesn't need to be
     * pre-validated as convex — but it must have between 3 and 8 points
     * (Box2D's own limit) to build a valid shape.
     *
     * @return the hitbox polygon's vertices
     */
    public List<PixelPoint> getHitboxPolygon() {
        return hitboxPolygon;
    }

    /**
     * Sets the collision hitbox's vertices.
     *
     * @param hitboxPolygon the hitbox polygon's vertices
     */
    public void setHitboxPolygon(List<PixelPoint> hitboxPolygon) {
        this.hitboxPolygon = hitboxPolygon;
    }

    /**
     * Returns every named attachment point, keyed by name (e.g.
     * {@code "PROJECTILE"}), each with one or more locations.
     *
     * @return the attachment points, by name
     */
    public Map<String, List<PixelPoint>> getAttachmentPoints() {
        return attachmentPoints;
    }

    /**
     * Sets the named attachment points.
     *
     * @param attachmentPoints the attachment points, by name
     */
    public void setAttachmentPoints(Map<String, List<PixelPoint>> attachmentPoints) {
        this.attachmentPoints = attachmentPoints;
    }

    /**
     * Returns this ship type's turret tuning values, if it has any
     * {@code "TURRET"} attachment points — {@code null} for every other
     * ship type.
     *
     * @return the turret config, or {@code null} if this ship has no turrets
     */
    public TurretConfig getTurretConfig() {
        return turretConfig;
    }

    /**
     * Sets this ship type's turret tuning values.
     *
     * @param turretConfig the turret config, or {@code null} for a ship with no turrets
     */
    public void setTurretConfig(TurretConfig turretConfig) {
        this.turretConfig = turretConfig;
    }
}
