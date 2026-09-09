package de.mkoehler.starwars.render;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import de.mkoehler.starwars.sim.ArenaBounds;
import de.mkoehler.starwars.sim.PhysicsConstants;

/**
 * Draws the arena's boundary as a glowing "energy containment field" band
 * running the perimeter of the {@link ArenaBounds#SIZE_METERS} square
 * (design.md — arena bounds) — the visual counterpart to
 * {@link ArenaBounds#createBoundary}'s physical wall, so a player can see
 * the edge coming rather than only feeling it as a bounce. Deliberately
 * <i>not</i> an attempt to texture the (much larger, effectively unbounded)
 * space beyond the wall — nothing ever gets out there to look at it, so the
 * existing starfield/nebula parallax already covers it for free; only the
 * wall itself needs art.
 * <p>
 * Implemented the same way {@link ParallaxBackground.Layer} tiles a
 * background — a single seamlessly-horizontally-tileable source texture
 * ({@link GameAssets#ARENA_BOUNDARY}), repeat-wrapped and sampled with a
 * {@link TextureRegion} whose U range spans many tiles — but unlike that
 * class, this one draws at a <i>fixed</i> world position/length (the arena
 * is a known, finite size), not a camera-relative infinite scroll, so it
 * needs no camera reference at all.
 * <p>
 * One tile represents {@link #TILE_LENGTH_METERS}×{@link #THICKNESS_METERS}
 * of world space — the source art (a Python/Pillow-generated placeholder,
 * see CLAUDE.md for the generation technique) <b>must</b> already tile
 * seamlessly at that aspect ratio; this class only ever changes how many
 * times it repeats, never how it's authored.
 */
public class ArenaBoundaryRenderer {

    /** How thick the visual band is, in meters — centered on the actual physical wall line. */
    private static final float THICKNESS_METERS = 8f;

    /**
     * How many meters of arena edge one horizontal tile of the source
     * texture represents. {@link ArenaBounds#SIZE_METERS} divides evenly by
     * this (500 / 10 = 50) so every edge ends on a whole tile, with no
     * stretched partial tile at the corners.
     */
    private static final float TILE_LENGTH_METERS = 10f;

    private final Texture texture;
    private final TextureRegion region;

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
        float thicknessPixels = THICKNESS_METERS * ppm;
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
