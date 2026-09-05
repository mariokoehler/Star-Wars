package de.mkoehler.starwars.render;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;

import java.util.Random;

/**
 * Generates a simple placeholder tileable starfield texture: random white
 * dots of varying brightness on a transparent background.
 * <p>
 * Stand-in for real, hand-authored starfield art (design.md 4.2 leaves the
 * actual art unpicked) — swap {@link ParallaxBackground.Layer} over to a real
 * texture loaded from {@code assets/} once that art exists, no other code
 * needs to change.
 */
public final class PlaceholderStarfield {

    private PlaceholderStarfield() {
    }

    /**
     * Generates a square, seamlessly tileable starfield texture.
     *
     * @param sizePixels width and height of the generated texture, in pixels
     * @param starCount  how many stars to scatter across it
     * @param seed       random seed, so repeated calls with the same
     *                   parameters produce the same pattern
     * @return the generated texture, owned by the caller
     */
    public static Texture generate(int sizePixels, int starCount, long seed) {
        Pixmap pixmap = new Pixmap(sizePixels, sizePixels, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        Random random = new Random(seed);
        for (int i = 0; i < starCount; i++) {
            int x = random.nextInt(sizePixels);
            int y = random.nextInt(sizePixels);
            float brightness = 0.5f + random.nextFloat() * 0.5f;
            pixmap.setColor(brightness, brightness, brightness, 1f);
            pixmap.drawPixel(x, y);
        }

        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }
}
