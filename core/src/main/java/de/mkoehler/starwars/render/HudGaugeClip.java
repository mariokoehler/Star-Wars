package de.mkoehler.starwars.render;

import com.badlogic.gdx.math.MathUtils;

/**
 * Pure pixel math for {@link ShipStatusHud}'s "fuel gauge" clipping — pulled
 * out of it specifically so this arithmetic is unit-testable without a GL
 * context (a {@code Texture}, unlike this class's inputs, can't be created
 * outside a running libGDX application), matching this project's convention
 * of testing logic-heavy pure functions separately from thin
 * rendering/GL-wiring code (see {@code SpriteCoordinates} for the same
 * split applied to the sprite metadata editor).
 */
final class HudGaugeClip {

    private HudGaugeClip() {
    }

    /**
     * One computed clip: which pixel rows of the source image are revealed,
     * and where that revealed slice lands on screen.
     *
     * @param revealTopPixel     the top row (from the top of the image) of
     *                           the revealed slice
     * @param revealHeightPixels the revealed slice's height, in image pixels
     * @param sliceScreenBottomY the screen Y of the revealed slice's bottom
     *                           edge — constant across every fraction, since
     *                           the gauge always fills from the same
     *                           bottom-anchored edge
     * @param sliceScreenHeight  the revealed slice's height, in screen
     *                           pixels (already scaled)
     */
    record Result(int revealTopPixel, int revealHeightPixels, float sliceScreenBottomY, float sliceScreenHeight) {
    }

    /**
     * Computes which vertical slice of a HUD overlay image to reveal for a
     * given fraction, bottom-anchored within {@code [clipTopPixel,
     * clipBottomPixel]} (see {@link de.mkoehler.starwars.sim.ShipTypeConfig}
     * for that range's meaning).
     * <p>
     * The revealed top and height are both rounded from the same base (the
     * bottom pixel), not independently, so the slice's bottom edge never
     * drifts by a stray pixel as the fraction changes frame to frame.
     *
     * @param fraction        the gauge's current fraction, clamped to {@code [0, 1]}
     * @param clipTopPixel    the overlay's visible-pixel range's top row
     * @param clipBottomPixel the overlay's visible-pixel range's bottom row
     * @param widgetSize      the widget's on-screen width/height (square)
     * @param textureHeight   the source texture's height, in pixels
     * @param widgetScreenY   the widget's on-screen Y position (bottom-left corner)
     * @return the computed clip, or a zero-height result if nothing should be revealed
     */
    static Result compute(float fraction, float clipTopPixel, float clipBottomPixel,
                           float widgetSize, float textureHeight, float widgetScreenY) {
        float visiblePixels = (clipBottomPixel - clipTopPixel) * MathUtils.clamp(fraction, 0f, 1f);
        int bottomPixel = Math.round(clipBottomPixel);
        int revealTopPixel = Math.round(clipBottomPixel - visiblePixels);
        int revealHeightPixels = Math.max(0, bottomPixel - revealTopPixel);

        float scale = widgetSize / textureHeight;
        float sliceScreenBottomY = widgetScreenY + widgetSize - bottomPixel * scale;
        float sliceScreenHeight = revealHeightPixels * scale;
        return new Result(revealTopPixel, revealHeightPixels, sliceScreenBottomY, sliceScreenHeight);
    }
}
