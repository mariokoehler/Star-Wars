package de.mkoehler.starwars.render;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import de.mkoehler.starwars.sim.ShipStats;

import java.util.List;

/**
 * Renders the radar/minimap HUD widget (design.md 2.14's rendering
 * addendum): a north-up, fixed scope (it never rotates with the observer's
 * own facing — see {@link RadarScopeMath}'s class Javadoc) showing up to
 * three range rings, a forward-facing cone overlay that <em>does</em> rotate
 * with the observer, and one marker per currently-known contact (a plain
 * dot in range, a chevron pinned to the scope's edge for a pulse-revealed
 * contact beyond the observer's own equipment).
 * <p>
 * The scope is scaled to <em>this ship type's own</em> largest enabled
 * radar range ({@link ShipStats#getRadarMaxRangeMeters()}), not one fixed
 * distance for every ship — a ship with fewer mechanisms enabled still uses
 * the whole scope, and the scope never visibly "jumps" scale just because
 * the pulse fires (design.md 2.14). A mechanism the ship type doesn't have
 * enabled simply isn't drawn at all — the cone overlay, in particular, is
 * skipped entirely for a ship type without one, not drawn at some
 * degenerate zero size.
 * <p>
 * All five source images are read from the shared {@link AssetManager} (see
 * {@link ShipStatusHud}'s class Javadoc for the same reasoning) rather than
 * loaded/disposed by this class itself, so this class owns nothing and
 * needs no {@code dispose()}.
 */
public class RadarHud {

    /** Fraction of the widget's drawn size the scope's center sits at horizontally — the background art's own authored geometry (256 of 512px). */
    private static final float SCOPE_CENTER_X_FRACTION = 0.5f;
    /** Fraction of the widget's drawn size the scope's center sits at, measured from the bottom (Y-up) — 1 - 224/512. */
    private static final float SCOPE_CENTER_Y_FRACTION_FROM_BOTTOM = 1f - 224f / 512f;
    /** Fraction of the widget's drawn size the scope's radius spans — 150/512. */
    private static final float SCOPE_RADIUS_FRACTION = 150f / 512f;
    /**
     * The ring overlay's own crisp circle sits at 90% of its texture's own
     * half-width (its outer glow needs the remaining margin so it doesn't
     * clip against the texture edge) — drawing it at
     * {@code targetDiameter / RING_VISIBLE_DIAMETER_FRACTION} compensates,
     * so the crisp ring itself lands exactly at the intended radius.
     */
    private static final float RING_VISIBLE_DIAMETER_FRACTION = 0.90f;
    /** On-screen contact marker size, as a fraction of the widget's drawn size. */
    private static final float BLIP_SIZE_FRACTION = 22f / 512f;
    private static final float CHEVRON_SIZE_FRACTION = 26f / 512f;

    private final TextureRegion background;
    private final TextureRegion ring;
    private final TextureRegion cone;
    private final TextureRegion blip;
    private final TextureRegion chevron;

    /**
     * Creates the widget, reading its five source textures from
     * {@code assets} immediately — all must already be loaded (see the
     * class Javadoc).
     *
     * @param assets the shared asset manager to resolve textures from
     */
    public RadarHud(AssetManager assets) {
        background = new TextureRegion(assets.get(GameAssets.RADAR_BACKGROUND, Texture.class));
        ring = new TextureRegion(assets.get(GameAssets.RADAR_RING, Texture.class));
        cone = new TextureRegion(assets.get(GameAssets.RADAR_CONE, Texture.class));
        blip = new TextureRegion(assets.get(GameAssets.RADAR_BLIP, Texture.class));
        chevron = new TextureRegion(assets.get(GameAssets.RADAR_CHEVRON, Texture.class));
    }

    /**
     * Draws the widget as a {@code size}x{@code size} square with its
     * bottom-left corner at ({@code x}, {@code y}), in whatever coordinate
     * system the batch's currently-set projection matrix uses (a
     * screen-space HUD camera, not the world camera).
     *
     * @param batch                  the batch to draw with; must already be
     *                               between {@code begin()}/{@code end()}
     * @param stats                  the observer's own ship type's stats,
     *                               for which radar mechanisms are enabled
     *                               and their ranges
     * @param x                      the widget's screen X position
     * @param y                      the widget's screen Y position
     * @param size                   the widget's width and height
     * @param observerXMeters        the observer's own current X position, in meters
     * @param observerYMeters        the observer's own current Y position, in meters
     * @param observerAngleRadians   the observer's own current facing angle, in radians —
     *                               only the cone overlay uses this, the scope itself is fixed
     * @param contactPositionsMeters every currently-known contact's world position, in meters —
     *                               already radar-filtered server-side (design.md 2.14), so every
     *                               entry here is drawn, none are filtered again client-side
     */
    public void render(SpriteBatch batch, ShipStats stats, float x, float y, float size,
                        float observerXMeters, float observerYMeters, float observerAngleRadians,
                        List<Vector2> contactPositionsMeters) {
        batch.draw(background, x, y, size, size);

        float maxRangeMeters = stats.getRadarMaxRangeMeters();
        if (maxRangeMeters <= 0f) {
            return; // no radar mechanism enabled at all - not expected in practice, see the getter's own Javadoc
        }

        float scopeCenterX = x + size * SCOPE_CENTER_X_FRACTION;
        float scopeCenterY = y + size * SCOPE_CENTER_Y_FRACTION_FROM_BOTTOM;
        float scopeRadius = size * SCOPE_RADIUS_FRACTION;

        drawRing(batch, stats.isRadarBaseEnabled(), stats.getRadarBaseRangeMeters(), maxRangeMeters,
            scopeCenterX, scopeCenterY, scopeRadius);
        drawRing(batch, stats.isRadarConeEnabled(), stats.getRadarConeRangeMeters(), maxRangeMeters,
            scopeCenterX, scopeCenterY, scopeRadius);
        drawRing(batch, stats.isRadarPulseEnabled(), stats.getRadarPulseRangeMeters(), maxRangeMeters,
            scopeCenterX, scopeCenterY, scopeRadius);

        if (stats.isRadarConeEnabled()) {
            drawCone(batch, stats.getRadarConeRangeMeters(), maxRangeMeters,
                scopeCenterX, scopeCenterY, scopeRadius, observerAngleRadians);
        }

        for (Vector2 contact : contactPositionsMeters) {
            drawContact(batch, observerXMeters, observerYMeters, contact.x, contact.y, maxRangeMeters,
                scopeCenterX, scopeCenterY, scopeRadius, size);
        }
    }

    private void drawRing(SpriteBatch batch, boolean enabled, float rangeMeters, float maxRangeMeters,
                           float scopeCenterX, float scopeCenterY, float scopeRadius) {
        if (!enabled) {
            return;
        }
        float fraction = RadarScopeMath.rangeRadiusFraction(rangeMeters, maxRangeMeters);
        if (fraction <= 0f) {
            return;
        }
        float visibleDiameter = 2f * fraction * scopeRadius;
        float drawnSize = visibleDiameter / RING_VISIBLE_DIAMETER_FRACTION;
        batch.draw(ring, scopeCenterX - drawnSize / 2f, scopeCenterY - drawnSize / 2f, drawnSize, drawnSize);
    }

    /**
     * Draws the forward-facing cone overlay, rotated to the observer's
     * current facing. The cone texture is authored with its apex at the
     * exact bottom-center of its canvas, reaching to the top edge — so its
     * own region height already <em>is</em> its apex-to-tip length in
     * texture pixels, and rotating around the origin
     * ({@code drawnWidth / 2}, {@code 0}) pivots exactly at the apex, which
     * is placed at the scope's center.
     */
    private void drawCone(SpriteBatch batch, float coneRangeMeters, float maxRangeMeters,
                           float scopeCenterX, float scopeCenterY, float scopeRadius, float observerAngleRadians) {
        float fraction = RadarScopeMath.rangeRadiusFraction(coneRangeMeters, maxRangeMeters);
        if (fraction <= 0f) {
            return;
        }
        float drawnLength = fraction * scopeRadius;
        float scale = drawnLength / cone.getRegionHeight();
        float drawnWidth = cone.getRegionWidth() * scale;
        float drawnHeight = cone.getRegionHeight() * scale;

        float originX = drawnWidth / 2f;
        float originY = 0f;
        float drawX = scopeCenterX - originX;
        float drawY = scopeCenterY - originY;
        float rotationDegrees = observerAngleRadians * MathUtils.radiansToDegrees;

        batch.draw(cone, drawX, drawY, originX, originY, drawnWidth, drawnHeight, 1f, 1f, rotationDegrees);
    }

    private void drawContact(SpriteBatch batch, float observerXMeters, float observerYMeters,
                              float contactXMeters, float contactYMeters, float maxRangeMeters,
                              float scopeCenterX, float scopeCenterY, float scopeRadius, float widgetSize) {
        RadarScopeMath.BlipPlacement placement = RadarScopeMath.computeBlipPlacement(
            observerXMeters, observerYMeters, contactXMeters, contactYMeters, maxRangeMeters);

        float radius = scopeRadius * placement.radiusFraction();
        // Inverse of this project's angle-to-direction convention, same formula TurretAiming/
        // RadarDetection use in the other direction - reconstructs a world offset from a bearing.
        float offsetX = -radius * MathUtils.sinDeg(placement.bearingDegrees());
        float offsetY = radius * MathUtils.cosDeg(placement.bearingDegrees());
        float contactX = scopeCenterX + offsetX;
        float contactY = scopeCenterY + offsetY;

        if (placement.clamped()) {
            float size = widgetSize * CHEVRON_SIZE_FRACTION;
            batch.draw(chevron, contactX - size / 2f, contactY - size / 2f, size / 2f, size / 2f,
                size, size, 1f, 1f, placement.bearingDegrees());
        } else {
            float size = widgetSize * BLIP_SIZE_FRACTION;
            batch.draw(blip, contactX - size / 2f, contactY - size / 2f, size, size);
        }
    }
}
