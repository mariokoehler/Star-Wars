package de.mkoehler.starwars.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;

/**
 * A horizontal, draggable volume slider (design.md — audio settings) — a
 * rail, a filled portion up to the current fraction, a handle, and a
 * percentage readout, all live-drawn with the same 1×1-white-{@link Texture}
 * -stretched-and-tinted technique {@link FlatButton}/{@link Tooltip} already
 * use, rather than pre-made art — the fill/handle position changes
 * continuously while dragging, so there's nothing fixed a baked asset could
 * usefully represent.
 * <p>
 * Stateless about which value it's showing — {@link de.mkoehler.starwars.AudioSettingsScreen}
 * owns the actual {@code float} fraction per row and passes it into
 * {@link #draw} every frame, the same "widget draws whatever it's told,
 * screen owns the state" split {@link FlatButton} already follows. Hit-
 * testing ({@link #containsPointer}) and turning a pointer position back
 * into a fraction ({@link #fractionForPointerX}) are separate static
 * utilities for the same reason {@link FlatButton#contains} is — the caller
 * decides what to do with a click/drag before this class draws anything.
 */
public class Slider implements Disposable {

    /** How far the handle extends above/below the track — makes it easier to grab than the bare track height. */
    private static final float HANDLE_OVERHANG = 8f;
    private static final float HANDLE_WIDTH = 14f;

    private final BitmapFont valueFont;
    private final Texture pixel;
    private final GlyphLayout layout = new GlyphLayout();

    /**
     * Creates a slider renderer using the game's "SF Distant Galaxy" font
     * ({@link GameFonts}) for its percentage readout.
     *
     * @param valueFontSizePx the percentage readout's font size in pixels
     */
    public Slider(int valueFontSizePx) {
        valueFont = GameFonts.generateSfDistantGalaxy(valueFontSizePx);

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        pixel = new Texture(pixmap);
        pixmap.dispose();
    }

    /**
     * Draws the rail, filled portion, handle, and a rounded percentage
     * readout to the right of the track.
     *
     * @param batch          the batch to draw with; must already be between {@code begin()}/{@code end()}
     * @param fraction       the current value, in {@code [0, 1]}
     * @param trackX         the track's screen X (bottom-left)
     * @param trackY         the track's screen Y (bottom-left)
     * @param trackWidth     the track's width
     * @param trackHeight    the track's height
     * @param railColor      the empty rail's color
     * @param fillColor      the filled portion's color, from the track's left edge up to {@code fraction}
     * @param handleColor    the handle's color
     * @param valueTextColor the percentage readout's color
     * @param valueLabelGapX the gap between the track's right edge and the percentage readout
     */
    public void draw(SpriteBatch batch, float fraction, float trackX, float trackY, float trackWidth, float trackHeight,
                      Color railColor, Color fillColor, Color handleColor, Color valueTextColor, float valueLabelGapX) {
        // batch.getColor() returns its own live, mutable Color field, not a snapshot - copy it
        // (cpy()) before setColor() below mutates that very object out from under us (same
        // gotcha FlatButton/Tooltip's own render() already documents).
        Color previousColor = batch.getColor().cpy();

        batch.setColor(railColor);
        batch.draw(pixel, trackX, trackY, trackWidth, trackHeight);

        float fillWidth = trackWidth * fraction;
        if (fillWidth > 0f) {
            batch.setColor(fillColor);
            batch.draw(pixel, trackX, trackY, fillWidth, trackHeight);
        }

        float handleCenterX = handleCenterX(trackX, trackWidth, fraction);
        batch.setColor(handleColor);
        batch.draw(pixel, handleCenterX - HANDLE_WIDTH / 2f, trackY - HANDLE_OVERHANG,
            HANDLE_WIDTH, trackHeight + 2f * HANDLE_OVERHANG);

        batch.setColor(previousColor);

        String valueText = Math.round(fraction * 100f) + "%";
        layout.setText(valueFont, valueText);
        valueFont.setColor(valueTextColor);
        valueFont.draw(batch, layout, trackX + trackWidth + valueLabelGapX, trackY + (trackHeight + layout.height) / 2f);
    }

    /**
     * Returns where the handle's center sits for a given fraction along the track.
     *
     * @param trackX     the track's screen X (bottom-left)
     * @param trackWidth the track's width
     * @param fraction   the value, in {@code [0, 1]}
     * @return the handle's center X, in screen coordinates
     */
    public static float handleCenterX(float trackX, float trackWidth, float fraction) {
        return trackX + trackWidth * fraction;
    }

    /**
     * Hit-tests a screen point against the track, extended vertically by
     * {@link #HANDLE_OVERHANG} on both sides — so clicking just above/below
     * the thin track still grabs the (taller) handle drawn on top of it.
     *
     * @param trackX      the track's screen X (bottom-left)
     * @param trackY      the track's screen Y (bottom-left)
     * @param trackWidth  the track's width
     * @param trackHeight the track's height
     * @param pointerX    the point's screen X (e.g. the mouse cursor)
     * @param pointerY    the point's screen Y
     * @return {@code true} if the point falls within the track/handle's combined bounds
     */
    public static boolean containsPointer(float trackX, float trackY, float trackWidth, float trackHeight,
                                           float pointerX, float pointerY) {
        float top = trackY - HANDLE_OVERHANG;
        float height = trackHeight + 2f * HANDLE_OVERHANG;
        return pointerX >= trackX && pointerX <= trackX + trackWidth && pointerY >= top && pointerY <= top + height;
    }

    /**
     * Converts a pointer's X position into a track fraction, clamped to
     * {@code [0, 1]} so dragging past either end of the track simply pins
     * the value at 0 or 1 rather than doing nothing.
     *
     * @param pointerX   the pointer's screen X
     * @param trackX     the track's screen X (bottom-left)
     * @param trackWidth the track's width
     * @return the resulting fraction, in {@code [0, 1]}
     */
    public static float fractionForPointerX(float pointerX, float trackX, float trackWidth) {
        if (trackWidth <= 0f) {
            return 0f;
        }
        return MathUtils.clamp((pointerX - trackX) / trackWidth, 0f, 1f);
    }

    @Override
    public void dispose() {
        valueFont.dispose();
        pixel.dispose();
    }
}
