package de.mkoehler.starwars;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.ScreenUtils;
import de.mkoehler.starwars.net.NetworkClient;
import de.mkoehler.starwars.net.NetworkConstants;
import de.mkoehler.starwars.net.messages.HandshakeResponse;
import de.mkoehler.starwars.net.messages.UnlockShipRequest;
import de.mkoehler.starwars.net.messages.UnlockShipResponse;
import de.mkoehler.starwars.render.DialogLayout;
import de.mkoehler.starwars.render.FlatButton;
import de.mkoehler.starwars.render.GameAssets;
import de.mkoehler.starwars.render.ScrollingBackground;
import de.mkoehler.starwars.render.Tooltip;
import de.mkoehler.starwars.sim.ShipStats;
import de.mkoehler.starwars.sim.ShipTree;
import de.mkoehler.starwars.sim.ShipType;
import de.mkoehler.starwars.sim.ShipUnlocks;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Shown right after a successful login on {@link ConnectScreen} (design.md
 * 5.1) — lets the player cycle through every known {@link ShipType}
 * (Previous/Next, by mouse or the left/right arrow keys) and start a match
 * flying the one currently displayed (Start button or ENTER).
 * <p>
 * The selected ship type is sent to the server in a
 * {@code de.mkoehler.starwars.net.messages.SpawnRequest} once {@link Client}
 * (re)establishes the connection — every ship type currently uses the same
 * performance numbers (thrust/torque/hull/shield), copied from the X-wing's
 * {@code .stats.json} until each gets its own real balancing pass.
 * <p>
 * All rendering uses pre-made dialog art at native pixel size/position (no
 * Scene2D/VisUI, matching this codebase's existing raw-{@link SpriteBatch}
 * style for {@code ShipStatusHud}/{@code ParallaxBackground}) — VisUI
 * (design.md 4.4) is used by {@link ConnectScreen} instead, which actually
 * needs form widgets; this screen, being entirely pre-rendered art plus two
 * arrow buttons and a start button, doesn't.
 * <p>
 * <b>Ship unlocks (design.md):</b> this is the first (and so far only)
 * screen besides {@link ConnectScreen} to hold its own live server
 * connection — established in {@link #show()} via its own fresh handshake
 * (design.md 3.6 — logging in again is harmless), kept open (unlike
 * {@code ConnectScreen}, which disconnects the moment it's validated login)
 * for as long as the player lingers here, so a locked ship can actually be
 * unlocked without leaving the screen. The handshake's {@link HandshakeResponse}
 * carries the account's XP and unlocked-ship-types set; a locked ship shows
 * a green ("'SPACE' to unlock", already baked into the art) or white ("not
 * enough XP") padlock overlay depending on {@link ShipUnlocks#availableXp}
 * versus that ship's {@link ShipStats#getUnlockCostXp()} — pressing
 * <b>SPACE</b> over a green one sends an {@link UnlockShipRequest}; the
 * server's {@link UnlockShipResponse} (re-validated there, never trusted
 * from this screen's own gating alone) replaces this screen's local copy of
 * both numbers outright rather than applying an optimistic local update.
 * {@link ShipType#SNOWSPEEDER} never shows a padlock at all — it's always
 * unlocked. Start/ENTER do nothing for a still-locked ship, same as this
 * screen already does nothing for input on its very first frame.
 * <p>
 * <b>Ship Tree (design.md):</b> unlocking is also gated by
 * {@link ShipTree} — each branch ship needs its own predecessor already
 * unlocked, not just enough XP. A ship blocked this way shows the
 * "Padlock_White_TierTooHigh" overlay regardless of affordability; hovering
 * the mouse over a locked ship's portrait/padlock (whichever kind) shows a
 * {@link Tooltip} explaining exactly why — missing XP amount, or which
 * ship to unlock first — since neither padlock's baked-in text alone says
 * either of those specifics.
 * <p>
 * <b>Keybind Settings (design.md 3.8/5.2):</b> a "KEYBINDS" button (bottom
 * left, mirroring the Start button's own bottom-right placement) or the
 * <b>F12</b> key opens {@link KeybindScreen} — the only entry point to it
 * in this project, exactly as asked for. Uses the new {@link FlatButton}
 * helper (a live-drawn, tintable rectangle button) rather than pre-made
 * art with hover states like the arrows/Start button, since a fourth art
 * asset just for this one button wasn't worth it — {@link KeybindScreen}
 * needs the same kind of button many times over anyway (its own class
 * Javadoc explains why it's built that way).
 */
public class ShipSelectionScreen implements Screen {

    private static final String TAG = "ShipSelectionScreen";

    private static final ShipType[] SHIP_TYPES = ShipType.values();

    private static final float DIALOG_WIDTH = 818f;
    private static final float DIALOG_HEIGHT = 618f;

    private static final float ARROW_WIDTH = 49f;
    private static final float ARROW_HEIGHT = 38f;
    private static final float ARROW_TOP_DOWN_Y = 124f;
    // "Equidistant in the range X=594 to X=755" (design.md 5.1): 3 equal gaps around the two
    // 49px-wide arrows fill that 161px range - gap = (161 - 2*49) / 3 = 21, giving these two
    // clean integer X positions.
    private static final float ARROW_LEFT_TOP_DOWN_X = 615f;
    private static final float ARROW_RIGHT_TOP_DOWN_X = 685f;

    private static final float PORTRAIT_AREA_TOP_DOWN_X = 370f;
    private static final float PORTRAIT_AREA_TOP_DOWN_Y = 172f;
    private static final float PORTRAIT_AREA_SIZE = 384f;

    private static final float DESCRIPTION_TOP_DOWN_X = 37f;
    private static final float DESCRIPTION_TOP_DOWN_Y = 171f;

    private static final float START_BUTTON_WIDTH = 82f;
    private static final float START_BUTTON_HEIGHT = 86f;
    /** Gap below the dialog's bottom edge for the start button - untuned placeholder, not pixel-specified. */
    private static final float START_BUTTON_GAP = 40f;

    private static final float KEYBINDS_BUTTON_WIDTH = 200f;
    private static final float KEYBINDS_BUTTON_HEIGHT = 44f;
    private static final int KEYBINDS_BUTTON_FONT_SIZE_PX = 18;
    private static final Color KEYBINDS_BUTTON_IDLE_COLOR = new Color(10 / 255f, 26 / 255f, 55 / 255f, 0.85f);
    private static final Color KEYBINDS_BUTTON_HOVER_COLOR = new Color(25 / 255f, 60 / 255f, 110 / 255f, 0.9f);
    private static final Color KEYBINDS_BUTTON_TEXT_COLOR = new Color(0.94f, 0.87f, 0.66f, 1f);

    /** Fraction of the screen-top-to-dialog-top gap the logo's height fills - untuned placeholder. */
    private static final float LOGO_HEIGHT_FRACTION_OF_GAP = 0.7f;

    /** Background drift - a fixed diagonal direction and a slow, subtle speed; both untuned placeholders. */
    private static final float BACKGROUND_DRIFT_DIRECTION_DEGREES = 25f;
    private static final float BACKGROUND_DRIFT_SPEED_PIXELS_PER_SECOND = 15f;

    private final StarWarsGame game;
    private final ConnectionInfo connectionInfo;

    private SpriteBatch batch;
    private OrthographicCamera camera;
    private ScrollingBackground background;
    private Texture logoTexture;
    private TextureAtlas menuAtlas;
    private TextureAtlas shipsAtlas;

    private TextureRegion dialogRegion;
    private TextureRegion arrowLeftRegion;
    private TextureRegion arrowLeftHoverRegion;
    private TextureRegion arrowRightRegion;
    private TextureRegion arrowRightHoverRegion;
    private TextureRegion startButtonRegion;
    private TextureRegion startButtonHoverRegion;
    private TextureRegion padlockGreenRegion;
    private TextureRegion padlockWhiteRegion;
    private TextureRegion padlockTierTooHighRegion;
    private Tooltip tooltip;
    private FlatButton keybindsButton;

    private static final int TOOLTIP_FONT_SIZE_PX = 16;
    /** Offset from the mouse cursor so the tooltip doesn't sit directly under it. */
    private static final float TOOLTIP_OFFSET_X = 18f;
    private static final float TOOLTIP_OFFSET_Y = 18f;

    private NetworkClient networkClient;
    private final Queue<Runnable> pendingUpdates = new ConcurrentLinkedQueue<>();
    private int myXp;
    private Set<ShipType> unlockedShips = new HashSet<>();
    /** Set once an {@link UnlockShipRequest} has been sent, until the server's {@link UnlockShipResponse} arrives - guards against spamming a request every frame SPACE is held. */
    private boolean unlockRequestInFlight;

    private int selectedIndex;
    /**
     * Ignores keyboard input for exactly this screen's first {@link #render}
     * call - found via live testing: pressing ENTER on {@link ConnectScreen}
     * to submit login can still read as "just pressed" on the very next
     * frame, which is this screen's first one, immediately triggering
     * {@link #startMatch()} and skipping ship selection entirely. libGDX's
     * "just pressed" flag only ever lives for one frame, so ignoring
     * input on this screen's first frame fully absorbs the leak without
     * losing any real player input.
     */
    private boolean firstFrame = true;

    /**
     * Creates the screen.
     *
     * @param game           the game to switch away to {@link Client} from once the
     *                       player presses Start
     * @param connectionInfo the already-validated login this session was
     *                       established with on the Connect Dialog
     *                       (design.md 5.1), passed through to {@link Client}
     */
    public ShipSelectionScreen(StarWarsGame game, ConnectionInfo connectionInfo) {
        this.game = game;
        this.connectionInfo = connectionInfo;
    }

    @Override
    public void show() {
        // Temporary diagnostic timing while investigating an intermittent screen-transition
        // pause (CLAUDE.md) - logged once show() finishes, below.
        long showStartMillis = System.currentTimeMillis();

        batch = new SpriteBatch();
        camera = new OrthographicCamera();
        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        background = new ScrollingBackground(game.getAssets().get(GameAssets.MENU_STARFIELD, Texture.class),
            BACKGROUND_DRIFT_DIRECTION_DEGREES, BACKGROUND_DRIFT_SPEED_PIXELS_PER_SECOND);
        logoTexture = game.getAssets().get(GameAssets.LOGO, Texture.class);

        menuAtlas = game.getAssets().get(GameAssets.MENU_ATLAS, TextureAtlas.class);
        dialogRegion = menuAtlas.findRegion("Select_Ship_Dialog");
        arrowLeftRegion = menuAtlas.findRegion("Arrow_Left");
        arrowLeftHoverRegion = menuAtlas.findRegion("Arrow_Left_MouseOver");
        arrowRightRegion = menuAtlas.findRegion("Arrow_Right");
        arrowRightHoverRegion = menuAtlas.findRegion("Arrow_Right_MouseOver");
        startButtonRegion = menuAtlas.findRegion("Start_Button");
        startButtonHoverRegion = menuAtlas.findRegion("Start_Button_MouseOver");
        padlockGreenRegion = menuAtlas.findRegion("Padlock_Green");
        padlockWhiteRegion = menuAtlas.findRegion("Padlock_White");
        padlockTierTooHighRegion = menuAtlas.findRegion("Padlock_White_TierTooHigh");
        tooltip = new Tooltip(TOOLTIP_FONT_SIZE_PX);
        keybindsButton = new FlatButton(KEYBINDS_BUTTON_FONT_SIZE_PX);

        // Ship hull sprites are also in this atlas, but only the portrait regions are used here.
        shipsAtlas = game.getAssets().get(GameAssets.SHIPS_ATLAS, TextureAtlas.class);

        connectToServer();

        Gdx.app.log(TAG, "show() took " + (System.currentTimeMillis() - showStartMillis) + "ms total");
    }

    /**
     * Establishes this screen's own live connection (see the class Javadoc's
     * "Ship unlocks" section) and sends a fresh handshake. Asynchronous,
     * deliberately: blocking here (the way {@code ConnectScreen.attemptConnect()}
     * blocks) would freeze this screen's very first frame for as long as
     * {@link NetworkConstants#CONNECTION_TIMEOUT_MILLIS} - acceptable for a
     * screen whose entire purpose at that moment <i>is</i> connecting, not
     * for this one, whose primary purpose is browsing ships. Until the
     * response arrives, every non-Snowspeeder ship simply shows as locked
     * (the harmless default {@link #myXp}{@code  = 0}/{@link #unlockedShips}
     * {@code  = \{\}} implies), which resolves itself within a frame or two
     * on any real connection.
     * <p>
     * A connection failure is fatal, same treatment as {@link Client#connectToServer()}
     * — {@code ConnectScreen} already validated this exact host/login moments
     * ago, so a failure this soon after is not expected in practice.
     */
    private void connectToServer() {
        networkClient = new NetworkClient() {
            @Override
            protected void onReceived(Object object) {
                // Runs on KryoNet's network thread - only ever enqueue here, never touch
                // myXp/unlockedShips directly (same cross-thread rule as Client/GameNetworkServer).
                if (object instanceof HandshakeResponse response) {
                    if (response.isAccepted()) {
                        pendingUpdates.add(() -> {
                            myXp = response.getXp();
                            unlockedShips = new HashSet<>(Arrays.asList(response.getUnlockedShips()));
                        });
                    } else {
                        pendingUpdates.add(() -> {
                            throw new IllegalStateException("Handshake rejected: " + response.getMessage());
                        });
                    }
                } else if (object instanceof UnlockShipResponse response) {
                    pendingUpdates.add(() -> {
                        myXp = response.getXp();
                        unlockedShips = new HashSet<>(Arrays.asList(response.getUnlockedShips()));
                        unlockRequestInFlight = false;
                    });
                }
            }
        };
        try {
            networkClient.connect(NetworkConstants.CONNECTION_TIMEOUT_MILLIS, connectionInfo.serverHost(),
                NetworkConstants.TCP_PORT, NetworkConstants.UDP_PORT);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to connect to " + connectionInfo.serverHost(), e);
        }
        networkClient.sendHandshake(connectionInfo.login(), connectionInfo.password(), connectionInfo.displayName());
    }

    @Override
    public void render(float deltaTime) {
        ScreenUtils.clear(0f, 0f, 0f, 1f);
        background.update(deltaTime);

        Runnable update;
        while ((update = pendingUpdates.poll()) != null) {
            update.run();
        }

        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();
        float dialogScreenX = (screenWidth - DIALOG_WIDTH) / 2f;
        float dialogScreenY = (screenHeight - DIALOG_HEIGHT) / 2f;

        // Raw input Y is measured down from the window's top-left; this screen's own camera (and
        // every layout position below) is Y-up from the bottom-left, matching Client's hudCamera
        // convention - so mouse Y needs flipping before comparing against anything drawn here.
        float mouseX = Gdx.input.getX();
        float mouseY = screenHeight - Gdx.input.getY();

        float leftArrowX = DialogLayout.toScreenX(dialogScreenX, ARROW_LEFT_TOP_DOWN_X);
        float rightArrowX = DialogLayout.toScreenX(dialogScreenX, ARROW_RIGHT_TOP_DOWN_X);
        float arrowY = DialogLayout.toScreenY(dialogScreenY, DIALOG_HEIGHT, ARROW_TOP_DOWN_Y, ARROW_HEIGHT);
        boolean hoveringLeftArrow = contains(leftArrowX, arrowY, ARROW_WIDTH, ARROW_HEIGHT, mouseX, mouseY);
        boolean hoveringRightArrow = contains(rightArrowX, arrowY, ARROW_WIDTH, ARROW_HEIGHT, mouseX, mouseY);

        float startButtonX = dialogScreenX + DIALOG_WIDTH - START_BUTTON_WIDTH;
        float startButtonY = dialogScreenY - START_BUTTON_GAP - START_BUTTON_HEIGHT;
        boolean hoveringStartButton = contains(startButtonX, startButtonY, START_BUTTON_WIDTH, START_BUTTON_HEIGHT, mouseX, mouseY);

        // Vertically centered within the same band as the Start button, mirrored to the dialog's
        // left edge (design.md 3.8/5.2's Keybind Settings entry point).
        float keybindsButtonX = dialogScreenX;
        float keybindsButtonY = startButtonY + (START_BUTTON_HEIGHT - KEYBINDS_BUTTON_HEIGHT) / 2f;
        boolean hoveringKeybindsButton = FlatButton.contains(keybindsButtonX, keybindsButtonY,
            KEYBINDS_BUTTON_WIDTH, KEYBINDS_BUTTON_HEIGHT, mouseX, mouseY);

        float portraitBoxScreenX = DialogLayout.toScreenX(dialogScreenX, PORTRAIT_AREA_TOP_DOWN_X);
        float portraitBoxScreenY = DialogLayout.toScreenY(dialogScreenY, DIALOG_HEIGHT, PORTRAIT_AREA_TOP_DOWN_Y, PORTRAIT_AREA_SIZE);
        boolean hoveringPortrait = contains(portraitBoxScreenX, portraitBoxScreenY, PORTRAIT_AREA_SIZE, PORTRAIT_AREA_SIZE, mouseX, mouseY);
        String tooltipText = hoveringPortrait ? tooltipTextFor(SHIP_TYPES[selectedIndex]) : null;

        if (handleInput(hoveringLeftArrow, hoveringRightArrow, hoveringStartButton, hoveringKeybindsButton)) {
            // startMatch() just disposed this screen's own textures/batch (switching to Client) -
            // drawing anything else this frame would use them after disposal and crash (a GL
            // "No buffer allocated!" error, found exactly this way): stop immediately instead of
            // falling through into the batch calls below.
            return;
        }

        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        background.render(batch, screenWidth, screenHeight);
        drawLogo(screenWidth, dialogScreenY + DIALOG_HEIGHT, screenHeight);
        batch.draw(dialogRegion, dialogScreenX, dialogScreenY, DIALOG_WIDTH, DIALOG_HEIGHT);
        batch.draw(hoveringLeftArrow ? arrowLeftHoverRegion : arrowLeftRegion, leftArrowX, arrowY, ARROW_WIDTH, ARROW_HEIGHT);
        batch.draw(hoveringRightArrow ? arrowRightHoverRegion : arrowRightRegion, rightArrowX, arrowY, ARROW_WIDTH, ARROW_HEIGHT);
        drawPortrait(dialogScreenX, dialogScreenY);
        drawLockOverlay(dialogScreenX, dialogScreenY);
        drawDescription(dialogScreenX, dialogScreenY);
        batch.draw(hoveringStartButton ? startButtonHoverRegion : startButtonRegion,
            startButtonX, startButtonY, START_BUTTON_WIDTH, START_BUTTON_HEIGHT);
        keybindsButton.draw(batch, "KEYBINDS (F12)", keybindsButtonX, keybindsButtonY,
            KEYBINDS_BUTTON_WIDTH, KEYBINDS_BUTTON_HEIGHT,
            hoveringKeybindsButton ? KEYBINDS_BUTTON_HOVER_COLOR : KEYBINDS_BUTTON_IDLE_COLOR, KEYBINDS_BUTTON_TEXT_COLOR);

        if (tooltipText != null) {
            tooltip.render(batch, tooltipText, mouseX + TOOLTIP_OFFSET_X, mouseY + TOOLTIP_OFFSET_Y, screenWidth, screenHeight);
        }

        batch.end();
    }

    /**
     * @return {@code true} if a match was just started — the caller must not
     * touch this screen's (now-disposed) batch/textures again this frame
     */
    private boolean handleInput(boolean hoveringLeftArrow, boolean hoveringRightArrow, boolean hoveringStartButton,
                                 boolean hoveringKeybindsButton) {
        if (firstFrame) {
            firstFrame = false;
            return false;
        }

        boolean keybindsClicked = hoveringKeybindsButton && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT);
        if (keybindsClicked || Gdx.input.isKeyJustPressed(Input.Keys.F12)) {
            openKeybindScreen();
            return true;
        }

        boolean leftClicked = hoveringLeftArrow && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT);
        boolean rightClicked = hoveringRightArrow && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT);

        if (leftClicked || Gdx.input.isKeyJustPressed(Input.Keys.LEFT)) {
            selectedIndex = Math.floorMod(selectedIndex - 1, SHIP_TYPES.length);
        } else if (rightClicked || Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)) {
            selectedIndex = Math.floorMod(selectedIndex + 1, SHIP_TYPES.length);
        }

        ShipType selectedType = SHIP_TYPES[selectedIndex];
        if (!isUnlocked(selectedType) && !unlockRequestInFlight
                && ShipTree.prerequisiteMet(selectedType, unlockedShips)
                && availableXp() >= ShipStats.forType(selectedType).getUnlockCostXp()
                && Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            unlockRequestInFlight = true;
            networkClient.sendTCP(new UnlockShipRequest(selectedType));
        }

        // A locked ship simply can't be started - same as the padlock overlay already
        // communicates why, no separate error message needed.
        boolean startClicked = hoveringStartButton && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT);
        if ((startClicked || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) && isUnlocked(selectedType)) {
            startMatch();
            return true;
        }
        return false;
    }

    private void startMatch() {
        Client next = new Client(game, SHIP_TYPES[selectedIndex], connectionInfo);
        game.setScreen(next);
        dispose();
    }

    /**
     * Opens {@link KeybindScreen} (design.md 3.8/5.2) — the only entry point
     * to it in this project, via the "KEYBINDS" button or F12 (see the class
     * Javadoc). Returning here re-does this screen's own fresh handshake
     * (same "reconnecting is harmless" precedent as every other screen
     * transition here, design.md 3.6), so nothing about the account/unlock
     * state needs to be threaded through {@link KeybindScreen} itself —
     * it's a purely local, offline settings screen.
     */
    private void openKeybindScreen() {
        game.setScreen(new KeybindScreen(game, connectionInfo));
        dispose();
    }

    private void drawLogo(float screenWidth, float dialogTopY, float screenHeight) {
        float gapHeight = screenHeight - dialogTopY;
        float logoHeight = gapHeight * LOGO_HEIGHT_FRACTION_OF_GAP;
        float logoWidth = logoHeight * (logoTexture.getWidth() / (float) logoTexture.getHeight());
        float logoX = (screenWidth - logoWidth) / 2f;
        float logoY = dialogTopY + (gapHeight - logoHeight) / 2f;
        batch.draw(logoTexture, logoX, logoY, logoWidth, logoHeight);
    }

    private void drawPortrait(float dialogScreenX, float dialogScreenY) {
        TextureRegion portrait = shipsAtlas.findRegion(SHIP_TYPES[selectedIndex].getResourceName() + "/portrait");
        if (portrait == null) {
            return;
        }
        float boxScreenX = DialogLayout.toScreenX(dialogScreenX, PORTRAIT_AREA_TOP_DOWN_X);
        float boxScreenY = DialogLayout.toScreenY(dialogScreenY, DIALOG_HEIGHT, PORTRAIT_AREA_TOP_DOWN_Y, PORTRAIT_AREA_SIZE);

        DialogLayout.Fit fit = DialogLayout.fitCentered(PORTRAIT_AREA_SIZE, PORTRAIT_AREA_SIZE,
            portrait.getRegionWidth(), portrait.getRegionHeight());
        batch.draw(portrait, boxScreenX + fit.offsetX(), boxScreenY + fit.offsetY(), fit.width(), fit.height());
    }

    /**
     * Draws the tier-too-high ("unlock the previous ship in this branch
     * first"), green ("affordable, SPACE to unlock"), or white ("not enough
     * XP") padlock overlay, centered over the portrait, for the currently-
     * selected ship type — or nothing at all if it's already unlocked (see
     * the class Javadoc's "Ship unlocks"/"Ship Tree" section). The tree
     * check ({@link ShipTree#prerequisiteMet}) takes priority over the
     * affordability one: a ship can be both unaffordable <i>and</i> blocked
     * by the tree, but only one padlock can show at a time, and knowing
     * which branch ship to unlock first is the more fundamental blocker.
     */
    private void drawLockOverlay(float dialogScreenX, float dialogScreenY) {
        ShipType type = SHIP_TYPES[selectedIndex];
        if (isUnlocked(type)) {
            return;
        }
        TextureRegion padlock;
        if (!ShipTree.prerequisiteMet(type, unlockedShips)) {
            padlock = padlockTierTooHighRegion;
        } else if (availableXp() >= ShipStats.forType(type).getUnlockCostXp()) {
            padlock = padlockGreenRegion;
        } else {
            padlock = padlockWhiteRegion;
        }
        if (padlock == null) {
            return;
        }
        float boxScreenX = DialogLayout.toScreenX(dialogScreenX, PORTRAIT_AREA_TOP_DOWN_X);
        float boxScreenY = DialogLayout.toScreenY(dialogScreenY, DIALOG_HEIGHT, PORTRAIT_AREA_TOP_DOWN_Y, PORTRAIT_AREA_SIZE);

        DialogLayout.Fit fit = DialogLayout.fitCentered(PORTRAIT_AREA_SIZE, PORTRAIT_AREA_SIZE,
            padlock.getRegionWidth(), padlock.getRegionHeight());
        batch.draw(padlock, boxScreenX + fit.offsetX(), boxScreenY + fit.offsetY(), fit.width(), fit.height());
    }

    private boolean isUnlocked(ShipType type) {
        return ShipUnlocks.isUnlocked(type, unlockedShips);
    }

    private int availableXp() {
        return ShipUnlocks.availableXp(myXp, unlockedShips);
    }

    /**
     * Returns the tooltip message explaining why {@code type}'s padlock is
     * showing what it's showing, or {@code null} if no tooltip should be
     * drawn — either the ship is already unlocked, or it's the green,
     * affordable-and-unlockable case, whose meaning is already fully
     * conveyed by the padlock art's own baked-in "'SPACE' to unlock" text.
     */
    private String tooltipTextFor(ShipType type) {
        if (isUnlocked(type)) {
            return null;
        }
        if (!ShipTree.prerequisiteMet(type, unlockedShips)) {
            ShipType prerequisite = ShipTree.prerequisiteOf(type).orElseThrow();
            return "You need to unlock the " + prerequisite.getDisplayName() + " before you can unlock this.";
        }
        int missingXp = ShipStats.forType(type).getUnlockCostXp() - availableXp();
        if (missingXp > 0) {
            return "You're lacking " + missingXp + " XP to unlock this ship.";
        }
        return null;
    }

    private void drawDescription(float dialogScreenX, float dialogScreenY) {
        TextureRegion description = menuAtlas.findRegion(descriptionRegionName(SHIP_TYPES[selectedIndex]));
        if (description == null) {
            return;
        }
        float x = DialogLayout.toScreenX(dialogScreenX, DESCRIPTION_TOP_DOWN_X);
        float y = DialogLayout.toScreenY(dialogScreenY, DIALOG_HEIGHT, DESCRIPTION_TOP_DOWN_Y, description.getRegionHeight());
        batch.draw(description, x, y);
    }

    /**
     * Maps a ship type to its {@code textures/menu.atlas} description image
     * region name — the user-provided filenames don't follow
     * {@link ShipType#getResourceName()}'s lowercase convention (e.g.
     * {@code "SnowSpeeder_Description"}, not {@code "snowspeeder_..."}), so
     * this is an explicit table rather than a derived name.
     */
    private static String descriptionRegionName(ShipType type) {
        return switch (type) {
            case XWING -> "XWing_Description";
            case FALCON -> "Falcon_Description";
            case SNOWSPEEDER -> "SnowSpeeder_Description";
            case STARDESTROYER -> "StarDestroyer_Description";
            case TIEFIGHTER -> "TieFighter_Description";
            case TIEINTERCEPTOR -> "TieInterceptor_Description";
            case AWING -> "AWing_Description";
        };
    }

    private static boolean contains(float x, float y, float width, float height, float pointX, float pointY) {
        return pointX >= x && pointX <= x + width && pointY >= y && pointY <= y + height;
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
        // Temporary diagnostic timing while investigating an intermittent screen-transition
        // pause (CLAUDE.md) - root-caused to NetworkClient#stop()'s underlying OS socket
        // teardown (see that method's Javadoc); networkClient.stopAsync() below is the fix,
        // this timing stays to confirm dispose() itself is fast now.
        long disposeStartMillis = System.currentTimeMillis();

        if (networkClient != null) {
            networkClient.stopAsync();
        }
        batch.dispose();
        // background/logoTexture/menuAtlas/shipsAtlas are owned by StarWarsGame#getAssets()
        // (design.md - asset loading), not this screen - disposed once, at app shutdown, not here.
        tooltip.dispose();
        keybindsButton.dispose();

        Gdx.app.log(TAG, "dispose() took " + (System.currentTimeMillis() - disposeStartMillis) + "ms total");
    }
}
