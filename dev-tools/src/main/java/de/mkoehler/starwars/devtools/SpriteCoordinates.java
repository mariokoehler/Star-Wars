package de.mkoehler.starwars.devtools;

import de.mkoehler.starwars.sim.metadata.PixelPoint;

/**
 * Converts between raw image/Swing pixel coordinates (origin top-left, Y
 * increasing downward) and the sprite-local convention {@link PixelPoint}
 * uses (origin at the sprite's center, Y increasing upward — matching this
 * project's world/screen convention, design.md 4.3).
 * <p>
 * Pulled out as its own pure, stateless utility specifically so this
 * conversion can be unit-tested in isolation — axis-convention mismatches
 * (Y-up vs. Y-down, in particular) have caused real, non-obvious bugs
 * elsewhere in this project (the parallax background's scroll direction,
 * design.md 4.2), so this is exactly the kind of small piece of logic worth
 * verifying directly rather than only checking visually once wired into the
 * full editor.
 */
final class SpriteCoordinates {

    private SpriteCoordinates() {
    }

    /**
     * Converts an image-space pixel (unzoomed, origin top-left, Y down) to
     * the sprite-local convention.
     *
     * @param imagePixelX  the X coordinate, in unzoomed image pixels from the image's left edge
     * @param imagePixelY  the Y coordinate, in unzoomed image pixels from the image's top edge
     * @param imageWidth   the image's width, in pixels
     * @param imageHeight  the image's height, in pixels
     * @return the equivalent sprite-local point
     */
    static PixelPoint toSpritePoint(int imagePixelX, int imagePixelY, int imageWidth, int imageHeight) {
        float spriteX = imagePixelX - imageWidth / 2f;
        float spriteY = imageHeight / 2f - imagePixelY;
        return new PixelPoint(spriteX, spriteY);
    }

    /**
     * Converts a sprite-local point to an on-screen X coordinate, for an
     * image drawn at zoom {@code zoom} with its top-left corner at the
     * screen origin.
     *
     * @param point       the sprite-local point
     * @param imageWidth  the image's width, in pixels
     * @param zoom        the display zoom factor
     * @return the equivalent on-screen X coordinate, in pixels
     */
    static int toScreenX(PixelPoint point, int imageWidth, int zoom) {
        return Math.round((point.getX() + imageWidth / 2f) * zoom);
    }

    /**
     * Converts a sprite-local point to an on-screen Y coordinate, for an
     * image drawn at zoom {@code zoom} with its top-left corner at the
     * screen origin.
     *
     * @param point       the sprite-local point
     * @param imageHeight the image's height, in pixels
     * @param zoom        the display zoom factor
     * @return the equivalent on-screen Y coordinate, in pixels
     */
    static int toScreenY(PixelPoint point, int imageHeight, int zoom) {
        return Math.round((imageHeight / 2f - point.getY()) * zoom);
    }
}
