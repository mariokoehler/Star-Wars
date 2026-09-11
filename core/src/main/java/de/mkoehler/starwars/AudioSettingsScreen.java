package de.mkoehler.starwars;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import de.mkoehler.starwars.audio.AudioSettings;
import de.mkoehler.starwars.render.DialogLayout;
import de.mkoehler.starwars.render.FlatButton;
import de.mkoehler.starwars.render.GameAssets;
import de.mkoehler.starwars.render.GameFonts;
import de.mkoehler.starwars.render.ScrollingBackground;
import de.mkoehler.starwars.render.Slider;

/**
 * The Audio Settings screen (design.md — audio settings) — lets the player
 * adjust a master volume plus three per-category volumes (Weapons, Engines,
 * Sound Effects), each a slider from 0% (off) to 100%. The resulting volume
 * actually passed to any sound is always {@code masterVolume × categoryVolume}
 * — see {@link AudioSettings}.
 * <p>
 * Reachable from {@link ShipSelectionScreen} only — via its "AUDIO" button
 * or the <b>F11</b> key, right next to the existing "KEYBINDS"/<b>F12</b>
 * pair, matching exactly what was asked for and following the same
 * conventions {@link KeybindScreen} already established: raw
 * {@link SpriteBatch} + {@link Gdx#input} polling (no Scene2D/VisUI, this
 * screen needs neither text entry nor any other form widget), a generated
 * navy/gold background panel ({@link GameAssets#AUDIO_SETTINGS_BACKGROUND},
 * same Python/Pillow + "SF Distant Galaxy" technique, sized/laid out to
 * match this screen's own layout constants below rather than reused
 * as-is), and every dynamic element (row labels, slider fill/handle,
 * percentage readouts, footer buttons) live-drawn on top of it.
 * <p>
 * Dragging a slider updates {@link AudioSettings} — and therefore every
 * currently-playing sound that reads it — immediately, every frame, but
 * only <em>persists</em> to the local audio-settings file once, when the
 * drag ends (mouse released), rather than writing the file on every one of
 * a drag's many per-frame updates. "Reset to Defaults" (100% on every
 * slider) saves immediately, same as {@link KeybindScreen}'s own button.
 * Back (or ESC, when not mid-drag) returns to {@link ShipSelectionScreen}.
 */
public class AudioSettingsScreen implements Screen {

    private static final String TAG = "AudioSettingsScreen";

    private static final float PANEL_WIDTH = 640f;
    private static final float PANEL_HEIGHT = 620f;

    private static final String[] ROW_LABELS = {"MASTER VOLUME", "WEAPONS", "ENGINES", "SOUND EFFECTS"};

    private static final float LABEL_X = 40f;
    private static final float LABEL_HEIGHT = 28f;
    private static final float FIRST_ROW_LABEL_TOP = 140f;
    private static final float ROW_BLOCK = 110f;
    /** Vertical gap from a row's label top edge to its slider track top edge. */
    private static final float LABEL_TO_TRACK_GAP = 42f;

    private static final float TRACK_X = 40f;
    private static final float TRACK_WIDTH = 460f;
    private static final float TRACK_HEIGHT = 26f;
    private static final float VALUE_LABEL_GAP_X = 16f;

    private static final float BUTTONS_TOP = 552f;
    private static final float BUTTON_HEIGHT = 56f;
    private static final float RESET_BUTTON_X = 32f;
    private static final float RESET_BUTTON_WIDTH = 276f;
    private static final float BACK_BUTTON_X = 332f;
    private static final float BACK_BUTTON_WIDTH = 276f;

    private static final int ROW_LABEL_FONT_SIZE_PX = 20;
    private static final int VALUE_FONT_SIZE_PX = 18;
    private static final int FOOTER_BUTTON_FONT_SIZE_PX = 18;

    private static final Color ROW_LABEL_COLOR = new Color(0.85f, 0.9f, 1f, 1f);
    private static final Color VALUE_TEXT_COLOR = new Color(0.92f, 0.95f, 1f, 1f);
    private static final Color RAIL_COLOR = new Color(10 / 255f, 26 / 255f, 55 / 255f, 0.9f);
    private static final Color FILL_COLOR = new Color(25 / 255f, 60 / 255f, 110 / 255f, 0.95f);
    private static final Color HANDLE_COLOR = new Color(221 / 255f, 190 / 255f, 124 / 255f, 1f);
    private static final Color RESET_IDLE_BG = new Color(80 / 255f, 20 / 255f, 20 / 255f, 0.85f);
    private static final Color RESET_HOVER_BG = new Color(140 / 255f, 40 / 255f, 40 / 255f, 0.9f);
    private static final Color BACK_IDLE_BG = new Color(10 / 255f, 26 / 255f, 55 / 255f, 0.85f);
    private static final Color BACK_HOVER_BG = new Color(25 / 255f, 60 / 255f, 110 / 255f, 0.9f);
    private static final Color FOOTER_TEXT_COLOR = new Color(0.94f, 0.87f, 0.66f, 1f);

    /** Fraction of the screen-top-to-panel-top gap the logo's height fills - same placeholder as the other menu-style screens. */
    private static final float LOGO_HEIGHT_FRACTION_OF_GAP = 0.7f;

    /** Background drift - same fixed diagonal direction/speed as {@code ShipSelectionScreen}/{@code KeybindScreen}, untuned placeholders. */
    private static final float BACKGROUND_DRIFT_DIRECTION_DEGREES = 25f;
    private static final float BACKGROUND_DRIFT_SPEED_PIXELS_PER_SECOND = 15f;

    private final StarWarsGame game;
    private final ConnectionInfo connectionInfo;

    private SpriteBatch batch;
    private OrthographicCamera camera;
    private ScrollingBackground background;
    private Texture logoTexture;
    private Texture panelTexture;
    private FlatButton flatButton;
    private Slider slider;
    private BitmapFont rowLabelFont;
    private final GlyphLayout rowLabelLayout = new GlyphLayout();

    private AudioSettings audioSettings;

    /**
     * The row index currently being dragged (0-3), or {@code -1} when no
     * drag is in progress — set the moment the mouse is pressed down on a
     * row's track/handle, cleared (and {@link AudioSettings} saved exactly
     * once) the moment the mouse is released.
     */
    private int draggingRowIndex = -1;

    /**
     * Ignores input on this screen's first {@link #render} call - same
     * "just pressed" leak guard {@link ShipSelectionScreen}/{@link KeybindScreen}
     * already use (e.g. a leftover F11 press from the previous screen could
     * otherwise immediately be mis-read as a click on this screen's very
     * first frame).
     */
    private boolean firstFrame = true;

    /**
     * Creates the screen.
     *
     * @param game           the game to switch back to {@link ShipSelectionScreen}
     *                       from once the player presses Back or ESC
     * @param connectionInfo the already-validated login to pass back to
     *                       {@link ShipSelectionScreen}
     */
    public AudioSettingsScreen(StarWarsGame game, ConnectionInfo connectionInfo) {
        this.game = game;
        this.connectionInfo = connectionInfo;
    }

    @Override
    public void show() {
        long showStartMillis = System.currentTimeMillis();

        batch = new SpriteBatch();
        camera = new OrthographicCamera();
        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        background = new ScrollingBackground(game.getAssets().get(GameAssets.MENU_STARFIELD, Texture.class),
            BACKGROUND_DRIFT_DIRECTION_DEGREES, BACKGROUND_DRIFT_SPEED_PIXELS_PER_SECOND);
        logoTexture = game.getAssets().get(GameAssets.LOGO, Texture.class);
        panelTexture = game.getAssets().get(GameAssets.AUDIO_SETTINGS_BACKGROUND, Texture.class);
        flatButton = new FlatButton(FOOTER_BUTTON_FONT_SIZE_PX);
        slider = new Slider(VALUE_FONT_SIZE_PX);
        rowLabelFont = GameFonts.generateSfDistantGalaxy(ROW_LABEL_FONT_SIZE_PX);

        audioSettings = game.getAudioSettings();

        Gdx.app.log(TAG, "show() took " + (System.currentTimeMillis() - showStartMillis) + "ms total");
    }

    @Override
    public void render(float deltaTime) {
        ScreenUtils.clear(0f, 0f, 0f, 1f);
        background.update(deltaTime);

        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();
        float panelScreenX = (screenWidth - PANEL_WIDTH) / 2f;
        float panelScreenY = (screenHeight - PANEL_HEIGHT) / 2f;

        // Raw input Y is measured down from the window's top-left; this screen's own camera is
        // Y-up from the bottom-left (same convention every other menu-style screen here uses).
        float mouseX = Gdx.input.getX();
        float mouseY = screenHeight - Gdx.input.getY();

        if (handleInput(panelScreenX, panelScreenY, mouseX, mouseY)) {
            // Just switched back to ShipSelectionScreen and disposed this screen's own
            // batch/textures - same anti-crash pattern every other screen transition here follows.
            return;
        }

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        background.render(batch, screenWidth, screenHeight);
        drawLogo(screenWidth, panelScreenY + PANEL_HEIGHT, screenHeight);
        batch.draw(panelTexture, panelScreenX, panelScreenY, PANEL_WIDTH, PANEL_HEIGHT);

        for (int i = 0; i < ROW_LABELS.length; i++) {
            drawRow(panelScreenX, panelScreenY, i);
        }
        drawFooterButtons(panelScreenX, panelScreenY, mouseX, mouseY);

        batch.end();
    }

    /**
     * @return {@code true} if this screen just switched away — the caller
     * must not touch this screen's (now-disposed) batch/textures again
     * this frame
     */
    private boolean handleInput(float panelScreenX, float panelScreenY, float mouseX, float mouseY) {
        if (firstFrame) {
            firstFrame = false;
            return false;
        }

        if (draggingRowIndex != -1) {
            if (Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
                float trackX = DialogLayout.toScreenX(panelScreenX, TRACK_X);
                setRowValue(draggingRowIndex, Slider.fractionForPointerX(mouseX, trackX, TRACK_WIDTH));
            } else {
                // Drag just ended - persist once here rather than on every one of the drag's
                // many per-frame updates above (design.md — audio settings).
                draggingRowIndex = -1;
                audioSettings.save();
            }
            return false;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            goBack();
            return true;
        }

        for (int i = 0; i < ROW_LABELS.length; i++) {
            float trackX = DialogLayout.toScreenX(panelScreenX, TRACK_X);
            float trackY = trackScreenY(panelScreenY, i);
            if (Slider.containsPointer(trackX, trackY, TRACK_WIDTH, TRACK_HEIGHT, mouseX, mouseY)
                    && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
                draggingRowIndex = i;
                setRowValue(i, Slider.fractionForPointerX(mouseX, trackX, TRACK_WIDTH));
                return false;
            }
        }

        float resetX = DialogLayout.toScreenX(panelScreenX, RESET_BUTTON_X);
        float backX = DialogLayout.toScreenX(panelScreenX, BACK_BUTTON_X);
        float footerY = DialogLayout.toScreenY(panelScreenY, PANEL_HEIGHT, BUTTONS_TOP, BUTTON_HEIGHT);
        if (FlatButton.contains(resetX, footerY, RESET_BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY)
                && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            audioSettings.setMasterVolume(1f);
            audioSettings.setWeaponsVolume(1f);
            audioSettings.setEnginesVolume(1f);
            audioSettings.setSoundEffectsVolume(1f);
            audioSettings.save();
            return false;
        }
        if (FlatButton.contains(backX, footerY, BACK_BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY)
                && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            goBack();
            return true;
        }
        return false;
    }

    private void goBack() {
        game.setScreen(new ShipSelectionScreen(game, connectionInfo));
        dispose();
    }

    /**
     * Returns row {@code index}'s current value from {@link #audioSettings}.
     *
     * @param index the row index, matching {@link #ROW_LABELS}' order
     * @return the row's current fraction, in {@code [0, 1]}
     */
    private float getRowValue(int index) {
        return switch (index) {
            case 0 -> audioSettings.getMasterVolume();
            case 1 -> audioSettings.getWeaponsVolume();
            case 2 -> audioSettings.getEnginesVolume();
            case 3 -> audioSettings.getSoundEffectsVolume();
            default -> throw new IllegalArgumentException("No such row: " + index);
        };
    }

    /**
     * Sets row {@code index}'s value on {@link #audioSettings} — takes
     * effect immediately for any currently-playing sound that reads it,
     * but isn't persisted to disk here; see {@link #handleInput}'s drag-end
     * handling for when that happens.
     *
     * @param index the row index, matching {@link #ROW_LABELS}' order
     * @param value the new fraction, in {@code [0, 1]}
     */
    private void setRowValue(int index, float value) {
        switch (index) {
            case 0 -> audioSettings.setMasterVolume(value);
            case 1 -> audioSettings.setWeaponsVolume(value);
            case 2 -> audioSettings.setEnginesVolume(value);
            case 3 -> audioSettings.setSoundEffectsVolume(value);
            default -> throw new IllegalArgumentException("No such row: " + index);
        }
    }

    private float trackScreenY(float panelScreenY, int index) {
        float trackTopDownY = FIRST_ROW_LABEL_TOP + index * ROW_BLOCK + LABEL_TO_TRACK_GAP;
        return DialogLayout.toScreenY(panelScreenY, PANEL_HEIGHT, trackTopDownY, TRACK_HEIGHT);
    }

    private void drawRow(float panelScreenX, float panelScreenY, int index) {
        float labelTopDownY = FIRST_ROW_LABEL_TOP + index * ROW_BLOCK;
        float labelScreenX = DialogLayout.toScreenX(panelScreenX, LABEL_X);
        float labelScreenY = DialogLayout.toScreenY(panelScreenY, PANEL_HEIGHT, labelTopDownY, LABEL_HEIGHT);
        drawLeftAlignedLabel(ROW_LABELS[index], labelScreenX, labelScreenY);

        float trackX = DialogLayout.toScreenX(panelScreenX, TRACK_X);
        float trackY = trackScreenY(panelScreenY, index);
        slider.draw(batch, getRowValue(index), trackX, trackY, TRACK_WIDTH, TRACK_HEIGHT,
            RAIL_COLOR, FILL_COLOR, HANDLE_COLOR, VALUE_TEXT_COLOR, VALUE_LABEL_GAP_X);
    }

    /**
     * Draws {@code text} left-aligned, vertically centered within one row's
     * {@link #LABEL_HEIGHT} band starting at {@code rowScreenY} — same
     * technique {@code KeybindScreen}'s own row labels use.
     */
    private void drawLeftAlignedLabel(String text, float x, float rowScreenY) {
        rowLabelLayout.setText(rowLabelFont, text);
        rowLabelFont.setColor(ROW_LABEL_COLOR);
        rowLabelFont.draw(batch, rowLabelLayout, x, rowScreenY + (LABEL_HEIGHT + rowLabelLayout.height) / 2f);
    }

    private void drawFooterButtons(float panelScreenX, float panelScreenY, float mouseX, float mouseY) {
        float resetX = DialogLayout.toScreenX(panelScreenX, RESET_BUTTON_X);
        float backX = DialogLayout.toScreenX(panelScreenX, BACK_BUTTON_X);
        float footerY = DialogLayout.toScreenY(panelScreenY, PANEL_HEIGHT, BUTTONS_TOP, BUTTON_HEIGHT);

        boolean resetHover = draggingRowIndex == -1
            && FlatButton.contains(resetX, footerY, RESET_BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY);
        boolean backHover = draggingRowIndex == -1
            && FlatButton.contains(backX, footerY, BACK_BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY);

        flatButton.draw(batch, "RESET TO DEFAULTS", resetX, footerY, RESET_BUTTON_WIDTH, BUTTON_HEIGHT,
            resetHover ? RESET_HOVER_BG : RESET_IDLE_BG, FOOTER_TEXT_COLOR);
        flatButton.draw(batch, "BACK", backX, footerY, BACK_BUTTON_WIDTH, BUTTON_HEIGHT,
            backHover ? BACK_HOVER_BG : BACK_IDLE_BG, FOOTER_TEXT_COLOR);
    }

    private void drawLogo(float screenWidth, float panelTopY, float screenHeight) {
        float gapHeight = screenHeight - panelTopY;
        float logoHeight = gapHeight * LOGO_HEIGHT_FRACTION_OF_GAP;
        float logoWidth = logoHeight * (logoTexture.getWidth() / (float) logoTexture.getHeight());
        float logoX = (screenWidth - logoWidth) / 2f;
        float logoY = panelTopY + (gapHeight - logoHeight) / 2f;
        batch.draw(logoTexture, logoX, logoY, logoWidth, logoHeight);
    }

    @Override
    public void resize(int width, int height) {
        camera.setToOrtho(false, width, height);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        batch.dispose();
        // background/logoTexture/panelTexture are owned by StarWarsGame#getAssets() (design.md -
        // asset loading), not this screen - disposed once, at app shutdown, not here.
        flatButton.dispose();
        slider.dispose();
        rowLabelFont.dispose();
    }
}
