package de.mkoehler.starwars.devtools;

import de.mkoehler.starwars.sim.metadata.PixelPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies {@link SpriteCoordinates}' image-pixel-space-to-sprite-local
 * conversion — specifically the Y-axis flip, which is exactly the kind of
 * thing this project has gotten wrong before (design.md 4.2).
 */
class SpriteCoordinatesTest {

    private static final int WIDTH = 128;
    private static final int HEIGHT = 128;

    @Test
    void imageCenterMapsToSpriteOrigin() {
        PixelPoint point = SpriteCoordinates.toSpritePoint(WIDTH / 2, HEIGHT / 2, WIDTH, HEIGHT);
        assertEquals(0f, point.getX());
        assertEquals(0f, point.getY());
    }

    @Test
    void topLeftCornerIsLeftAndUp() {
        // Image-space top-left (0,0) should become sprite-local up-left: negative X, positive Y
        // (Y increases upward in sprite-local space, matching the game's world convention).
        PixelPoint point = SpriteCoordinates.toSpritePoint(0, 0, WIDTH, HEIGHT);
        assertEquals(-WIDTH / 2f, point.getX());
        assertEquals(HEIGHT / 2f, point.getY());
    }

    @Test
    void bottomRightCornerIsRightAndDown() {
        PixelPoint point = SpriteCoordinates.toSpritePoint(WIDTH, HEIGHT, WIDTH, HEIGHT);
        assertEquals(WIDTH / 2f, point.getX());
        assertEquals(-HEIGHT / 2f, point.getY());
    }

    @Test
    void screenConversionIsTheInverseOfSpriteConversion() {
        int zoom = 2;
        for (int imageX = 0; imageX <= WIDTH; imageX += 16) {
            for (int imageY = 0; imageY <= HEIGHT; imageY += 16) {
                PixelPoint spritePoint = SpriteCoordinates.toSpritePoint(imageX, imageY, WIDTH, HEIGHT);
                int screenX = SpriteCoordinates.toScreenX(spritePoint, WIDTH, zoom);
                int screenY = SpriteCoordinates.toScreenY(spritePoint, HEIGHT, zoom);
                assertEquals(imageX * zoom, screenX, "round trip X for image pixel (" + imageX + "," + imageY + ")");
                assertEquals(imageY * zoom, screenY, "round trip Y for image pixel (" + imageX + "," + imageY + ")");
            }
        }
    }
}
