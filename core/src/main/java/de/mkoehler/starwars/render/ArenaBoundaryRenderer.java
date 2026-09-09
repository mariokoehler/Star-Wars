package de.mkoehler.starwars.render;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import de.mkoehler.starwars.sim.ArenaBounds;
import de.mkoehler.starwars.sim.PhysicsConstants;

/**
 * Draws the arena's boundary wall marker as a band running the perimeter of
 * the {@link ArenaBounds#SIZE_METERS} square (design.md — arena bounds) —
 * the visual counterpart to {@link ArenaBounds#createBoundary}'s physical
 * wall, so a player can see the edge coming rather than only feeling it as a
 * bounce. Deliberately <i>not</i> an attempt to texture the (much larger,
 * effectively unbounded) space beyond the wall — nothing ever gets out there
 * to look at it, so the existing starfield/nebula parallax already covers it
 * for free; only the wall itself needs art.
 * <p>
 * Implemented the same way {@link ParallaxBackground.Layer} tiles a
 * background — a single seamlessly-horizontally-tileable source texture
 * ({@link GameAssets#ARENA_BOUNDARY}), repeat-wrapped and sampled with a
 * {@link TextureRegion} whose U range spans many tiles — but unlike that
 * class, this one draws at a <i>fixed</i> world position/length (the arena
 * is a known, finite size), not a camera-relative infinite scroll, so it
 * needs no camera reference at all.
 * <p>
 * One tile spans {@link #TILE_LENGTH_METERS} of arena edge; the band's
 * on-screen <i>thickness</i> is derived from the source texture's own
 * aspect ratio ({@link #thicknessMeters}), not a second independently-tuned
 * constant — so a future re-authored texture (e.g. the user's own
 * hand-drawn "hazard tape" art, which replaced the original generated
 * placeholder here) can have a completely different pixel aspect ratio
 * without needing this class updated to match, or risking a silently
 * stretched/squashed result if that update were forgotten.
 */
public class ArenaBoundaryRenderer {

    /**
     * How many meters of arena edge one horizontal tile of the source
     * texture represents. {@link ArenaBounds#SIZE_METERS} divides evenly by
     * this (500 / 50 = 10) so every edge ends on a whole tile, with no
     * stretched partial tile at the corners.
     */
    private static final float TILE_LENGTH_METERS = 50f;

    private final Texture texture;
    private final TextureRegion region;
    private final float thicknessMeters;

    /**
     * Creates the renderer, reading its texture from {@code assets} — it
     * must already be loaded (design.md — asset loading).
     *
     * @param assets the shared asset manager to resolve the texture from
     */
    public ArenaBoundaryRenderer(AssetManager assets) {
        texture = assets.get(GameAssets.ARENA_BOUNDARY, Texture.class);
        texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        region = new TextureRegion(texture);
        // Preserves the source art's real proportions at whatever pixel size it happens to be
        // authored at - see the class Javadoc for why this isn't a second hardcoded constant.
        thicknessMeters = TILE_LENGTH_METERS * texture.getHeight() / texture.getWidth();
    }

    /**
     * Draws all 4 edges of the arena boundary.
     *
     * @param batch the batch to draw with; must already be between
     *              {@code begin()}/{@code end()}, in world space (the same
     *              camera-projected batch ships/projectiles draw with, not
     *              a HUD/screen-space one)
     */
    public void render(SpriteBatch batch) {
        float ppm = PhysicsConstants.PIXELS_PER_METER;
        float halfPixels = ArenaBounds.HALF_SIZE_METERS * ppm;
        float sizePixels = ArenaBounds.SIZE_METERS * ppm;
        float thicknessPixels = thicknessMeters * ppm;
        float tilesAcross = ArenaBounds.SIZE_METERS / TILE_LENGTH_METERS;

        region.setRegion(0f, 0f, tilesAcross, 1f);

        // Top/bottom: the band's own width axis (U, tiled) already runs along world X, so no
        // rotation needed - centered on the physical wall line, half the band inside the arena
        // and half outside.
        batch.draw(region, -halfPixels, halfPixels - thicknessPixels / 2f, sizePixels, thicknessPixels);
        batch.draw(region, -halfPixels, -halfPixels - thicknessPixels / 2f, sizePixels, thicknessPixels);

        // Left/right: same tiled quad, rotated 90 degrees around its own center so the tiled
        // axis runs along world Y instead - the rotated bounding box keeps width=thicknessPixels,
        // height=sizePixels, still centered on the wall line the same way as the pair above.
        batch.draw(region, -halfPixels - sizePixels / 2f, -thicknessPixels / 2f,
            sizePixels / 2f, thicknessPixels / 2f, sizePixels, thicknessPixels, 1f, 1f, 90f);
        batch.draw(region, halfPixels - sizePixels / 2f, -thicknessPixels / 2f,
            sizePixels / 2f, thicknessPixels / 2f, sizePixels, thicknessPixels, 1f, 1f, 90f);
    }
}
