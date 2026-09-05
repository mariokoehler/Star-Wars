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
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.physics.box2d.World;
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
import de.mkoehler.starwars.sim.ShipFactory;
import de.mkoehler.starwars.sim.ShipStats;
import de.mkoehler.starwars.sim.systems.PhysicsSystem;
import de.mkoehler.starwars.sim.systems.ShipControlSystem;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * {@link com.badlogic.gdx.ApplicationListener} implementation shared by all
 * platforms.
 * <p>
 * Second networked milestone: client-side prediction. The client runs its
 * own local Box2D body for its own ship, applying held input to it
 * immediately every frame (via the exact same {@link ShipControlSystem#applyInput}
 * math the server uses) so movement feels instant, rather than waiting for a
 * server round trip. Each server {@link WorldSnapshotMessage} then
 * reconciles that local prediction against the authoritative state — a
 * small blend toward the server's position/velocity for small errors, a
 * hard snap for large ones (see {@link #reconcileWithServer}). Other
 * players' ships are still simple snapshot interpolation, unchanged from the
 * previous milestone — predicting someone else's ship isn't possible without
 * knowing their future input.
 * <p>
 * {@link NetworkClient}'s callbacks run on KryoNet's own thread, not the
 * render thread, so incoming messages are queued in {@link #pendingUpdates}
 * and only applied at the start of {@link #render()} — never mutate
 * {@link #ships}, {@link #myBody} or the local Box2D {@link #localWorld}
 * directly from a network callback.
 */
public class Client extends ApplicationAdapter {

    private static final String SERVER_HOST = "localhost";
    private static final String DISPLAY_NAME = "Pilot";

    /** How quickly another player's ship's drawn position eases toward its latest network target each frame. */
    private static final float SHIP_INTERPOLATION_SPEED = 10f;
    /** How quickly the camera eases toward the local ship each frame; not the full model from design.md 4.1. */
    private static final float CAMERA_FOLLOW_SPEED = 3f;

    /** Reconciliation error, in meters, beyond which the local prediction hard-snaps to the server's state instead of blending. */
    private static final float RECONCILE_SNAP_THRESHOLD_METERS = 3f;
    /** Fraction of a small reconciliation error corrected per snapshot, rather than all at once. */
    private static final float RECONCILE_SOFT_BLEND = 0.2f;

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

    private World localWorld;
    private PhysicsSystem localPhysicsSystem;
    private Body myBody;
    private float myPreviousX;
    private float myPreviousY;
    private float myPreviousAngle;

    @Override
    public void create() {
        Box2D.init();

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
                // `ships`/`myBody`/`localWorld` directly (see class Javadoc).
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

        localWorld = new World(new Vector2(0, 0), true);
        localPhysicsSystem = new PhysicsSystem(localWorld);
        myBody = ShipFactory.createBody(localWorld, joined.getSpawnX(), joined.getSpawnY(), ShipStats.XWING);
        myPreviousX = myBody.getPosition().x;
        myPreviousY = myBody.getPosition().y;
        myPreviousAngle = myBody.getAngle();
    }

    private void onWorldSnapshot(WorldSnapshotMessage snapshot) {
        for (ShipState state : snapshot.getShips()) {
            if (state.getPlayerId() == myPlayerId) {
                reconcileWithServer(state);
                continue;
            }
            RemoteShip ship = ships.computeIfAbsent(state.getPlayerId(), id ->
                new RemoteShip(state.getX() * PhysicsConstants.PIXELS_PER_METER,
                    state.getY() * PhysicsConstants.PIXELS_PER_METER, state.getAngle()));
            ship.targetX = state.getX() * PhysicsConstants.PIXELS_PER_METER;
            ship.targetY = state.getY() * PhysicsConstants.PIXELS_PER_METER;
            ship.targetAngle = state.getAngle();
        }
    }

    /**
     * Corrects the local prediction body against the server's authoritative
     * state for it: a small blend for a small error (smooths out normal
     * prediction/authority drift without a visible pop), or a hard snap
     * (including velocity) for a large one, so a bad desync — e.g. from a
     * burst of dropped packets — can't leave the local prediction
     * permanently wrong.
     *
     * @param state the local player's ship state from the latest snapshot
     */
    private void reconcileWithServer(ShipState state) {
        float dx = state.getX() - myBody.getPosition().x;
        float dy = state.getY() - myBody.getPosition().y;
        float errorMeters = (float) Math.sqrt(dx * dx + dy * dy);

        if (errorMeters > RECONCILE_SNAP_THRESHOLD_METERS) {
            myBody.setTransform(state.getX(), state.getY(), state.getAngle());
            myBody.setLinearVelocity(state.getVelocityX(), state.getVelocityY());
            myBody.setAngularVelocity(state.getAngularVelocity());
        } else {
            float blendedX = MathUtils.lerp(myBody.getPosition().x, state.getX(), RECONCILE_SOFT_BLEND);
            float blendedY = MathUtils.lerp(myBody.getPosition().y, state.getY(), RECONCILE_SOFT_BLEND);
            float blendedAngle = MathUtils.lerpAngle(myBody.getAngle(), state.getAngle(), RECONCILE_SOFT_BLEND);
            myBody.setTransform(blendedX, blendedY, blendedAngle);
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

        if (myPlayerId >= 0) {
            boolean thrustForward = Gdx.input.isKeyPressed(Input.Keys.W);
            boolean thrustReverse = Gdx.input.isKeyPressed(Input.Keys.S);
            boolean turnLeft = Gdx.input.isKeyPressed(Input.Keys.A);
            boolean turnRight = Gdx.input.isKeyPressed(Input.Keys.D);

            networkClient.sendUDP(new PlayerInputMessage(thrustForward, thrustReverse, turnLeft, turnRight));
            predictLocalShip(thrustForward, thrustReverse, turnLeft, turnRight, deltaTime);
        }

        interpolateRemoteShips(deltaTime);
        updateCamera(deltaTime);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        background.render(batch, camera);
        drawRemoteShips();
        drawLocalShip();
        batch.end();
    }

    private void predictLocalShip(boolean thrustForward, boolean thrustReverse, boolean turnLeft, boolean turnRight, float deltaTime) {
        myPreviousX = myBody.getPosition().x;
        myPreviousY = myBody.getPosition().y;
        myPreviousAngle = myBody.getAngle();

        // Reapply input before every individual physics step (see PhysicsSystem#update(float,
        // Runnable)), not just once here - a frame hitch can make this need more than one step,
        // and Box2D clears applied forces/torque after each one.
        localPhysicsSystem.update(deltaTime, () ->
            ShipControlSystem.applyInput(myBody, ShipStats.XWING.getThrustForce(), ShipStats.XWING.getTurnTorque(),
                thrustForward, thrustReverse, turnLeft, turnRight));
    }

    private void interpolateRemoteShips(float deltaTime) {
        float lerp = MathUtils.clamp(SHIP_INTERPOLATION_SPEED * deltaTime, 0f, 1f);
        for (RemoteShip ship : ships.values()) {
            ship.renderX += (ship.targetX - ship.renderX) * lerp;
            ship.renderY += (ship.targetY - ship.renderY) * lerp;
            ship.renderAngle = MathUtils.lerpAngle(ship.renderAngle, ship.targetAngle, lerp);
        }
    }

    private void updateCamera(float deltaTime) {
        if (myBody == null) {
            return;
        }
        float alpha = localPhysicsSystem.getAlpha();
        float targetX = MathUtils.lerp(myPreviousX, myBody.getPosition().x, alpha) * PhysicsConstants.PIXELS_PER_METER;
        float targetY = MathUtils.lerp(myPreviousY, myBody.getPosition().y, alpha) * PhysicsConstants.PIXELS_PER_METER;

        float lerp = MathUtils.clamp(CAMERA_FOLLOW_SPEED * deltaTime, 0f, 1f);
        camera.position.x += (targetX - camera.position.x) * lerp;
        camera.position.y += (targetY - camera.position.y) * lerp;
        camera.update();
    }

    private void drawRemoteShips() {
        float widthPixels = ShipStats.XWING.getRadiusMeters() * 2f * PhysicsConstants.PIXELS_PER_METER;
        float heightPixels = widthPixels;

        batch.setColor(OTHER_SHIP_TINT);
        for (RemoteShip ship : ships.values()) {
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

    private void drawLocalShip() {
        if (myBody == null) {
            return;
        }
        float alpha = localPhysicsSystem.getAlpha();
        float x = MathUtils.lerp(myPreviousX, myBody.getPosition().x, alpha) * PhysicsConstants.PIXELS_PER_METER;
        float y = MathUtils.lerp(myPreviousY, myBody.getPosition().y, alpha) * PhysicsConstants.PIXELS_PER_METER;
        float angle = MathUtils.lerpAngle(myPreviousAngle, myBody.getAngle(), alpha);

        float widthPixels = ShipStats.XWING.getRadiusMeters() * 2f * PhysicsConstants.PIXELS_PER_METER;
        float heightPixels = widthPixels;

        batch.draw(xwingRegion,
            x - widthPixels / 2f, y - heightPixels / 2f,
            widthPixels / 2f, heightPixels / 2f,
            widthPixels, heightPixels,
            1f, 1f,
            angle * MathUtils.radiansToDegrees);
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
        if (localWorld != null) {
            localWorld.dispose();
        }
        batch.dispose();
        shipsAtlas.dispose();
        background.dispose();
    }

    /** Another player's ship, eased toward the latest network-reported target each frame. */
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
