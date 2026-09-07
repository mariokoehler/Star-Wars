package de.mkoehler.starwars.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;

/**
 * A small floating text bubble anchored to an arbitrary screen position
 * (typically the mouse cursor) — used by the Ship Selection screen to
 * explain why a locked ship's padlock is showing white or "tier too high"
 * (design.md — Ship unlocks/Ship Tree), since neither padlock's baked-in
 * text says <i>how much</i> XP is missing or <i>which</i> ship to unlock
 * first.
 * <p>
 * There's no pre-made art for this (unlike every other overlay in this
 * project) since its content is arbitrary, dynamic text — so it draws its
 * own padded background box via a 1x1 white {@link Texture} tinted and
 * stretched to size, rather than pulling in a {@code ShapeRenderer} just
 * for one solid rectangle.
 */
public class Tooltip implements Disposable {

    private static final float PADDING_X = 12f;
    private static final float PADDING_Y = 8f;
    private static final Color BACKGROUND_COLOR = new Color(0f, 0f, 0f, 0.85f);
    private static final Color TEXT_COLOR = Color.WHITE;

    private final BitmapFont font;
    private final Texture backgroundPixel;
    private final GlyphLayout layout = new GlyphLayout();

    /**
     * Creates a tooltip renderer using the game's "SF Distant Galaxy" font
     * ({@link GameFonts}) at the given size.
     *
     * @param fontSizePx the font size in pixels
     */
    public Tooltip(int fontSizePx) {
        font = GameFonts.generateSfDistantGalaxy(fontSizePx);

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        backgroundPixel = new Texture(pixmap);
        pixmap.dispose();
    }

    /**
     * Draws {@code text} in a padded background box, anchored so its
     * bottom-left corner sits at ({@code anchorX}, {@code anchorY}) — except
     * it's pulled back inside {@code screenWidth}/{@code screenHeight} if it
     * would otherwise run off the right or top edge, so a tooltip near the
     * mouse cursor at the edge of the window still stays fully readable.
     *
     * @param batch        the batch to draw with; must already be between
     *                     {@code begin()}/{@code end()}
     * @param text         the text to show
     * @param anchorX      the preferred bottom-left screen X
     * @param anchorY      the preferred bottom-left screen Y
     * @param screenWidth  the current screen width, for right-edge clamping
     * @param screenHeight the current screen height, for top-edge clamping
     */
    public void render(SpriteBatch batch, String text, float anchorX, float anchorY, float screenWidth, float screenHeight) {
        layout.setText(font, text);
        float boxWidth = layout.width + PADDING_X * 2f;
        float boxHeight = layout.height + PADDING_Y * 2f;

        float x = Math.max(0f, Math.min(anchorX, screenWidth - boxWidth));
        float y = Math.max(0f, Math.min(anchorY, screenHeight - boxHeight));

        // batch.getColor() returns its own live, mutable Color field, not a snapshot - copy it
        // (cpy()) before setColor() below mutates that very object out from under us.
        Color previousColor = batch.getColor().cpy();
        batch.setColor(BACKGROUND_COLOR);
        batch.draw(backgroundPixel, x, y, boxWidth, boxHeight);
        batch.setColor(previousColor);

        font.setColor(TEXT_COLOR);
        font.draw(batch, layout, x + PADDING_X, y + boxHeight - PADDING_Y);
    }

    @Override
    public void dispose() {
        font.dispose();
        backgroundPixel.dispose();
    }
}
