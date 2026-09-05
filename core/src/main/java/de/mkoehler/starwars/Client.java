package de.mkoehler.starwars;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import de.mkoehler.starwars.net.NetworkClient;
import de.mkoehler.starwars.net.NetworkConstants;
import de.mkoehler.starwars.net.messages.PlayerInputMessage;
import de.mkoehler.starwars.net.messages.PlayerJoinedMessage;
import de.mkoehler.starwars.net.messages.PlayerLeftMessage;
import de.mkoehler.starwars.net.messages.ShipState;
import de.mkoehler.starwars.net.messages.WorldSnapshotMessage;
import de.mkoehler.starwars.render.ParallaxBackground;
import de.mkoehler.starwars.render.PlaceholderStarfield;
import de.mkoehler.starwars.sim.PhysicsConstants;
import de.mkoehler.starwars.sim.ShipStats;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * {@link com.badlogic.gdx.ApplicationListener} implementation shared by all
 * platforms.
 * <p>
 * First networked milestone: the server is the sole simulator of ship
 * physics (design.md 3.5); this client sends its held input to the server
 * and renders every ship — including its own — purely from broadcast
 * {@link WorldSnapshotMessage}s, easing each ship's drawn position/angle
 * toward the latest received values rather than snapping to them. There is
 * no client-side prediction yet, so input has a visible network round trip
 * before it's reflected on screen; that's a deliberate simplification for
 * this milestone, not an oversight.
 * <p>
 * {@link NetworkClient}'s callbacks run on KryoNet's own thread, not the
 * render thread, so incoming messages are queued in {@link #pendingUpdates}
 * and only applied at the start of {@link #render()} — never mutate
 * {@link #ships} directly from a network callback.
 */
public class Client extends ApplicationAdapter {

    private static final String SERVER_HOST = "localhost";
    private static final String DISPLAY_NAME = "Pilot";

    /** How quickly a ship's drawn position eases toward its latest network target each frame. */
    private static final float SHIP_INTERPOLATION_SPEED = 10f;
    /** How quickly the camera eases toward the local ship each frame; not the full model from design.md 4.1. */
    private static final float CAMERA_FOLLOW_SPEED = 3f;

    private static final Color OTHER_SHIP_TINT = new Color(0.6f, 0.85f, 1f, 1f);

    private SpriteBatch batch;
    private TextureAtlas shipsAtlas;
    private TextureRegion xwingRegion;
    private ParallaxBackground background;
    private OrthographicCamera camera;
    private Viewport viewport;

    private NetworkClient networkClient;
    private final Queue<Runnable> pendingUpdates = new ConcurrentLinkedQueue<>();
    private final Map<Integer, RemoteShip> ships = new HashMap<>();
    private int myPlayerId = -1;

    @Override
    public void create() {
        batch = new SpriteBatch();
        shipsAtlas = new TextureAtlas(Gdx.files.internal("textures/ships.atlas"));
        xwingRegion = shipsAtlas.findRegion("xwing/xwing128", 20);

        background = new ParallaxBackground(
            new ParallaxBackground.Layer(new Texture(
                Gdx.files.internal("textures/backgrounds/blue_nebula.png")), 0.1f),
            new ParallaxBackground.Layer(PlaceholderStarfield.generate(512, 120, 1L), 0.4f)
        );

        camera = new OrthographicCamera();
        viewport = new ScreenViewport(camera);
        viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);

        connectToServer();
    }

    private void connectToServer() {
        networkClient = new NetworkClient() {
            @Override
            protected void onReceived(Object object) {
                // Runs on KryoNet's network thread - only ever enqueue here, never touch
                // `ships`/`myPlayerId` directly (see class Javadoc).
                if (object instanceof PlayerJoinedMessage joined) {
                    pendingUpdates.add(() -> onPlayerJoined(joined));
                } else if (object instanceof WorldSnapshotMessage snapshot) {
                    pendingUpdates.add(() -> onWorldSnapshot(snapshot));
                } else if (object instanceof PlayerLeftMessage left) {
                    pendingUpdates.add(() -> ships.remove(left.getPlayerId()));
                }
            }
        };
        try {
            networkClient.connect(NetworkConstants.CONNECTION_TIMEOUT_MILLIS, SERVER_HOST,
                NetworkConstants.TCP_PORT, NetworkConstants.UDP_PORT);
        } catch (IOException e) {
            // No Connect Dialog / error screen yet (design.md 4.1) - a real failure here is
            // simply fatal for now.
            throw new IllegalStateException("Failed to connect to " + SERVER_HOST, e);
        }
        networkClient.sendHandshake(DISPLAY_NAME);
    }

    private void onPlayerJoined(PlayerJoinedMessage joined) {
        myPlayerId = joined.getPlayerId();
        float x = joined.getSpawnX() * PhysicsConstants.PIXELS_PER_METER;
        float y = joined.getSpawnY() * PhysicsConstants.PIXELS_PER_METER;
        ships.put(myPlayerId, new RemoteShip(x, y, 0f));
    }

    private void onWorldSnapshot(WorldSnapshotMessage snapshot) {
        for (ShipState state : snapshot.getShips()) {
            RemoteShip ship = ships.computeIfAbsent(state.getPlayerId(), id ->
                new RemoteShip(state.getX() * PhysicsConstants.PIXELS_PER_METER,
                    state.getY() * PhysicsConstants.PIXELS_PER_METER, state.getAngle()));
            ship.targetX = state.getX() * PhysicsConstants.PIXELS_PER_METER;
            ship.targetY = state.getY() * PhysicsConstants.PIXELS_PER_METER;
            ship.targetAngle = state.getAngle();
        }
    }

    @Override
    public void render() {
        ScreenUtils.clear(0.05f, 0.05f, 0.08f, 1f);

        float deltaTime = Gdx.graphics.getDeltaTime();

        Runnable update;
        while ((update = pendingUpdates.poll()) != null) {
            update.run();
        }

        sendInput();
        interpolateShips(deltaTime);
        updateCamera(deltaTime);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        background.render(batch, camera);
        drawShips();
        batch.end();
    }

    private void sendInput() {
        if (myPlayerId < 0) {
            return;
        }
        networkClient.sendUDP(new PlayerInputMessage(
            Gdx.input.isKeyPressed(Input.Keys.W),
            Gdx.input.isKeyPressed(Input.Keys.S),
            Gdx.input.isKeyPressed(Input.Keys.A),
            Gdx.input.isKeyPressed(Input.Keys.D)));
    }

    private void interpolateShips(float deltaTime) {
        float lerp = MathUtils.clamp(SHIP_INTERPOLATION_SPEED * deltaTime, 0f, 1f);
        for (RemoteShip ship : ships.values()) {
            ship.renderX += (ship.targetX - ship.renderX) * lerp;
            ship.renderY += (ship.targetY - ship.renderY) * lerp;
            ship.renderAngle = MathUtils.lerpAngle(ship.renderAngle, ship.targetAngle, lerp);
        }
    }

    private void updateCamera(float deltaTime) {
        RemoteShip localShip = ships.get(myPlayerId);
        if (localShip == null) {
            return;
        }
        float lerp = MathUtils.clamp(CAMERA_FOLLOW_SPEED * deltaTime, 0f, 1f);
        camera.position.x += (localShip.renderX - camera.position.x) * lerp;
        camera.position.y += (localShip.renderY - camera.position.y) * lerp;
        camera.update();
    }

    private void drawShips() {
        float widthPixels = ShipStats.XWING.getRadiusMeters() * 2f * PhysicsConstants.PIXELS_PER_METER;
        float heightPixels = widthPixels;

        for (Map.Entry<Integer, RemoteShip> entry : ships.entrySet()) {
            RemoteShip ship = entry.getValue();
            batch.setColor(entry.getKey() == myPlayerId ? Color.WHITE : OTHER_SHIP_TINT);
            // The source art faces up/north when unrotated (design.md 4.3), and the server's
            // ShipControlSystem treats angle 0 as "facing north" too - so the ship's angle
            // maps directly onto the region's rotation with no offset needed.
            batch.draw(xwingRegion,
                ship.renderX - widthPixels / 2f, ship.renderY - heightPixels / 2f,
                widthPixels / 2f, heightPixels / 2f,
                widthPixels, heightPixels,
                1f, 1f,
                ship.renderAngle * MathUtils.radiansToDegrees);
        }
        batch.setColor(Color.WHITE);
    }

    @Override
    public void resize(int width, int height) {
        // false: don't recenter the camera on the world origin, keep wherever it's currently
        // following the ship - only the visible area changes, matching the current position.
        viewport.update(width, height, false);
    }

    @Override
    public void dispose() {
        if (networkClient != null) {
            networkClient.stop();
        }
        batch.dispose();
        shipsAtlas.dispose();
        background.dispose();
    }

    /** A ship's drawn state, eased toward the latest network-reported target each frame. */
    private static final class RemoteShip {
        float renderX;
        float renderY;
        float renderAngle;
        float targetX;
        float targetY;
        float targetAngle;

        RemoteShip(float x, float y, float angle) {
            renderX = targetX = x;
            renderY = targetY = y;
            renderAngle = targetAngle = angle;
        }
    }
}
