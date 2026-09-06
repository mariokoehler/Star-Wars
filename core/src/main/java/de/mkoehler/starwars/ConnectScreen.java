package de.mkoehler.starwars;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisTextField;
import de.mkoehler.starwars.net.ConnectionConfig;
import de.mkoehler.starwars.net.ConnectionConfigStore;
import de.mkoehler.starwars.net.NetworkClient;
import de.mkoehler.starwars.net.NetworkConstants;
import de.mkoehler.starwars.net.messages.HandshakeResponse;
import de.mkoehler.starwars.render.DialogLayout;
import de.mkoehler.starwars.render.ScrollingBackground;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The first screen shown (design.md 5.1) — logs into (or creates,
 * design.md 3.6) a player account before anything else is reachable.
 * Fields: server host, display name, login, password; pre-filled from a
 * local {@link ConnectionConfig} (design.md 3.7) if one was saved by a
 * previous successful connect. Connect button or <b>ENTER</b> attempts the
 * login; <b>TAB</b> (Shift+TAB to go backward) cycles keyboard focus
 * between the four fields, so the whole screen is usable without a mouse —
 * required for this project's own remote-control verification technique
 * (CLAUDE.md), not just an accessibility nicety.
 * <p>
 * The one screen in this codebase that actually needs form widgets
 * (design.md 4.4), so it's the first real use of Scene2D + VisUI here,
 * layered as a second pass on top of the same background-drift +
 * pre-rendered dialog art style {@link ShipSelectionScreen} already
 * established — the dialog panel and the Connect button's up/hover art
 * are custom-generated to match that screen's palette (deep navy panel,
 * gold beveled header text), but the text fields themselves have no
 * background of their own: they sit, fully transparent, directly on top
 * of the "well" rectangles already baked into the panel art, so only the
 * live cursor/typed text needs to be drawn on top at runtime.
 * <p>
 * On a successful login, disconnects immediately (this screen's
 * {@link NetworkClient} was only ever needed to validate the handshake)
 * and hands a {@link ConnectionInfo} to {@link ShipSelectionScreen} —
 * {@link Client} re-sends the same credentials on its own fresh connection
 * once a match actually starts (design.md 3.6 — logging in again is
 * harmless), rather than this screen keeping the connection alive across
 * screens.
 */
public class ConnectScreen implements Screen {

    private static final float DIALOG_WIDTH = 640f;
    private static final float DIALOG_HEIGHT = 580f;

    private static final float FIELD_LEFT = 40f;
    private static final float FIELD_WIDTH = DIALOG_WIDTH - 2 * FIELD_LEFT;
    private static final float FIELD_HEIGHT = 40f;

    private static final float HOST_FIELD_TOP = 158f;
    private static final float DISPLAY_NAME_FIELD_TOP = 246f;
    private static final float LOGIN_FIELD_TOP = 334f;
    private static final float PASSWORD_FIELD_TOP = 422f;

    private static final float ERROR_LABEL_TOP = 468f;
    private static final float ERROR_LABEL_HEIGHT = 22f;

    private static final float BUTTON_WIDTH = 220f;
    private static final float BUTTON_HEIGHT = 56f;
    private static final float BUTTON_TOP = 496f;

    /** Fraction of the screen-top-to-dialog-top gap the logo's height fills - same placeholder as {@code ShipSelectionScreen}. */
    private static final float LOGO_HEIGHT_FRACTION_OF_GAP = 0.7f;

    /** Background drift - same fixed diagonal direction/speed as {@code ShipSelectionScreen}, untuned placeholders. */
    private static final float BACKGROUND_DRIFT_DIRECTION_DEGREES = 25f;
    private static final float BACKGROUND_DRIFT_SPEED_PIXELS_PER_SECOND = 15f;

    private static final Color ERROR_COLOR = new Color(1f, 0.45f, 0.35f, 1f);
    private static final Color FIELD_FONT_COLOR = new Color(0.92f, 0.95f, 1f, 1f);
    private static final Color BUTTON_FONT_COLOR = new Color(0.94f, 0.87f, 0.66f, 1f);

    private final Game game;

    private SpriteBatch batch;
    private OrthographicCamera camera;
    private ScrollingBackground background;
    private Texture logoTexture;
    private TextureAtlas menuAtlas;
    private TextureRegion dialogRegion;

    private Stage stage;
    private VisTextField hostField;
    private VisTextField displayNameField;
    private VisTextField loginField;
    private VisTextField passwordField;
    private VisTextField[] tabOrder;
    private VisLabel errorLabel;
    private VisTextButton connectButton;

    private boolean connecting;

    /**
     * Creates the screen.
     *
     * @param game the game to switch away to {@link ShipSelectionScreen} from
     *             once login succeeds
     */
    public ConnectScreen(Game game) {
        this.game = game;
    }

    @Override
    public void show() {
        batch = new SpriteBatch();
        camera = new OrthographicCamera();
        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        background = new ScrollingBackground(new Texture(Gdx.files.internal("textures/backgrounds/menu_starfield.png")),
            BACKGROUND_DRIFT_DIRECTION_DEGREES, BACKGROUND_DRIFT_SPEED_PIXELS_PER_SECOND);
        logoTexture = new Texture(Gdx.files.internal("textures/menu/logo.png"));

        menuAtlas = new TextureAtlas(Gdx.files.internal("textures/menu.atlas"));
        dialogRegion = menuAtlas.findRegion("Connect_Dialog");

        if (!VisUI.isLoaded()) {
            // VisUI 1.5.9 (latest as of this writing) still pins gdx 1.14.1 in its own POM,
            // one patch version behind ours (1.14.2, design.md's gdxVersion) - VisUI only
            // touches long-stable Scene2D/Skin APIs, and this exact combination has been
            // exercised live (this screen's own verification) with zero issues, so the
            // warning is a false positive worth silencing rather than downgrading our engine
            // version for. Revisit (drop this line) once a VisUI release targets 1.14.2+.
            VisUI.setSkipGdxVersionCheck(true);
            VisUI.load();
        }
        stage = new Stage(new ScreenViewport());

        VisTextField.VisTextFieldStyle fieldStyle =
            new VisTextField.VisTextFieldStyle(VisUI.getSkin().get(VisTextField.VisTextFieldStyle.class));
        BaseDrawable transparentBackground = transparentBackgroundWithPadding();
        fieldStyle.background = transparentBackground;
        fieldStyle.focusedBackground = transparentBackground;
        fieldStyle.disabledBackground = transparentBackground;
        // VisUI-specific field (not on the base TextFieldStyle it extends) - swaps in on mouse
        // hover, independent of background/focusedBackground above; left at the default skin's
        // value it's a light box that clashes badly with this dark theme.
        fieldStyle.backgroundOver = transparentBackground;
        fieldStyle.focusBorder = null;
        fieldStyle.fontColor = FIELD_FONT_COLOR;
        fieldStyle.focusedFontColor = FIELD_FONT_COLOR;

        hostField = new VisTextField("", fieldStyle);
        displayNameField = new VisTextField("", fieldStyle);
        loginField = new VisTextField("", fieldStyle);
        passwordField = new VisTextField("", fieldStyle);
        passwordField.setPasswordMode(true);
        passwordField.setPasswordCharacter('*');
        tabOrder = new VisTextField[] {hostField, displayNameField, loginField, passwordField};
        // TextField.focusTraversal defaults to true - it would otherwise handle TAB itself
        // (jumping focus in Stage actor-tree order, not the field order below) *in addition to*
        // the stage-level listener further down, double-stepping focus on every press.
        for (VisTextField field : tabOrder) {
            field.setFocusTraversal(false);
        }

        ConnectionConfigStore.load().ifPresentOrElse(saved -> {
            hostField.setText(saved.getServerHost());
            displayNameField.setText(saved.getDisplayName());
            loginField.setText(saved.getLogin());
            passwordField.setText(saved.getPassword());
        }, () -> hostField.setText("localhost"));

        Label.LabelStyle errorStyle = new Label.LabelStyle(VisUI.getSkin().get(Label.LabelStyle.class));
        errorStyle.fontColor = ERROR_COLOR;
        errorLabel = new VisLabel("", errorStyle);

        VisTextButton.VisTextButtonStyle buttonStyle =
            new VisTextButton.VisTextButtonStyle(VisUI.getSkin().get(VisTextButton.VisTextButtonStyle.class));
        TextureRegionDrawable up = new TextureRegionDrawable(menuAtlas.findRegion("Connect_Button"));
        TextureRegionDrawable over = new TextureRegionDrawable(menuAtlas.findRegion("Connect_Button_MouseOver"));
        buttonStyle.up = up;
        buttonStyle.over = over;
        buttonStyle.down = over;
        buttonStyle.fontColor = BUTTON_FONT_COLOR;
        connectButton = new VisTextButton("Connect", buttonStyle);
        connectButton.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                attemptConnect();
            }
        });

        stage.addActor(hostField);
        stage.addActor(displayNameField);
        stage.addActor(loginField);
        stage.addActor(passwordField);
        stage.addActor(errorLabel);
        stage.addActor(connectButton);

        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.TAB) {
                    boolean backward = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                        || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
                    cycleFocus(backward);
                    return true;
                }
                if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
                    attemptConnect();
                    return true;
                }
                return false;
            }
        });

        stage.setKeyboardFocus(hostField);
        Gdx.input.setInputProcessor(stage);
    }

    /**
     * A {@link com.badlogic.gdx.scenes.scene2d.utils.Drawable} that draws
     * nothing - the field "well" rectangles are already baked into
     * {@link #dialogRegion}, so the live {@link VisTextField} on top only
     * needs to contribute its cursor/typed text, not another background.
     * Still declares padding (via the {@code Drawable} insets libGDX reads
     * for content placement) so text doesn't sit flush against the baked
     * well's border.
     */
    private static BaseDrawable transparentBackgroundWithPadding() {
        BaseDrawable drawable = new BaseDrawable() {
            @Override
            public void draw(com.badlogic.gdx.graphics.g2d.Batch batch, float x, float y, float width, float height) {
                // Intentionally blank.
            }
        };
        drawable.setLeftWidth(14f);
        drawable.setRightWidth(14f);
        drawable.setTopHeight(10f);
        drawable.setBottomHeight(10f);
        return drawable;
    }

    private void cycleFocus(boolean backward) {
        com.badlogic.gdx.scenes.scene2d.Actor current = stage.getKeyboardFocus();
        int index = -1;
        for (int i = 0; i < tabOrder.length; i++) {
            if (tabOrder[i] == current) {
                index = i;
                break;
            }
        }
        int next = index < 0 ? 0 : Math.floorMod(index + (backward ? -1 : 1), tabOrder.length);
        VisTextField target = tabOrder[next];
        stage.setKeyboardFocus(target);
        target.selectAll();
    }

    @Override
    public void render(float deltaTime) {
        ScreenUtils.clear(0f, 0f, 0f, 1f);
        background.update(deltaTime);

        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();
        float dialogScreenX = (screenWidth - DIALOG_WIDTH) / 2f;
        float dialogScreenY = (screenHeight - DIALOG_HEIGHT) / 2f;

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        background.render(batch, screenWidth, screenHeight);
        drawLogo(screenWidth, dialogScreenY + DIALOG_HEIGHT, screenHeight);
        batch.draw(dialogRegion, dialogScreenX, dialogScreenY, DIALOG_WIDTH, DIALOG_HEIGHT);
        batch.end();

        layoutActors(dialogScreenX, dialogScreenY);
        stage.act(deltaTime);
        stage.draw();
    }

    private void layoutActors(float dialogScreenX, float dialogScreenY) {
        setFieldBounds(hostField, dialogScreenX, dialogScreenY, HOST_FIELD_TOP);
        setFieldBounds(displayNameField, dialogScreenX, dialogScreenY, DISPLAY_NAME_FIELD_TOP);
        setFieldBounds(loginField, dialogScreenX, dialogScreenY, LOGIN_FIELD_TOP);
        setFieldBounds(passwordField, dialogScreenX, dialogScreenY, PASSWORD_FIELD_TOP);

        float errorX = DialogLayout.toScreenX(dialogScreenX, FIELD_LEFT);
        float errorY = DialogLayout.toScreenY(dialogScreenY, DIALOG_HEIGHT, ERROR_LABEL_TOP, ERROR_LABEL_HEIGHT);
        errorLabel.setBounds(errorX, errorY, FIELD_WIDTH, ERROR_LABEL_HEIGHT);

        float buttonX = DialogLayout.toScreenX(dialogScreenX, (DIALOG_WIDTH - BUTTON_WIDTH) / 2f);
        float buttonY = DialogLayout.toScreenY(dialogScreenY, DIALOG_HEIGHT, BUTTON_TOP, BUTTON_HEIGHT);
        connectButton.setBounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    private void setFieldBounds(VisTextField field, float dialogScreenX, float dialogScreenY, float topDownY) {
        float x = DialogLayout.toScreenX(dialogScreenX, FIELD_LEFT);
        float y = DialogLayout.toScreenY(dialogScreenY, DIALOG_HEIGHT, topDownY, FIELD_HEIGHT);
        field.setBounds(x, y, FIELD_WIDTH, FIELD_HEIGHT);
    }

    private void drawLogo(float screenWidth, float dialogTopY, float screenHeight) {
        float gapHeight = screenHeight - dialogTopY;
        float logoHeight = gapHeight * LOGO_HEIGHT_FRACTION_OF_GAP;
        float logoWidth = logoHeight * (logoTexture.getWidth() / (float) logoTexture.getHeight());
        float logoX = (screenWidth - logoWidth) / 2f;
        float logoY = dialogTopY + (gapHeight - logoHeight) / 2f;
        batch.draw(logoTexture, logoX, logoY, logoWidth, logoHeight);
    }

    /**
     * Validates the four fields, then performs a blocking connect + login
     * handshake (same simplification {@link Client#connectToServer} already
     * accepts — no separate "connecting..." UI state, the screen just
     * doesn't repaint for up to {@link NetworkConstants#CONNECTION_TIMEOUT_MILLIS}
     * on an unreachable host). On success, persists the fields locally
     * (design.md 3.7) and transitions to {@link ShipSelectionScreen}; on
     * failure, shows the reason and stays on this screen.
     */
    private void attemptConnect() {
        if (connecting) {
            return;
        }
        String host = hostField.getText().trim();
        String displayName = displayNameField.getText().trim();
        String login = loginField.getText().trim();
        String password = passwordField.getText();

        if (host.isEmpty() || displayName.isEmpty() || login.isEmpty() || password.isEmpty()) {
            showError("All fields are required.");
            return;
        }

        connecting = true;
        errorLabel.setText("Connecting...");

        CountDownLatch responseLatch = new CountDownLatch(1);
        AtomicReference<HandshakeResponse> responseRef = new AtomicReference<>();
        NetworkClient client = new NetworkClient() {
            @Override
            protected void onReceived(Object object) {
                if (object instanceof HandshakeResponse response) {
                    responseRef.set(response);
                    responseLatch.countDown();
                }
            }
        };

        try {
            client.connect(NetworkConstants.CONNECTION_TIMEOUT_MILLIS, host,
                NetworkConstants.TCP_PORT, NetworkConstants.UDP_PORT);
        } catch (IOException e) {
            client.stop();
            connecting = false;
            showError("Could not reach '" + host + "'.");
            return;
        }

        client.sendHandshake(login, password, displayName);

        boolean responded;
        try {
            responded = responseLatch.await(NetworkConstants.CONNECTION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            responded = false;
        }

        connecting = false;
        if (!responded) {
            client.stop();
            showError("Server did not respond.");
            return;
        }

        HandshakeResponse response = responseRef.get();
        if (!response.isAccepted()) {
            client.stop();
            showError(response.getMessage());
            return;
        }

        client.stop();
        ConnectionConfigStore.save(new ConnectionConfig(host, displayName, login, password));
        Gdx.input.setInputProcessor(null);
        game.setScreen(new ShipSelectionScreen(game, new ConnectionInfo(host, login, password, displayName)));
        dispose();
    }

    private void showError(String message) {
        errorLabel.setText(message);
    }

    @Override
    public void resize(int width, int height) {
        camera.setToOrtho(false, width, height);
        stage.getViewport().update(width, height, true);
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
        background.dispose();
        logoTexture.dispose();
        menuAtlas.dispose();
        stage.dispose();
    }
}
