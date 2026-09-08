package de.mkoehler.starwars.render;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;

/**
 * Fills the screen with a seamlessly tileable texture that autonomously
 * drifts in a fixed direction over time — unlike {@link ParallaxBackground},
 * which scrolls relative to a world camera's movement, this is for screens
 * with no camera/player to derive movement from (the Ship Selection screen,
 * design.md 5.1), giving the illusion of an ever-moving starfield behind a
 * perfectly still dialog.
 * <p>
 * Implemented the same way as {@link ParallaxBackground.Layer} — sliding the
 * sampled texture coordinates of a {@link Texture.TextureWrap#Repeat}-wrapped
 * texture — but the drawn quad always exactly fills the screen and never
 * itself moves; only the sampled UV offset advances, since there's no camera
 * position to tie the quad's placement to here.
 * <p>
 * Does <b>not</b> take ownership of the given texture — every caller in this
 * codebase passes in a texture already owned by {@code StarWarsGame}'s
 * shared {@link com.badlogic.gdx.assets.AssetManager} ({@link GameAssets#MENU_STARFIELD}),
 * which disposes it once, at app shutdown; this class only mutates its wrap
 * mode.
 */
public class ScrollingBackground {

    private final Texture texture;
    private final TextureRegion region;
    private final Vector2 direction;
    private final float speedPixelsPerSecond;
    private float elapsedSeconds;

    /**
     * Creates a scrolling background.
     *
     * @param texture              a seamlessly tileable texture, set to
     *                             repeat-wrap by this constructor; not owned
     *                             by this class (see the class Javadoc)
     * @param directionDegrees     the drift direction, in degrees (0 = along
     *                             +X, 90 = along +Y)
     * @param speedPixelsPerSecond how fast the pattern drifts, in texture
     *                             pixels/second
     */
    public ScrollingBackground(Texture texture, float directionDegrees, float speedPixelsPerSecond) {
        this.texture = texture;
        texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        this.region = new TextureRegion(texture);
        this.direction = new Vector2(1f, 0f).setAngleDeg(directionDegrees);
        this.speedPixelsPerSecond = speedPixelsPerSecond;
    }

    /**
     * Advances the drift.
     *
     * @param deltaTime time elapsed, in seconds
     */
    public void update(float deltaTime) {
        elapsedSeconds += deltaTime;
    }

    /**
     * Draws the background filling exactly {@code [0, screenWidth] x [0,
     * screenHeight]} of whatever coordinate system the batch's current
     * projection matrix uses.
     *
     * @param batch        the batch to draw with; must already be between
     *                     {@code begin()}/{@code end()}
     * @param screenWidth  the width to fill
     * @param screenHeight the height to fill
     */
    public void render(SpriteBatch batch, float screenWidth, float screenHeight) {
        float offsetX = direction.x * speedPixelsPerSecond * elapsedSeconds;
        float offsetY = direction.y * speedPixelsPerSecond * elapsedSeconds;

        // Negated on V, same as ParallaxBackground.Layer: raw OpenGL texture V runs opposite to
        // world/screen Y, so without this the drift would visibly run backwards on the Y axis.
        float u = offsetX / texture.getWidth();
        float v = -offsetY / texture.getHeight();
        float uWidth = screenWidth / texture.getWidth();
        float vHeight = screenHeight / texture.getHeight();
        region.setRegion(u, v, u + uWidth, v + vHeight);

        batch.draw(region, 0f, 0f, screenWidth, screenHeight);
    }
}
