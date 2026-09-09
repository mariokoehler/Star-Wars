package de.mkoehler.starwars;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import de.mkoehler.starwars.input.GameAction;
import de.mkoehler.starwars.input.KeyBindings;
import de.mkoehler.starwars.render.DialogLayout;
import de.mkoehler.starwars.render.FlatButton;
import de.mkoehler.starwars.render.GameAssets;
import de.mkoehler.starwars.render.GameFonts;
import de.mkoehler.starwars.render.ScrollingBackground;

/**
 * The Keybind Settings screen (design.md 3.8/5.2) — lets the player remap
 * every gameplay action listed in {@link GameAction} to a different key,
 * so a control scheme built around WASD isn't forced on players with a
 * physically different keyboard layout (design.md 3.8's own motivation:
 * this project's players are in the UK, Belgium and Norway — WASD is not
 * universally sensible, most notably on a Belgian AZERTY keyboard).
 * <p>
 * Reachable from {@link ShipSelectionScreen} only — via its "KEYBINDS"
 * button or the <b>F12</b> key — matching exactly what was asked for.
 * <b>ESC (leave match), the cursor keys, and ENTER are not remappable</b>
 * and never shown here — see {@link GameAction}'s Javadoc for why.
 * <p>
 * Click a row's key button to enter "listening" mode for that action (the
 * button highlights and reads "PRESS A KEY..."); the next keypress rebinds
 * it (see {@link KeyBindings#rebind}, which also swaps away a conflicting
 * existing binding rather than leaving one key doing double duty), or ESC
 * cancels without changing anything. Every change — a rebind or "Reset to
 * Defaults" — is saved to the local keybinds file immediately (design.md
 * 5.2), so there's no separate Save step; Back (or ESC, when not
 * mid-capture) just returns to {@link ShipSelectionScreen}.
 * <p>
 * <b>Deviates from design.md 4.4's original assumption</b> that this
 * screen (like the Connect Dialog) would need Scene2D/VisUI for form
 * widgets: it turns out to need neither text entry nor any other widget
 * VisUI adds real value for, just clickable rows and a one-key capture —
 * exactly what {@link ShipSelectionScreen}'s existing raw-{@link SpriteBatch}
 * + {@link Gdx#input} polling style already handles well, so this screen
 * follows that style instead (design.md updated to match). The one thing
 * that <i>is</i> pre-made art is {@link GameAssets#KEYBINDS_BACKGROUND} — a
 * generated panel (Python/Pillow + the game's own "SF Distant Galaxy"
 * font, same technique as the combat-lock warning banner/radar HUD art,
 * see CLAUDE.md) matching {@code ConnectScreen}/{@code ShipSelectionScreen}'s
 * navy/gold palette; every row and button on top of it is live-drawn via
 * {@link FlatButton} instead, since a row's key label and highlight state
 * change at runtime — see this class's own layout constants, which
 * <b>must stay in sync</b> with the Python generator script's own
 * (throwaway, not committed) copies of the same numbers.
 */
public class KeybindScreen implements Screen {

    private static final String TAG = "KeybindScreen";

    private static final float PANEL_WIDTH = 640f;
    private static final float PANEL_HEIGHT = 816f;

    private static final float LABEL_X = 40f;
    private static final float KEY_BUTTON_WIDTH = 170f;
    private static final float KEY_BUTTON_X = 438f;

    private static final float FIRST_ROW_TOP = 138f;
    private static final float ROW_HEIGHT = 40f;
    private static final float ROW_BLOCK = 48f;
    private static final float KEY_BUTTON_HEIGHT = 34f;
    private static final float KEY_BUTTON_Y_INSET = 3f;

    private static final float BUTTONS_TOP = 730f;
    private static final float BUTTON_HEIGHT = 56f;
    private static final float RESET_BUTTON_X = 32f;
    private static final float RESET_BUTTON_WIDTH = 276f;
    private static final float BACK_BUTTON_X = 332f;
    private static final float BACK_BUTTON_WIDTH = 276f;

    private static final int ROW_LABEL_FONT_SIZE_PX = 18;
    private static final int KEY_BUTTON_FONT_SIZE_PX = 16;

    private static final Color ROW_LABEL_COLOR = new Color(0.85f, 0.9f, 1f, 1f);
    private static final Color KEY_IDLE_BG = new Color(10 / 255f, 26 / 255f, 55 / 255f, 0.85f);
    private static final Color KEY_HOVER_BG = new Color(25 / 255f, 60 / 255f, 110 / 255f, 0.9f);
    private static final Color KEY_LISTENING_BG = new Color(221 / 255f, 190 / 255f, 124 / 255f, 0.95f);
    private static final Color KEY_IDLE_TEXT = new Color(0.92f, 0.95f, 1f, 1f);
    private static final Color KEY_LISTENING_TEXT = new Color(0.05f, 0.05f, 0.1f, 1f);
    private static final Color RESET_IDLE_BG = new Color(80 / 255f, 20 / 255f, 20 / 255f, 0.85f);
    private static final Color RESET_HOVER_BG = new Color(140 / 255f, 40 / 255f, 40 / 255f, 0.9f);
    private static final Color FOOTER_TEXT_COLOR = new Color(0.94f, 0.87f, 0.66f, 1f);

    /** Fraction of the screen-top-to-panel-top gap the logo's height fills - same placeholder as the other menu-style screens. */
    private static final float LOGO_HEIGHT_FRACTION_OF_GAP = 0.7f;

    /** Background drift - same fixed diagonal direction/speed as {@code ShipSelectionScreen}/{@code ConnectScreen}, untuned placeholders. */
    private static final float BACKGROUND_DRIFT_DIRECTION_DEGREES = 25f;
    private static final float BACKGROUND_DRIFT_SPEED_PIXELS_PER_SECOND = 15f;

    private static final GameAction[] ACTIONS = GameAction.values();

    private final StarWarsGame game;
    private final ConnectionInfo connectionInfo;

    private SpriteBatch batch;
    private OrthographicCamera camera;
    private ScrollingBackground background;
    private Texture logoTexture;
    private Texture panelTexture;
    private FlatButton flatButton;
    private BitmapFont rowLabelFont;
    private final GlyphLayout rowLabelLayout = new GlyphLayout();

    private KeyBindings keyBindings;

    /**
     * The action currently being listened for (design.md 5.2 — a row's key
     * button was just clicked), or {@code null} when no capture is in
     * progress. While non-null, every other row/button ignores mouse
     * input — only the temporary {@link InputAdapter} installed by
     * {@link #startListening} can resolve or cancel the capture.
     */
    private GameAction listeningFor;

    /**
     * Ignores input on this screen's first {@link #render} call — same
     * "just pressed" leak guard already used by {@link ShipSelectionScreen}
     * (e.g. a leftover F12 press from the previous screen could otherwise
     * immediately be mis-read as a click on this screen's very first frame).
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
    public KeybindScreen(StarWarsGame game, ConnectionInfo connectionInfo) {
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
        panelTexture = game.getAssets().get(GameAssets.KEYBINDS_BACKGROUND, Texture.class);
        flatButton = new FlatButton(KEY_BUTTON_FONT_SIZE_PX);
        rowLabelFont = GameFonts.generateSfDistantGalaxy(ROW_LABEL_FONT_SIZE_PX);

        keyBindings = game.getKeyBindings();

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
        // Y-up from the bottom-left (same convention ShipSelectionScreen/Client's hudCamera use).
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

        for (int i = 0; i < ACTIONS.length; i++) {
            drawRow(panelScreenX, panelScreenY, i, mouseX, mouseY);
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
        if (listeningFor != null) {
            // Only the temporary InputAdapter's keyDown can resolve/cancel a capture in progress -
            // ignore every other input (including a stray click on Reset/Back) until it does.
            return false;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            goBack();
            return true;
        }

        for (int i = 0; i < ACTIONS.length; i++) {
            float rowTopDownY = FIRST_ROW_TOP + i * ROW_BLOCK;
            float x = DialogLayout.toScreenX(panelScreenX, KEY_BUTTON_X);
            float y = DialogLayout.toScreenY(panelScreenY, PANEL_HEIGHT, rowTopDownY + KEY_BUTTON_Y_INSET, KEY_BUTTON_HEIGHT);
            if (FlatButton.contains(x, y, KEY_BUTTON_WIDTH, KEY_BUTTON_HEIGHT, mouseX, mouseY)
                    && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
                startListening(ACTIONS[i]);
                return false;
            }
        }

        float resetX = DialogLayout.toScreenX(panelScreenX, RESET_BUTTON_X);
        float backX = DialogLayout.toScreenX(panelScreenX, BACK_BUTTON_X);
        float footerY = DialogLayout.toScreenY(panelScreenY, PANEL_HEIGHT, BUTTONS_TOP, BUTTON_HEIGHT);
        if (FlatButton.contains(resetX, footerY, RESET_BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY)
                && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            keyBindings.resetToDefaults();
            keyBindings.save();
            return false;
        }
        if (FlatButton.contains(backX, footerY, BACK_BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY)
                && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            goBack();
            return true;
        }
        return false;
    }

    /**
     * Enters "listening" mode for {@code action} — installs a temporary
     * {@link InputAdapter} to capture the very next keypress (design.md 5.2's
     * "press a key to bind" capture field), since there's no single
     * {@link Gdx#input} polling call for "whichever key was just pressed,
     * if any." ESC cancels without changing the binding; any other key
     * rebinds it and saves immediately.
     *
     * @param action the action to rebind
     */
    private void startListening(GameAction action) {
        listeningFor = action;
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode != Input.Keys.ESCAPE) {
                    keyBindings.rebind(action, keycode);
                    keyBindings.save();
                }
                stopListening();
                return true;
            }
        });
    }

    private void stopListening() {
        listeningFor = null;
        Gdx.input.setInputProcessor(null);
    }

    private void goBack() {
        game.setScreen(new ShipSelectionScreen(game, connectionInfo));
        dispose();
    }

    private void drawRow(float panelScreenX, float panelScreenY, int index, float mouseX, float mouseY) {
        GameAction action = ACTIONS[index];
        float rowTopDownY = FIRST_ROW_TOP + index * ROW_BLOCK;

        float labelScreenX = DialogLayout.toScreenX(panelScreenX, LABEL_X);
        float rowScreenY = DialogLayout.toScreenY(panelScreenY, PANEL_HEIGHT, rowTopDownY, ROW_HEIGHT);
        drawLeftAlignedLabel(action.getDisplayName(), labelScreenX, rowScreenY);

        float keyX = DialogLayout.toScreenX(panelScreenX, KEY_BUTTON_X);
        float keyY = DialogLayout.toScreenY(panelScreenY, PANEL_HEIGHT, rowTopDownY + KEY_BUTTON_Y_INSET, KEY_BUTTON_HEIGHT);
        boolean isListeningRow = action == listeningFor;
        boolean hovering = listeningFor == null
            && FlatButton.contains(keyX, keyY, KEY_BUTTON_WIDTH, KEY_BUTTON_HEIGHT, mouseX, mouseY);

        String keyLabel = isListeningRow ? "PRESS A KEY..." : Input.Keys.toString(keyBindings.get(action));
        Color background = isListeningRow ? KEY_LISTENING_BG : (hovering ? KEY_HOVER_BG : KEY_IDLE_BG);
        Color textColor = isListeningRow ? KEY_LISTENING_TEXT : KEY_IDLE_TEXT;
        flatButton.draw(batch, keyLabel, keyX, keyY, KEY_BUTTON_WIDTH, KEY_BUTTON_HEIGHT, background, textColor);
    }

    /**
     * Draws {@code text} left-aligned, vertically centered within one row's
     * {@link #ROW_HEIGHT} band starting at {@code rowScreenY} — unlike
     * {@link FlatButton#draw}, which always centers its text horizontally
     * too (right for a button, wrong for a row label that should read as
     * plain left-aligned list text).
     */
    private void drawLeftAlignedLabel(String text, float x, float rowScreenY) {
        rowLabelLayout.setText(rowLabelFont, text);
        rowLabelFont.setColor(ROW_LABEL_COLOR);
        rowLabelFont.draw(batch, rowLabelLayout, x, rowScreenY + (ROW_HEIGHT + rowLabelLayout.height) / 2f);
    }

    private void drawFooterButtons(float panelScreenX, float panelScreenY, float mouseX, float mouseY) {
        float resetX = DialogLayout.toScreenX(panelScreenX, RESET_BUTTON_X);
        float backX = DialogLayout.toScreenX(panelScreenX, BACK_BUTTON_X);
        float footerY = DialogLayout.toScreenY(panelScreenY, PANEL_HEIGHT, BUTTONS_TOP, BUTTON_HEIGHT);

        boolean resetHover = listeningFor == null
            && FlatButton.contains(resetX, footerY, RESET_BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY);
        boolean backHover = listeningFor == null
            && FlatButton.contains(backX, footerY, BACK_BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY);

        flatButton.draw(batch, "RESET TO DEFAULTS", resetX, footerY, RESET_BUTTON_WIDTH, BUTTON_HEIGHT,
            resetHover ? RESET_HOVER_BG : RESET_IDLE_BG, FOOTER_TEXT_COLOR);
        flatButton.draw(batch, "BACK", backX, footerY, BACK_BUTTON_WIDTH, BUTTON_HEIGHT,
            backHover ? KEY_HOVER_BG : KEY_IDLE_BG, FOOTER_TEXT_COLOR);
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
        if (listeningFor != null) {
            stopListening();
        }
        batch.dispose();
        // background/logoTexture/panelTexture are owned by StarWarsGame#getAssets() (design.md -
        // asset loading), not this screen - disposed once, at app shutdown, not here.
        flatButton.dispose();
        rowLabelFont.dispose();
    }
}
