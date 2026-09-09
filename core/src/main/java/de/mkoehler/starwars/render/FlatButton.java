package de.mkoehler.starwars.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;

/**
 * A clickable rectangle with centered live text and a solid, tintable
 * background — same "no pre-made art since the content/size is arbitrary"
 * reasoning and 1x1-white-{@link Texture}-stretched-and-tinted technique as
 * {@link Tooltip}, just for a persistent on-screen button instead of a
 * floating cursor-anchored bubble (so it draws at a fixed, caller-given
 * rectangle rather than clamped to the screen edges).
 * <p>
 * Used by {@link de.mkoehler.starwars.KeybindScreen} for its per-row
 * "press a key to rebind" buttons and its Reset/Back buttons — a Table of
 * baked-art buttons (like {@code ShipSelectionScreen}'s Start button) isn't
 * practical there since a button's label/highlight color changes at
 * runtime (bound key name, "listening" state) — and by
 * {@link de.mkoehler.starwars.ShipSelectionScreen} for its one "Keybinds"
 * button, so the two screens' buttons look identical.
 */
public class FlatButton implements Disposable {

    private final BitmapFont font;
    private final Texture backgroundPixel;
    private final GlyphLayout layout = new GlyphLayout();

    /**
     * Creates a button renderer using the game's "SF Distant Galaxy" font
     * ({@link GameFonts}) at the given size.
     *
     * @param fontSizePx the font size in pixels
     */
    public FlatButton(int fontSizePx) {
        font = GameFonts.generateSfDistantGalaxy(fontSizePx);

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        backgroundPixel = new Texture(pixmap);
        pixmap.dispose();
    }

    /**
     * Draws a solid {@code backgroundColor} rectangle at
     * ({@code x}, {@code y}, {@code width}, {@code height}) with
     * {@code text} centered on top in {@code textColor}.
     *
     * @param batch           the batch to draw with; must already be
     *                        between {@code begin()}/{@code end()}
     * @param text            the label to draw, centered
     * @param x               the button's screen X (bottom-left)
     * @param y               the button's screen Y (bottom-left)
     * @param width           the button's width
     * @param height          the button's height
     * @param backgroundColor the button's fill color (include alpha for translucency)
     * @param textColor       the label's color
     */
    public void draw(SpriteBatch batch, String text, float x, float y, float width, float height,
                      Color backgroundColor, Color textColor) {
        // batch.getColor() returns its own live, mutable Color field, not a snapshot - copy it
        // (cpy()) before setColor() below mutates that very object out from under us (same
        // gotcha Tooltip's own render() already documents).
        Color previousColor = batch.getColor().cpy();
        batch.setColor(backgroundColor);
        batch.draw(backgroundPixel, x, y, width, height);
        batch.setColor(previousColor);

        layout.setText(font, text);
        font.setColor(textColor);
        font.draw(batch, layout, x + (width - layout.width) / 2f, y + (height + layout.height) / 2f);
    }

    /**
     * Hit-tests a screen point against a button's rectangle — a static
     * utility (no button state involved) so callers can compute hover/click
     * state before deciding what color to pass to {@link #draw}.
     *
     * @param x      the rectangle's screen X (bottom-left)
     * @param y      the rectangle's screen Y (bottom-left)
     * @param width  the rectangle's width
     * @param height the rectangle's height
     * @param pointX the point's screen X (e.g. the mouse cursor)
     * @param pointY the point's screen Y
     * @return {@code true} if the point falls within the rectangle
     */
    public static boolean contains(float x, float y, float width, float height, float pointX, float pointY) {
        return pointX >= x && pointX <= x + width && pointY >= y && pointY <= y + height;
    }

    @Override
    public void dispose() {
        font.dispose();
        backgroundPixel.dispose();
    }
}
