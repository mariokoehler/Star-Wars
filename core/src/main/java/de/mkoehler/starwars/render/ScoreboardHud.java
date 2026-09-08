package de.mkoehler.starwars.render;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import de.mkoehler.starwars.net.messages.PlayerScoreEntry;

/**
 * Renders the TAB-held scoreboard overlay (design.md 2.11): a fixed
 * background panel ({@code textures/hud/scoreboard.png}, drawn at its native
 * 768x512 pixel size, not scaled) with the "NAME"/"XP"/"KILLS"/"DEATHS"
 * column headers already baked in, plus one live text row per
 * {@link PlayerScoreEntry} drawn on top in the real "SF Distant Galaxy" game
 * font ({@link GameFonts}) at the same 14px size the baked headers use.
 * <p>
 * Every pixel offset here (column X positions, the first row's Y) is given
 * in the panel image's own top-down pixel space, same convention as
 * {@link DialogLayout} — converted to screen space via
 * {@link DialogLayout#toScreenX}/{@link DialogLayout#toScreenY}.
 * <p>
 * The panel texture is read from the shared {@link AssetManager} (see
 * {@link ShipStatusHud}'s class Javadoc for the same reasoning); the font
 * is still generated fresh per instance via {@link GameFonts} (cheap, and
 * each instance's own to dispose), so this class still owns exactly that
 * one resource.
 */
public class ScoreboardHud implements Disposable {

    private static final int ROW_FONT_SIZE_PX = 14;

    /** Top-down Y of the first player row - matches the baked column headers directly above it. */
    private static final float FIRST_ROW_TOP_DOWN_Y = 85f;
    /**
     * Vertical gap between rows - untuned placeholder (not specified alongside the first row's
     * position); comfortably fits more than the design's 8-player cap within the panel's body.
     */
    private static final float ROW_HEIGHT_PX = 32f;

    private static final float COLUMN_NAME_X = 51f;
    private static final float COLUMN_XP_X = 307f;
    private static final float COLUMN_KILLS_X = 457f;
    private static final float COLUMN_DEATHS_X = 600f;

    private static final Color ROW_TEXT_COLOR = new Color(0.85f, 0.9f, 1f, 1f);

    private final Texture panel;
    private final BitmapFont font = GameFonts.generateSfDistantGalaxy(ROW_FONT_SIZE_PX);

    /**
     * Creates the widget, reading its panel texture from {@code assets}
     * immediately — it must already be loaded (see the class Javadoc).
     *
     * @param assets the shared asset manager to resolve the panel texture from
     */
    public ScoreboardHud(AssetManager assets) {
        panel = assets.get(GameAssets.SCOREBOARD_PANEL, Texture.class);
    }

    /**
     * Returns the panel's native pixel width - {@link #render} always draws
     * it at this size, so callers can use this to center/position it without
     * hardcoding the source art's dimensions themselves.
     *
     * @return the panel texture's width, in pixels
     */
    public float getPanelWidth() {
        return panel.getWidth();
    }

    /**
     * Returns the panel's native pixel height, see {@link #getPanelWidth()}.
     *
     * @return the panel texture's height, in pixels
     */
    public float getPanelHeight() {
        return panel.getHeight();
    }

    /**
     * Draws the panel with its bottom-left corner at ({@code x}, {@code y}),
     * at its native pixel size, and one row per entry starting at
     * {@link #FIRST_ROW_TOP_DOWN_Y} - callers are responsible for deciding
     * {@code entries}' order (design.md 2.11 doesn't specify one; the current
     * caller sorts by kills).
     *
     * @param batch   the batch to draw with; must already be between
     *                {@code begin()}/{@code end()}
     * @param x       the panel's screen X position
     * @param y       the panel's screen Y position
     * @param entries one row per currently-connected player, in display order
     */
    public void render(SpriteBatch batch, float x, float y, PlayerScoreEntry[] entries) {
        batch.draw(panel, x, y, panel.getWidth(), panel.getHeight());

        font.setColor(ROW_TEXT_COLOR);
        float rowTopDownY = FIRST_ROW_TOP_DOWN_Y;
        for (PlayerScoreEntry entry : entries) {
            float rowScreenY = DialogLayout.toScreenY(y, panel.getHeight(), rowTopDownY, 0f);
            font.draw(batch, entry.getDisplayName(), DialogLayout.toScreenX(x, COLUMN_NAME_X), rowScreenY);
            font.draw(batch, Integer.toString(entry.getXp()), DialogLayout.toScreenX(x, COLUMN_XP_X), rowScreenY);
            font.draw(batch, Integer.toString(entry.getKills()), DialogLayout.toScreenX(x, COLUMN_KILLS_X), rowScreenY);
            font.draw(batch, Integer.toString(entry.getDeaths()), DialogLayout.toScreenX(x, COLUMN_DEATHS_X), rowScreenY);
            rowTopDownY += ROW_HEIGHT_PX;
        }
    }

    @Override
    public void dispose() {
        font.dispose();
    }
}
