package de.mkoehler.starwars.render;

/**
 * Pure pixel math for placing pre-rendered dialog art (design.md 5.1's Ship
 * Selection screen) — pulled out of {@code ShipSelectionScreen} specifically
 * so it's unit-testable without a GL context, same split as
 * {@code SpriteCoordinates}/{@code HudGaugeClip}.
 * <p>
 * Element positions in this screen's spec are given in the dialog image's
 * own pixel space — top-down, top-left origin, matching
 * {@link com.badlogic.gdx.graphics.g2d.TextureRegion}'s convention (not the
 * sprite-local Y-up convention {@code PixelPoint} uses) — while libGDX draws
 * from a bottom-left screen-space corner. {@link #toScreenY} is the one
 * conversion needed between them; X needs no conversion since both
 * conventions agree on which way X increases.
 * <p>
 * Public because its consumer, {@code ShipSelectionScreen}, lives in the
 * root {@code de.mkoehler.starwars} package alongside {@code Client}, not
 * here in {@code render} — unlike {@code SpriteCoordinates}/{@code
 * HudGaugeClip}, which stayed package-private since their one consumer is
 * co-located with them.
 */
public final class DialogLayout {

    private DialogLayout() {
    }

    /**
     * Converts a dialog-local, top-down X to screen space.
     *
     * @param containerScreenX the container's (e.g. the dialog image's)
     *                         on-screen bottom-left X
     * @param topDownX         the element's X, relative to the container's
     *                         left edge
     * @return the element's on-screen X
     */
    public static float toScreenX(float containerScreenX, float topDownX) {
        return containerScreenX + topDownX;
    }

    /**
     * Converts a dialog-local, top-down Y (of an element's top edge) to the
     * screen-space Y of that element's *bottom* edge, as
     * {@link com.badlogic.gdx.graphics.g2d.SpriteBatch#draw} expects.
     *
     * @param containerScreenY the container's on-screen bottom-left Y
     * @param containerHeight  the container's height
     * @param topDownY         the element's top edge, counted down from the
     *                         container's top edge
     * @param elementHeight    the element's height
     * @return the element's on-screen bottom-left Y
     */
    public static float toScreenY(float containerScreenY, float containerHeight, float topDownY, float elementHeight) {
        return containerScreenY + containerHeight - topDownY - elementHeight;
    }

    /**
     * One result of {@link #fitCentered}: the content's scaled size and its
     * offset from the box's corner (the same offset regardless of whether
     * the box's own position is expressed top-down or bottom-up, since a
     * centering gap is symmetric either way).
     *
     * @param width  the scaled content width
     * @param height the scaled content height
     * @param offsetX the horizontal gap between the box's edge and the
     *                scaled content, i.e. half of the leftover space
     * @param offsetY the vertical gap between the box's edge and the scaled
     *                content
     */
    public record Fit(float width, float height, float offsetX, float offsetY) {
    }

    /**
     * Scales content down (never up) to fit within a box, preserving aspect
     * ratio, and centers it within the box.
     *
     * @param boxWidth     the box's width
     * @param boxHeight    the box's height
     * @param contentWidth the unscaled content width
     * @param contentHeight the unscaled content height
     * @return the fitted size and centering offset
     */
    public static Fit fitCentered(float boxWidth, float boxHeight, float contentWidth, float contentHeight) {
        float scale = Math.min(1f, Math.min(boxWidth / contentWidth, boxHeight / contentHeight));
        float width = contentWidth * scale;
        float height = contentHeight * scale;
        return new Fit(width, height, (boxWidth - width) / 2f, (boxHeight - height) / 2f);
    }
}
