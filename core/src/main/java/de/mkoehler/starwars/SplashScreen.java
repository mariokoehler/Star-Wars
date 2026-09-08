package de.mkoehler.starwars;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import de.mkoehler.starwars.render.GameAssets;

/**
 * The very first screen shown, replacing {@link ConnectScreen} as
 * {@link StarWarsGame}'s initial screen (design.md — asset loading). Shows
 * the game's logo on a plain black background with a progress bar while
 * every shared texture/atlas this app will ever need (see
 * {@link GameAssets}) loads once into {@link StarWarsGame#getAssets()} —
 * so every screen reached afterward only ever reads already-resident
 * assets, instead of each one loading/disposing its own copies on every
 * transition, which is what used to cause a noticeable pause switching
 * screens (per CLAUDE.md).
 * <p>
 * The logo itself is loaded synchronously first, in {@link #show()} —
 * a single small texture, negligible added blocking cost — specifically so
 * it's available to actually draw on this very screen; everything else is
 * then queued via {@link GameAssets#queueAll} for the asynchronous loading
 * {@link #render} drives one step at a time via {@link AssetManager#update()},
 * drawing the progress bar from {@link AssetManager#getProgress()} until
 * loading completes and this screen hands off to {@link ConnectScreen}.
 */
public class SplashScreen implements Screen {

    /** Fraction of the screen width the logo's own width fills - untuned placeholder. */
    private static final float LOGO_WIDTH_FRACTION = 0.5f;

    private static final float PROGRESS_BAR_WIDTH_FRACTION = 0.4f;
    private static final float PROGRESS_BAR_HEIGHT = 10f;
    private static final float PROGRESS_BAR_BOTTOM_MARGIN = 60f;
    private static final Color PROGRESS_BAR_TRACK_COLOR = new Color(1f, 1f, 1f, 0.2f);
    private static final Color PROGRESS_BAR_FILL_COLOR = new Color(0.94f, 0.87f, 0.66f, 1f);

    private final StarWarsGame game;

    private SpriteBatch batch;
    private OrthographicCamera camera;
    private Texture logoTexture;
    private Texture whitePixel;

    /**
     * Creates the screen.
     *
     * @param game the game to read/populate {@link StarWarsGame#getAssets()}
     *             on and to switch away to {@link ConnectScreen} from once
     *             loading completes
     */
    public SplashScreen(StarWarsGame game) {
        this.game = game;
    }

    @Override
    public void show() {
        batch = new SpriteBatch();
        camera = new OrthographicCamera();
        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        whitePixel = new Texture(pixmap);
        pixmap.dispose();

        AssetManager assets = game.getAssets();
        // Loaded and finished synchronously, ahead of everything else queued below, so the logo
        // is actually available to draw on this screen - GameAssets.queueAll queues it again
        // right after, which is a harmless no-op (AssetManager.load is ref-counted/idempotent for
        // an already-loaded path), not a second real load.
        assets.load(GameAssets.LOGO, Texture.class);
        assets.finishLoadingAsset(GameAssets.LOGO);
        logoTexture = assets.get(GameAssets.LOGO, Texture.class);

        GameAssets.queueAll(assets);
    }

    @Override
    public void render(float deltaTime) {
        ScreenUtils.clear(0f, 0f, 0f, 1f);

        AssetManager assets = game.getAssets();
        boolean done = assets.update();

        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawLogo(screenWidth, screenHeight);
        drawProgressBar(screenWidth, assets.getProgress());
        batch.end();

        if (done) {
            game.setScreen(new ConnectScreen(game));
            dispose();
        }
    }

    private void drawLogo(float screenWidth, float screenHeight) {
        float logoWidth = screenWidth * LOGO_WIDTH_FRACTION;
        float logoHeight = logoWidth * (logoTexture.getHeight() / (float) logoTexture.getWidth());
        float x = (screenWidth - logoWidth) / 2f;
        float y = (screenHeight - logoHeight) / 2f;
        batch.draw(logoTexture, x, y, logoWidth, logoHeight);
    }

    private void drawProgressBar(float screenWidth, float progress) {
        float barWidth = screenWidth * PROGRESS_BAR_WIDTH_FRACTION;
        float x = (screenWidth - barWidth) / 2f;
        float y = PROGRESS_BAR_BOTTOM_MARGIN;

        batch.setColor(PROGRESS_BAR_TRACK_COLOR);
        batch.draw(whitePixel, x, y, barWidth, PROGRESS_BAR_HEIGHT);

        batch.setColor(PROGRESS_BAR_FILL_COLOR);
        batch.draw(whitePixel, x, y, barWidth * progress, PROGRESS_BAR_HEIGHT);
        batch.setColor(Color.WHITE);
    }

    @Override
    public void resize(int width, int height) {
        camera.setToOrtho(false, width, height);
    }

    /**
     * No-op — this screen has no state that needs pausing.
     */
    @Override
    public void pause() {
    }

    /**
     * No-op, see {@link #pause()}.
     */
    @Override
    public void resume() {
    }

    /**
     * No-op — see {@code Client#hide()}'s identical reasoning.
     */
    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        batch.dispose();
        whitePixel.dispose();
        // logoTexture is owned by StarWarsGame#getAssets() (loaded via GameAssets.LOGO above),
        // not this screen - it's disposed once, along with everything else, when the asset
        // manager itself is (StarWarsGame#dispose()), not here.
    }
}
