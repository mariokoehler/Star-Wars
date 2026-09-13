package de.mkoehler.starwars.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * A short-lived piece of text that rises and fades out from a fixed spawn
 * point, played once each time some discrete event occurs — currently used
 * only for "+N XP" feedback (design.md — floating XP text), but the text/
 * color are caller-supplied, so any future one-off floating-text feedback
 * (not just XP) can reuse the same class/pool.
 * <p>
 * Unlike {@link OneShotParticleEffect}, which is repositioned externally
 * every frame to track a moving point, this effect owns its position from
 * the moment it's {@link #trigger}ed and drifts upward on its own from
 * there, independent of whatever it was spawned from - the same "detach and
 * float free" convention as classic MMO combat text - so nothing needs to
 * keep feeding it a live position after the initial spawn.
 */
public class FloatingTextEffect {

    private static final float DURATION_SECONDS = 1.4f;
    private static final float RISE_PIXELS_PER_SECOND = 40f;

    private final Color color = new Color(Color.WHITE);
    private String text = "";
    private float x;
    private float y;
    private float elapsedSeconds;
    private boolean playing;

    /**
     * (Re)starts playback from scratch at a fixed spawn point - safe to call
     * on an already-playing instance (pooled reuse), which simply restarts it.
     *
     * @param text      the text to display
     * @param xPixels   the spawn position, in pixels
     * @param yPixels   the spawn position, in pixels
     * @param textColor the text's color, copied rather than retained by reference
     */
    public void trigger(String text, float xPixels, float yPixels, Color textColor) {
        this.text = text;
        this.x = xPixels;
        this.y = yPixels;
        this.color.set(textColor);
        this.elapsedSeconds = 0f;
        this.playing = true;
    }

    /**
     * Advances this effect by one frame - a no-op once playback has finished.
     *
     * @param deltaTime time since the last frame, in seconds
     */
    public void update(float deltaTime) {
        if (!playing) {
            return;
        }
        elapsedSeconds += deltaTime;
        y += RISE_PIXELS_PER_SECOND * deltaTime;
        if (elapsedSeconds >= DURATION_SECONDS) {
            playing = false;
        }
    }

    /**
     * Draws this effect's text, horizontally centered on its current
     * position, fading out linearly over its full lifetime - a no-op if
     * playback has finished.
     *
     * @param batch  the batch to draw with, already begun with a matching projection matrix
     * @param font   the font to draw with - its color is overwritten every call, same
     *               "set it right before you draw" convention as {@code Client.drawDisplayName}
     * @param layout scratch layout, reused across calls to avoid a per-frame allocation
     */
    public void draw(SpriteBatch batch, BitmapFont font, GlyphLayout layout) {
        if (!playing) {
            return;
        }
        float alpha = 1f - (elapsedSeconds / DURATION_SECONDS);
        font.setColor(color.r, color.g, color.b, color.a * alpha);
        layout.setText(font, text);
        font.draw(batch, layout, x - layout.width / 2f, y + layout.height);
    }

    /**
     * Returns whether this effect is currently mid-playback - used to find a
     * free instance in a pool rather than always allocating a new one.
     *
     * @return {@code true} if this effect is still playing
     */
    public boolean isPlaying() {
        return playing;
    }
}
