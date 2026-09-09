package de.mkoehler.starwars.render;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Disposable;
import de.mkoehler.starwars.sim.ArenaBounds;
import de.mkoehler.starwars.sim.ShipStats;

import java.util.List;
import java.util.Optional;

/**
 * Renders the radar/minimap HUD widget (design.md 2.14's rendering
 * addendum): a north-up, fixed scope (it never rotates with the observer's
 * own facing — see {@link RadarScopeMath}'s class Javadoc) showing up to
 * three range rings, a forward-facing cone overlay that <em>does</em> rotate
 * with the observer, one marker per currently-known contact (a plain
 * dot in range, a chevron pinned to the scope's edge for a pulse-revealed
 * contact beyond the observer's own equipment), and a pulse-cooldown
 * indicator LED ({@link #drawIndicator}, added 2026-09-09) that's
 * independent of the scope geometry entirely.
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
 * All seven source images are read from the shared {@link AssetManager} (see
 * {@link ShipStatusHud}'s class Javadoc for the same reasoning) rather than
 * loaded/disposed by this class itself.
 * <p>
 * Also draws the observer's own position (design.md — arena bounds), rounded
 * to the nearest meter, in the background art's otherwise-empty bottom-left
 * corner (the bottom-right is already spoken for by the pulse-cooldown
 * indicator's "extrusion" tab) — a simple orientation aid for "which part of
 * the arena did I spawn into," not tied to the circular scope's own
 * geometry at all. This is the one thing about this widget that isn't just
 * pre-made art, so — same reasoning as {@link ScoreboardHud}/{@link Tooltip}
 * — it owns a live {@link BitmapFont} and needs {@link #dispose()} called.
 * <p>
 * Also draws the arena boundary itself (design.md — arena bounds' addendum)
 * as up to 4 straight lines — one per edge that currently falls within the
 * scope's displayed range, computed by {@link RadarScopeMath#computeBoundaryLine}
 * and drawn the same "stretched tinted 1x1 pixel" way {@link Tooltip}/
 * {@link FlatButton} draw a solid rectangle, since there's no pre-made art
 * for a line whose length/position varies every frame. An edge more than
 * the scope's current range away from the observer simply isn't drawn at
 * all, same "not detected, not shown" treatment as an out-of-range contact
 * — deep in the arena's interior, the scope shows no boundary lines.
 */
public class RadarHud implements Disposable {

    /**
     * The redesigned background art's (2026-09-09) own native pixel size —
     * used both for the scope-geometry fractions below and for
     * {@link #drawIndicator}'s conversion of the indicator LED's authored
     * pixel offset into a size-relative fraction. Smaller and less square
     * than the original 512×512 art (378×379, plus a small rounded
     * "extrusion" tab in the lower-right corner molded in for the indicator
     * LED) — wastes less widget space around the actual circular scope, per
     * the user's own framing when providing it.
     */
    private static final float BACKGROUND_TEXTURE_WIDTH = 378f;
    private static final float BACKGROUND_TEXTURE_HEIGHT = 379f;
    /**
     * Fraction of the widget's drawn size the scope's center sits at
     * horizontally — measured directly off the new background art (crosshair
     * center at pixel (188.5, 189.5) of 378×379, i.e. dead center this time,
     * unlike the original art's off-center-vertically layout).
     */
    private static final float SCOPE_CENTER_X_FRACTION = 188.5f / BACKGROUND_TEXTURE_WIDTH;
    /** Fraction of the widget's drawn size the scope's center sits at, measured from the bottom (Y-up) — 1 - 189.5/379. */
    private static final float SCOPE_CENTER_Y_FRACTION_FROM_BOTTOM = 1f - 189.5f / BACKGROUND_TEXTURE_HEIGHT;
    /**
     * Fraction of the widget's drawn size the scope's radius spans — 150px,
     * chosen to sit safely inside the new art's dark glass disc (the disc's
     * hash-textured interior measured out to ~156px from center before
     * transitioning into the gold border ring, so 150px leaves a small,
     * deliberate margin rather than grazing the border).
     */
    private static final float SCOPE_RADIUS_FRACTION = 150f / BACKGROUND_TEXTURE_WIDTH;
    /**
     * The pulse-cooldown indicator LED's authored top-left offset, in the
     * background art's own pixel space (image space — Y measured down from
     * the top, per the user's own framing when specifying it) — matches the
     * small molded socket in the art's lower-right "extrusion" tab. 60×60,
     * same native size as {@link #indicatorGreen}/{@link #indicatorRed}.
     */
    private static final float INDICATOR_OFFSET_X_PIXELS = 300f;
    private static final float INDICATOR_OFFSET_Y_PIXELS_FROM_TOP = 300f;
    private static final float INDICATOR_SIZE_PIXELS = 60f;
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

    private static final int COORDS_FONT_SIZE_PX = 14;
    /** Same live-text color convention as {@link ScoreboardHud}'s rows. */
    private static final Color COORDS_TEXT_COLOR = new Color(0.85f, 0.9f, 1f, 1f);
    /**
     * Where the coordinate readout's baseline sits, as a fraction of the
     * widget's drawn size — the background art's bottom-left corner, clear
     * of both the circular scope (centered, {@link #SCOPE_RADIUS_FRACTION}
     * from the middle) and the indicator LED's tab (bottom-right) —
     * untuned placeholder position, not measured off the art the precise
     * way {@link #SCOPE_CENTER_X_FRACTION} etc. are.
     */
    private static final float COORDS_X_FRACTION = 0.06f;
    private static final float COORDS_Y_FRACTION = 0.045f;

    /**
     * Arena-boundary line color (design.md — arena bounds' addendum) — a
     * caution amber/yellow, echoing the in-world hazard-tape boundary wall
     * texture (`ArenaBoundaryRenderer`) rather than reusing any color
     * already meaningful elsewhere on this widget (the ring/cone's
     * blue-green, the pulse indicator's green/red) — an intentional
     * thematic tie-in, not just an arbitrary pick, though the exact shade
     * is still untuned.
     */
    private static final Color BOUNDARY_LINE_COLOR = new Color(1f, 0.78f, 0.1f, 0.9f);
    /** On-screen boundary line thickness, as a fraction of the widget's drawn size — untuned placeholder. */
    private static final float BOUNDARY_LINE_THICKNESS_FRACTION = 3f / 512f;

    private final TextureRegion background;
    private final TextureRegion ring;
    private final TextureRegion cone;
    private final TextureRegion blip;
    private final TextureRegion chevron;
    private final TextureRegion indicatorGreen;
    private final TextureRegion indicatorRed;
    private final BitmapFont coordsFont = GameFonts.generateSfDistantGalaxy(COORDS_FONT_SIZE_PX);
    /**
     * A 1x1 white pixel, stretched and tinted to draw the boundary lines —
     * same "no pre-made art since content/length is dynamic" technique as
     * {@link Tooltip}/{@link FlatButton}'s own background box.
     */
    private final Texture boundaryLinePixel = createWhitePixel();

    /**
     * Creates the widget, reading its seven source textures from
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
        indicatorGreen = new TextureRegion(assets.get(GameAssets.RADAR_INDICATOR_GREEN, Texture.class));
        indicatorRed = new TextureRegion(assets.get(GameAssets.RADAR_INDICATOR_RED, Texture.class));
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
     * @param pulseCooldownRemainingSeconds the observer's own active-pulse
     *                                       cooldown ({@code ShipState#getRadarPulseCooldownRemaining()}),
     *                                       {@code <= 0} meaning ready — drives the indicator LED
     *                                       ({@link #drawIndicator}), independent of whether any
     *                                       range ring/contact is drawn this call
     */
    public void render(SpriteBatch batch, ShipStats stats, float x, float y, float size,
                        float observerXMeters, float observerYMeters, float observerAngleRadians,
                        List<Vector2> contactPositionsMeters, float pulseCooldownRemainingSeconds) {
        batch.draw(background, x, y, size, size);
        boolean pulseAvailable = stats.isRadarPulseEnabled() && pulseCooldownRemainingSeconds <= 0f;
        drawIndicator(batch, pulseAvailable, x, y, size);
        drawCoordinates(batch, observerXMeters, observerYMeters, x, y, size);

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

        drawBoundaryLines(batch, observerXMeters, observerYMeters, maxRangeMeters,
            scopeCenterX, scopeCenterY, scopeRadius, size);

        for (Vector2 contact : contactPositionsMeters) {
            drawContact(batch, observerXMeters, observerYMeters, contact.x, contact.y, maxRangeMeters,
                scopeCenterX, scopeCenterY, scopeRadius, size);
        }
    }

    /**
     * Draws the pulse-cooldown indicator LED (2026-09-09) — green when the
     * active pulse is enabled for this ship type and off cooldown, red
     * otherwise (disabled entirely, or on cooldown; the two cases share one
     * color since neither means "press R right now"). Positioned at the
     * background art's own molded socket in its lower-right "extrusion" tab,
     * via {@link #INDICATOR_OFFSET_X_PIXELS}/{@link #INDICATOR_OFFSET_Y_PIXELS_FROM_TOP}
     * converted from that art's native pixel space into a fraction of
     * {@code size} — independent of the scope's own center/radius geometry
     * above, since the LED isn't part of the circular scope at all.
     */
    private void drawIndicator(SpriteBatch batch, boolean available, float x, float y, float size) {
        TextureRegion region = available ? indicatorGreen : indicatorRed;
        float widthPixels = size * INDICATOR_SIZE_PIXELS / BACKGROUND_TEXTURE_WIDTH;
        float heightPixels = size * INDICATOR_SIZE_PIXELS / BACKGROUND_TEXTURE_HEIGHT;
        float leftX = x + size * INDICATOR_OFFSET_X_PIXELS / BACKGROUND_TEXTURE_WIDTH;
        // The background quad's top edge is at y + size (batch.draw's (x, y) is its bottom-left,
        // screen space is Y-up) - the offset is authored Y-down from that top edge, per the art's
        // own image-space convention.
        float topY = y + size - size * INDICATOR_OFFSET_Y_PIXELS_FROM_TOP / BACKGROUND_TEXTURE_HEIGHT;
        float bottomY = topY - heightPixels;
        batch.draw(region, leftX, bottomY, widthPixels, heightPixels);
    }

    /**
     * Draws the observer's own position, rounded to the nearest meter, as
     * plain "x, y" text (design.md — arena bounds) — a simple orientation
     * aid, not tied to any particular coordinate convention beyond "the same
     * meters everything else in the simulation already uses."
     */
    private void drawCoordinates(SpriteBatch batch, float observerXMeters, float observerYMeters, float x, float y, float size) {
        String text = Math.round(observerXMeters) + ", " + Math.round(observerYMeters);
        coordsFont.setColor(COORDS_TEXT_COLOR);
        coordsFont.draw(batch, text, x + size * COORDS_X_FRACTION, y + size * COORDS_Y_FRACTION);
    }

    /**
     * Draws up to 4 straight lines marking the arena boundary (design.md —
     * arena bounds' addendum) — one per edge currently within
     * {@code maxRangeMeters} of the observer, via
     * {@link RadarScopeMath#computeBoundaryLine}. Top/bottom are horizontal
     * (the edge's own axis is world X, perpendicular is world Y);
     * left/right are vertical (axes swapped) — see that method's Javadoc
     * for why this needs no rotation despite covering all 4 edges with one
     * formula.
     */
    private void drawBoundaryLines(SpriteBatch batch, float observerXMeters, float observerYMeters,
                                    float maxRangeMeters, float scopeCenterX, float scopeCenterY,
                                    float scopeRadius, float widgetSize) {
        float half = ArenaBounds.HALF_SIZE_METERS;
        float thickness = widgetSize * BOUNDARY_LINE_THICKNESS_FRACTION;

        // Top/bottom edges: horizontal lines, "along" = world X, "perpendicular" = world Y.
        drawHorizontalBoundaryEdge(batch, observerXMeters, observerYMeters, half, -half, half,
            maxRangeMeters, scopeCenterX, scopeCenterY, scopeRadius, thickness);
        drawHorizontalBoundaryEdge(batch, observerXMeters, observerYMeters, -half, -half, half,
            maxRangeMeters, scopeCenterX, scopeCenterY, scopeRadius, thickness);

        // Left/right edges: vertical lines, "along" = world Y, "perpendicular" = world X.
        drawVerticalBoundaryEdge(batch, observerYMeters, observerXMeters, half, -half, half,
            maxRangeMeters, scopeCenterX, scopeCenterY, scopeRadius, thickness);
        drawVerticalBoundaryEdge(batch, observerYMeters, observerXMeters, -half, -half, half,
            maxRangeMeters, scopeCenterX, scopeCenterY, scopeRadius, thickness);
    }

    private void drawHorizontalBoundaryEdge(SpriteBatch batch, float observerAlong, float observerPerp,
                                             float edgePerp, float edgeAlongMin, float edgeAlongMax,
                                             float maxRangeMeters, float scopeCenterX, float scopeCenterY,
                                             float scopeRadius, float thickness) {
        Optional<RadarScopeMath.BoundaryLinePlacement> placement = RadarScopeMath.computeBoundaryLine(
            observerAlong, observerPerp, edgePerp, edgeAlongMin, edgeAlongMax, scopeRadius, maxRangeMeters);
        if (placement.isEmpty()) {
            return;
        }
        RadarScopeMath.BoundaryLinePlacement line = placement.get();
        float lineY = scopeCenterY + line.perpOffset() - thickness / 2f;
        float startX = scopeCenterX + line.alongStart();
        float endX = scopeCenterX + line.alongEnd();
        drawTintedLine(batch, startX, lineY, endX - startX, thickness);
    }

    private void drawVerticalBoundaryEdge(SpriteBatch batch, float observerAlong, float observerPerp,
                                           float edgePerp, float edgeAlongMin, float edgeAlongMax,
                                           float maxRangeMeters, float scopeCenterX, float scopeCenterY,
                                           float scopeRadius, float thickness) {
        Optional<RadarScopeMath.BoundaryLinePlacement> placement = RadarScopeMath.computeBoundaryLine(
            observerAlong, observerPerp, edgePerp, edgeAlongMin, edgeAlongMax, scopeRadius, maxRangeMeters);
        if (placement.isEmpty()) {
            return;
        }
        RadarScopeMath.BoundaryLinePlacement line = placement.get();
        float lineX = scopeCenterX + line.perpOffset() - thickness / 2f;
        float startY = scopeCenterY + line.alongStart();
        float endY = scopeCenterY + line.alongEnd();
        drawTintedLine(batch, lineX, startY, thickness, endY - startY);
    }

    private void drawTintedLine(SpriteBatch batch, float x, float y, float width, float height) {
        // batch.getColor() returns its own live, mutable Color field, not a snapshot - copy it
        // (cpy()) before setColor() below mutates that very object out from under us (same
        // gotcha Tooltip/FlatButton's own render() already documents).
        Color previousColor = batch.getColor().cpy();
        batch.setColor(BOUNDARY_LINE_COLOR);
        batch.draw(boundaryLinePixel, x, y, width, height);
        batch.setColor(previousColor);
    }

    private static Texture createWhitePixel() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
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

    @Override
    public void dispose() {
        coordsFont.dispose();
        boundaryLinePixel.dispose();
    }
}
