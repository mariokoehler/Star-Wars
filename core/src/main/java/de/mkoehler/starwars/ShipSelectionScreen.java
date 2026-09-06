package de.mkoehler.starwars;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.ScreenUtils;
import de.mkoehler.starwars.render.DialogLayout;
import de.mkoehler.starwars.render.ScrollingBackground;
import de.mkoehler.starwars.sim.ShipType;

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
 * {@code .stats.json} until each gets its own real balancing pass. No XP
 * gating yet (design.md 3.6) — every ship type is always shown, regardless
 * of the logged-in account's XP.
 * <p>
 * All rendering uses pre-made dialog art at native pixel size/position (no
 * Scene2D/VisUI, matching this codebase's existing raw-{@link SpriteBatch}
 * style for {@code ShipStatusHud}/{@code ParallaxBackground}) — VisUI
 * (design.md 4.4) is used by {@link ConnectScreen} instead, which actually
 * needs form widgets; this screen, being entirely pre-rendered art plus two
 * arrow buttons and a start button, doesn't.
 */
public class ShipSelectionScreen implements Screen {

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

    /** Fraction of the screen-top-to-dialog-top gap the logo's height fills - untuned placeholder. */
    private static final float LOGO_HEIGHT_FRACTION_OF_GAP = 0.7f;

    /** Background drift - a fixed diagonal direction and a slow, subtle speed; both untuned placeholders. */
    private static final float BACKGROUND_DRIFT_DIRECTION_DEGREES = 25f;
    private static final float BACKGROUND_DRIFT_SPEED_PIXELS_PER_SECOND = 15f;

    private final Game game;
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
    public ShipSelectionScreen(Game game, ConnectionInfo connectionInfo) {
        this.game = game;
        this.connectionInfo = connectionInfo;
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
        dialogRegion = menuAtlas.findRegion("Select_Ship_Dialog");
        arrowLeftRegion = menuAtlas.findRegion("Arrow_Left");
        arrowLeftHoverRegion = menuAtlas.findRegion("Arrow_Left_MouseOver");
        arrowRightRegion = menuAtlas.findRegion("Arrow_Right");
        arrowRightHoverRegion = menuAtlas.findRegion("Arrow_Right_MouseOver");
        startButtonRegion = menuAtlas.findRegion("Start_Button");
        startButtonHoverRegion = menuAtlas.findRegion("Start_Button_MouseOver");

        // Ship hull sprites are also in this atlas, but only the portrait regions are used here.
        shipsAtlas = new TextureAtlas(Gdx.files.internal("textures/ships.atlas"));
    }

    @Override
    public void render(float deltaTime) {
        ScreenUtils.clear(0f, 0f, 0f, 1f);
        background.update(deltaTime);

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

        if (handleInput(hoveringLeftArrow, hoveringRightArrow, hoveringStartButton)) {
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
        drawDescription(dialogScreenX, dialogScreenY);
        batch.draw(hoveringStartButton ? startButtonHoverRegion : startButtonRegion,
            startButtonX, startButtonY, START_BUTTON_WIDTH, START_BUTTON_HEIGHT);

        batch.end();
    }

    /**
     * @return {@code true} if a match was just started — the caller must not
     * touch this screen's (now-disposed) batch/textures again this frame
     */
    private boolean handleInput(boolean hoveringLeftArrow, boolean hoveringRightArrow, boolean hoveringStartButton) {
        if (firstFrame) {
            firstFrame = false;
            return false;
        }

        boolean leftClicked = hoveringLeftArrow && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT);
        boolean rightClicked = hoveringRightArrow && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT);

        if (leftClicked || Gdx.input.isKeyJustPressed(Input.Keys.LEFT)) {
            selectedIndex = Math.floorMod(selectedIndex - 1, SHIP_TYPES.length);
        } else if (rightClicked || Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)) {
            selectedIndex = Math.floorMod(selectedIndex + 1, SHIP_TYPES.length);
        }

        boolean startClicked = hoveringStartButton && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT);
        if (startClicked || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
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
        batch.dispose();
        background.dispose();
        logoTexture.dispose();
        menuAtlas.dispose();
        shipsAtlas.dispose();
    }
}
