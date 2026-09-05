package de.mkoehler.starwars;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import de.mkoehler.starwars.render.ParallaxBackground;
import de.mkoehler.starwars.render.PlaceholderStarfield;
import de.mkoehler.starwars.sim.PhysicsConstants;
import de.mkoehler.starwars.sim.ShipFactory;
import de.mkoehler.starwars.sim.components.PhysicsBodyComponent;
import de.mkoehler.starwars.sim.systems.PhysicsSystem;
import de.mkoehler.starwars.sim.systems.PlayerInputSystem;
import de.mkoehler.starwars.sim.systems.RenderSystem;

/**
 * {@link com.badlogic.gdx.ApplicationListener} implementation shared by all
 * platforms.
 * <p>
 * Currently a single-player Newtonian flight prototype: one player-controlled
 * ship, driven by Box2D/Ashley, with a camera that eases toward it. No
 * networking, accounts or UI screens yet (see design.md 3 and 5).
 * <p>
 * Drives {@code playerInputSystem}/{@code physicsSystem}/{@code renderSystem}
 * directly (not via a single {@code engine.update()} call) so the background
 * can be drawn, and the camera updated, at the exact point between physics
 * stepping and ship rendering where both need the same frame's freshly
 * computed interpolation state — see {@link PhysicsSystem#getAlpha()}. Revisit
 * once there's more than one entity/system pipeline to coordinate; Ashley's
 * engine-driven update (with camera-follow and background as their own
 * systems) would scale better than this manual sequencing.
 */
public class Client extends ApplicationAdapter {

    /** How quickly the camera eases toward the ship each frame; not the full speed/inertia model from design.md 4.1. */
    private static final float CAMERA_FOLLOW_SPEED = 3f;

    private SpriteBatch batch;
    private TextureAtlas shipsAtlas;
    private ParallaxBackground background;
    private World world;
    private Engine engine;
    private PlayerInputSystem playerInputSystem;
    private PhysicsSystem physicsSystem;
    private RenderSystem renderSystem;
    private OrthographicCamera camera;
    private Viewport viewport;
    private Entity playerShip;

    @Override
    public void create() {
        Box2D.init();

        batch = new SpriteBatch();
        shipsAtlas = new TextureAtlas(Gdx.files.internal("textures/ships.atlas"));

        // Real, tileable-but-opaque nebula art (CC0, see assets-raw/backgrounds/blue-nebula/
        // License.txt) as the furthest layer, with a transparent procedurally-generated star
        // layer (still a placeholder - design.md 4.2 - until real transparent star art is
        // sourced) drawn on top of it for closer, faster-scrolling depth.
        background = new ParallaxBackground(
            new ParallaxBackground.Layer(new Texture(
                Gdx.files.internal("textures/backgrounds/blue_nebula.png")), 0.1f),
            new ParallaxBackground.Layer(PlaceholderStarfield.generate(512, 120, 1L), 0.4f)
        );

        world = new World(new Vector2(0, 0), true);
        engine = new Engine();

        playerInputSystem = new PlayerInputSystem();
        physicsSystem = new PhysicsSystem(world);
        engine.addSystem(playerInputSystem);
        engine.addSystem(physicsSystem);

        // ScreenViewport rather than a fixed-size camera: our "world" units already are
        // screen pixels (via PhysicsConstants.PIXELS_PER_METER), so 1:1 mapping the camera's
        // viewport to the actual window size on resize shows more/less world with no
        // stretching or letterboxing, instead of distorting a fixed 1920x1080 view.
        camera = new OrthographicCamera();
        viewport = new ScreenViewport(camera);
        viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
        renderSystem = new RenderSystem(batch, camera, physicsSystem);
        engine.addSystem(renderSystem);

        TextureRegion xwingRegion = shipsAtlas.findRegion("xwing/xwing128", 20);
        float widthMeters = xwingRegion.getRegionWidth() / PhysicsConstants.PIXELS_PER_METER;
        float heightMeters = xwingRegion.getRegionHeight() / PhysicsConstants.PIXELS_PER_METER;
        playerShip = ShipFactory.createPlayerShip(engine, world, 0f, 0f,
            xwingRegion, widthMeters, heightMeters,
            30f, 22.5f);
    }

    @Override
    public void render() {
        ScreenUtils.clear(0.05f, 0.05f, 0.08f, 1f);

        float deltaTime = Gdx.graphics.getDeltaTime();

        // Deliberately not engine.update(deltaTime): input must apply before physics steps,
        // physics must step before the camera reads this frame's interpolated ship position,
        // and the background must draw (using that camera) before the ship does.
        playerInputSystem.update(deltaTime);
        physicsSystem.update(deltaTime);

        updateCamera(physicsSystem.getAlpha(), deltaTime);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        background.render(batch, camera);
        batch.end();

        renderSystem.update(deltaTime);
    }

    private void updateCamera(float alpha, float deltaTime) {
        Vector2 shipPosition = playerShip.getComponent(PhysicsBodyComponent.class).getInterpolatedPosition(alpha);
        float targetX = shipPosition.x * PhysicsConstants.PIXELS_PER_METER;
        float targetY = shipPosition.y * PhysicsConstants.PIXELS_PER_METER;

        float lerp = MathUtils.clamp(CAMERA_FOLLOW_SPEED * deltaTime, 0f, 1f);
        camera.position.x += (targetX - camera.position.x) * lerp;
        camera.position.y += (targetY - camera.position.y) * lerp;
        camera.update();
    }

    @Override
    public void resize(int width, int height) {
        // false: don't recenter the camera on the world origin, keep wherever it's currently
        // following the ship - only the visible area changes, matching the current position.
        viewport.update(width, height, false);
    }

    @Override
    public void dispose() {
        batch.dispose();
        shipsAtlas.dispose();
        background.dispose();
        world.dispose();
    }
}
