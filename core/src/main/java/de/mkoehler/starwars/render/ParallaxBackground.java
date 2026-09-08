package de.mkoehler.starwars.render;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * Draws one or more infinitely-tiling background layers behind gameplay, each
 * scrolling at its own fraction of camera movement to fake depth (design.md
 * 4.2). Draw this before any gameplay entities, in its own
 * {@code batch.begin()}/{@code end()} pass.
 */
public class ParallaxBackground {

    private final Layer[] layers;

    /**
     * Creates a parallax background from back to front.
     *
     * @param layers the layers to draw, in the order they should be drawn (the
     *               first is drawn first, i.e. furthest back)
     */
    public ParallaxBackground(Layer... layers) {
        this.layers = layers;
    }

    /**
     * Draws every layer, filling the camera's current viewport.
     *
     * @param batch  the batch to draw with; must already be set up for the
     *               given camera (see {@link SpriteBatch#setProjectionMatrix})
     * @param camera the camera the background should appear relative to
     */
    public void render(SpriteBatch batch, OrthographicCamera camera) {
        for (Layer layer : layers) {
            layer.render(batch, camera);
        }
    }

    /**
     * Disposes every layer's texture that this background actually owns
     * (see {@link Layer#Layer(Texture, float, boolean)}) — a layer built
     * from an {@code AssetManager}-owned texture is left untouched, since
     * that texture is disposed once, elsewhere, at app shutdown.
     */
    public void dispose() {
        for (Layer layer : layers) {
            layer.dispose();
        }
    }

    /**
     * One scrolling, tileable background layer.
     * <p>
     * Implemented by repeatedly wrapping a single tileable {@link Texture} (via
     * {@link Texture.TextureWrap#Repeat}) and sliding its sampled texture
     * coordinates as the camera moves, rather than placing discrete copies of a
     * sprite next to each other — this covers an arbitrarily large/moving
     * viewport with a single draw call and no edge-of-world limit.
     */
    public static class Layer {

        private final Texture texture;
        private final TextureRegion region;
        private final float parallaxFactor;
        private final boolean ownsTexture;

        /**
         * Creates a layer that owns (and will dispose) its texture — for a
         * texture that exists only for this one layer instance, e.g.
         * {@link PlaceholderStarfield}'s procedurally-generated placeholder.
         *
         * @param texture        a seamlessly tileable texture; this class takes
         *                       ownership and will set it to repeat-wrap and
         *                       dispose it
         * @param parallaxFactor fraction of camera movement this layer scrolls
         *                       by — {@code 0} would be fixed to the screen,
         *                       {@code 1} would move exactly with the camera
         *                       (and so appear stationary in world space);
         *                       values well below 1 read as "distant"
         */
        public Layer(Texture texture, float parallaxFactor) {
            this(texture, parallaxFactor, true);
        }

        /**
         * Creates a layer, optionally without taking ownership of the
         * texture — pass {@code false} for a texture already owned
         * elsewhere (the shared {@code AssetManager}, e.g.
         * {@link GameAssets#BLUE_NEBULA}), so this layer only sets its
         * wrap mode and never disposes it.
         *
         * @param texture        a seamlessly tileable texture, set to
         *                       repeat-wrap by this constructor
         * @param parallaxFactor see {@link #Layer(Texture, float)}
         * @param ownsTexture    whether {@link #dispose()} should dispose
         *                       {@code texture} too
         */
        public Layer(Texture texture, float parallaxFactor, boolean ownsTexture) {
            this.texture = texture;
            texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
            this.region = new TextureRegion(texture);
            this.parallaxFactor = parallaxFactor;
            this.ownsTexture = ownsTexture;
        }

        private void render(SpriteBatch batch, OrthographicCamera camera) {
            float viewWidth = camera.viewportWidth * camera.zoom;
            float viewHeight = camera.viewportHeight * camera.zoom;

            // Negated: raw OpenGL texture V runs opposite to world/screen Y, so without this
            // the background would visibly scroll the wrong way on vertical movement only.
            float u = camera.position.x * parallaxFactor / texture.getWidth();
            float v = -camera.position.y * parallaxFactor / texture.getHeight();
            float uWidth = viewWidth / texture.getWidth();
            float vHeight = viewHeight / texture.getHeight();
            region.setRegion(u, v, u + uWidth, v + vHeight);

            batch.draw(region,
                camera.position.x - viewWidth / 2f, camera.position.y - viewHeight / 2f,
                viewWidth, viewHeight);
        }

        private void dispose() {
            if (ownsTexture) {
                texture.dispose();
            }
        }
    }
}
