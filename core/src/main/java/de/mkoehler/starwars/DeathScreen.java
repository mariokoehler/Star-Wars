package de.mkoehler.starwars;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import de.mkoehler.starwars.net.messages.PlayerScoreEntry;
import de.mkoehler.starwars.render.GameAssets;
import de.mkoehler.starwars.render.ScoreboardHud;
import de.mkoehler.starwars.render.ScrollingBackground;

/**
 * Shown after the local player's ship is destroyed in combat (design.md
 * 5.1) — a randomly-selected Star Wars quote paired with a matching image,
 * meant to rub in the loss. Reached only from a real combat death
 * ({@code Client#onShipDestroyed}), never from a voluntary ESC leave
 * (design.md 2.3), which goes straight to {@link ShipSelectionScreen}
 * instead.
 * <p>
 * User-provided art: {@code Dialog_Background.png} (a plain bordered
 * panel) and 23 {@code Quote_<n>.png} images, both 818×618 - the same
 * dialog size {@link ShipSelectionScreen} uses. Each quote image has fully
 * transparent corners (confirmed by inspecting its alpha channel while
 * authoring this screen) so {@code Dialog_Background}'s own border shows
 * through around it when the two are drawn at the same position, and each
 * quote image already has its own "Press 'ESC' to continue" baked in —
 * <b>ESC</b>, not ENTER, is therefore this screen's real continue key,
 * superseding design.md 5.1's original ENTER sketch (written before this
 * art existed).
 * <p>
 * <b>Deliberately not atlas-packed.</b> Only one quote is ever drawn per
 * death - texture atlases exist to share one GPU texture bind across many
 * sprites drawn together, which doesn't apply when 22 of the 23 packed
 * variants would sit unused. An earlier version of this screen atlas-
 * packed all 23 anyway, which forced ~4 full 2048×2048 pages into memory
 * on every death and caused a real, reproducible multi-second freeze
 * (the whole window stopped responding) while they synchronously decoded
 * and uploaded - found by the user actually playing it, not by review.
 * Loading just the two needed files as plain {@link Texture}s instead
 * (same convention as this project's tileable backgrounds, e.g.
 * {@code blue_nebula.png}) fixed it: at most ~2MB decoded per death
 * instead of ~67MB. All 23 (plus the shared dialog background) are now
 * additionally preloaded into {@link StarWarsGame#getAssets()} by
 * {@link SplashScreen} (design.md - asset loading) — this screen just
 * reads whichever one {@link #show()} deals from there, so even that ~2MB
 * on-demand decode at the moment of death is gone too.
 * <p>
 * Combat death itself no longer waits for the server's automatic
 * mid-match respawn (design.md 2.4's {@code RESPAWN_DELAY_SECONDS}) the
 * way it did before this screen existed — the client now leaves the match
 * immediately (same connection teardown as a voluntary ESC leave) and
 * only returns via this screen's ESC, landing back on Ship Selection to
 * pick a ship and start a fresh match/connection. The server-side respawn
 * timer itself is untouched (harmless if a connection somehow outlives
 * it - see {@code GameNetworkServer#onDisconnected}'s existing cleanup) but
 * is no longer exercised by this client, which always leaves well within
 * its 3-second window.
 * <p>
 * Holding <b>TAB</b> here shows the same scoreboard overlay as the
 * gameplay screen ({@link de.mkoehler.starwars.render.ScoreboardHud},
 * design.md 2.11/5.1's addendum) - but with only the local player's own
 * row ({@link #myScore}, a snapshot handed over by {@code Client} at the
 * moment of death), not everyone connected, since this screen has no live
 * server connection of its own to ask for anyone else's.
 */
public class DeathScreen implements Screen {

    private static final float DIALOG_WIDTH = 818f;
    private static final float DIALOG_HEIGHT = 618f;

    /** Background drift - same fixed diagonal direction/speed as the other menu-style screens. */
    private static final float BACKGROUND_DRIFT_DIRECTION_DEGREES = 25f;
    private static final float BACKGROUND_DRIFT_SPEED_PIXELS_PER_SECOND = 15f;

    private final StarWarsGame game;
    private final ConnectionInfo connectionInfo;
    private final PlayerScoreEntry myScore;

    private SpriteBatch batch;
    private OrthographicCamera camera;
    private ScrollingBackground background;
    private Texture dialogBackgroundTexture;
    private Texture quoteTexture;
    private ScoreboardHud scoreboardHud;

    /**
     * Ignores keyboard input for exactly this screen's first {@link #render}
     * call - the same defensive guard {@code ShipSelectionScreen} added
     * after finding a real leftover-input bug there (CLAUDE.md): ESC is
     * reused between Gameplay (leave) and this screen (continue), so this
     * absorbs any one-frame-stale "just pressed" state the same way.
     */
    private boolean firstFrame = true;

    /**
     * Creates the screen.
     *
     * @param game           the game to draw the next quote from
     *                       ({@link StarWarsGame#getQuoteDeck()}) and to
     *                       switch back to {@link ShipSelectionScreen} from
     * @param connectionInfo the already-validated login this session was
     *                       established with, passed through to
     *                       {@link ShipSelectionScreen}
     * @param myScore        a snapshot of the local player's own stats at
     *                       the moment of death, for the TAB scoreboard
     *                       overlay (see the class Javadoc)
     */
    public DeathScreen(StarWarsGame game, ConnectionInfo connectionInfo, PlayerScoreEntry myScore) {
        this.game = game;
        this.connectionInfo = connectionInfo;
        this.myScore = myScore;
    }

    @Override
    public void show() {
        batch = new SpriteBatch();
        camera = new OrthographicCamera();
        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        scoreboardHud = new ScoreboardHud(game.getAssets());

        background = new ScrollingBackground(game.getAssets().get(GameAssets.MENU_STARFIELD, Texture.class),
            BACKGROUND_DRIFT_DIRECTION_DEGREES, BACKGROUND_DRIFT_SPEED_PIXELS_PER_SECOND);

        dialogBackgroundTexture = game.getAssets().get(GameAssets.AFTER_DEATH_DIALOG_BACKGROUND, Texture.class);

        int quoteIndex = game.getQuoteDeck().next();
        quoteTexture = game.getAssets().get(GameAssets.afterDeathQuotePath(quoteIndex + 1), Texture.class);
    }

    @Override
    public void render(float deltaTime) {
        ScreenUtils.clear(0f, 0f, 0f, 1f);
        background.update(deltaTime);

        if (firstFrame) {
            firstFrame = false;
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.setScreen(new ShipSelectionScreen(game, connectionInfo));
            dispose();
            return;
        }

        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();
        float dialogScreenX = (screenWidth - DIALOG_WIDTH) / 2f;
        float dialogScreenY = (screenHeight - DIALOG_HEIGHT) / 2f;

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        background.render(batch, screenWidth, screenHeight);
        batch.draw(dialogBackgroundTexture, dialogScreenX, dialogScreenY, DIALOG_WIDTH, DIALOG_HEIGHT);
        batch.draw(quoteTexture, dialogScreenX, dialogScreenY, DIALOG_WIDTH, DIALOG_HEIGHT);
        if (Gdx.input.isKeyPressed(Input.Keys.TAB)) {
            float scoreboardX = (screenWidth - scoreboardHud.getPanelWidth()) / 2f;
            float scoreboardY = (screenHeight - scoreboardHud.getPanelHeight()) / 2f;
            scoreboardHud.render(batch, scoreboardX, scoreboardY, new PlayerScoreEntry[] {myScore});
        }
        batch.end();
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
        // background/dialogBackgroundTexture/quoteTexture/scoreboardHud's panel are owned by
        // StarWarsGame#getAssets() (design.md - asset loading), not this screen - disposed once,
        // at app shutdown, not here.
        scoreboardHud.dispose();
    }
}
