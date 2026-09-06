package de.mkoehler.starwars;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
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
import de.mkoehler.starwars.net.messages.PlayerLeftMessage;
import de.mkoehler.starwars.net.messages.PowerAdjustMessage;
import de.mkoehler.starwars.net.messages.ProjectileState;
import de.mkoehler.starwars.net.messages.ShipDestroyedMessage;
import de.mkoehler.starwars.net.messages.ShipSpawnedMessage;
import de.mkoehler.starwars.net.messages.ShipState;
import de.mkoehler.starwars.net.messages.WorldSnapshotMessage;
import de.mkoehler.starwars.render.ParallaxBackground;
import de.mkoehler.starwars.render.PlaceholderStarfield;
import de.mkoehler.starwars.render.PowerDistributionHud;
import de.mkoehler.starwars.render.ShipStatusHud;
import de.mkoehler.starwars.sim.PhysicsConstants;
import de.mkoehler.starwars.sim.PowerDistribution;
import de.mkoehler.starwars.sim.PowerSystem;
import de.mkoehler.starwars.sim.ShipFactory;
import de.mkoehler.starwars.sim.ShipStats;
import de.mkoehler.starwars.sim.ShipType;
import de.mkoehler.starwars.sim.WeaponStats;
import de.mkoehler.starwars.sim.systems.PhysicsSystem;
import de.mkoehler.starwars.sim.systems.ShipControlSystem;

import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The gameplay {@link Screen} — reached from {@link ShipSelectionScreen} via
 * {@link StarWarsGame}, which owns the actual {@code ApplicationListener}
 * now that the app has more than one screen (design.md 5.1).
 * <p>
 * Third networked milestone: weapons/combat. The client sends its held fire
 * input alongside movement input; the server is the sole simulator of
 * projectiles and hit detection (design.md 3.5/2.3-adjacent — see
 * {@code GameNetworkServer}). Projectiles are never predicted, even the
 * local player's own — they're drawn purely from
 * {@link WorldSnapshotMessage#getProjectiles()}, extrapolated (dead-reckoned)
 * from the last snapshot using known velocity rather than eased toward it —
 * easing lags behind anything moving at real speed (a 50m/s projectile eased
 * at this class's original rate would trail its true position by ~5m at
 * steady state, more than double a ship's radius — exactly why shots used to
 * visually vanish well before reaching a target). Other players' ships use
 * the same extrapolation, using the velocity already carried in
 * {@link ShipState}. Local ship movement prediction/reconciliation is
 * unchanged from the previous milestone.
 * <p>
 * Power distribution (design.md 2.2): {@link #myPowerDistribution} is a
 * locally-mirrored copy of this ship's server-authoritative power split,
 * kept in sync purely by sending the exact same discrete keypress events
 * ({@code PowerAdjustMessage}) the local copy applies to itself, over the
 * reliable/ordered TCP channel — see
 * {@code PowerDistributionComponent}'s Javadoc for why the two can't
 * actually diverge. Used both to render {@link #powerHud} and to scale
 * {@link #predictLocalShip}'s thrust/torque, so local prediction matches
 * what the server will compute for the same input.
 * <p>
 * {@link NetworkClient}'s callbacks run on KryoNet's own thread, not the
 * render thread, so incoming messages are queued in {@link #pendingUpdates}
 * and only applied at the start of {@link #render(float)} — never mutate
 * {@link #ships}, {@link #projectiles}, {@link #myBody} or the local Box2D
 * {@link #localWorld} directly from a network callback.
 */
public class Client implements Screen {

    private static final String SERVER_HOST = "localhost";
    private static final String DISPLAY_NAME = "Pilot";

    /** How quickly the camera eases toward the local ship each frame; not the full model from design.md 4.1. */
    private static final float CAMERA_FOLLOW_SPEED = 3f;

    /** Reconciliation error, in meters, beyond which the local prediction hard-snaps to the server's state instead of blending. */
    private static final float RECONCILE_SNAP_THRESHOLD_METERS = 3f;
    /** Fraction of a small reconciliation error corrected per snapshot, rather than all at once. */
    private static final float RECONCILE_SOFT_BLEND = 0.2f;

    private static final Color OTHER_SHIP_TINT = new Color(0.6f, 0.85f, 1f, 1f);

    /** Size, in screen pixels, of the ship status HUD widget - placeholder until tuned by feel. */
    private static final float HUD_STATUS_SIZE = 220f;
    /** Screen-pixel margin from the bottom-left corner for the ship status HUD widget. */
    private static final float HUD_STATUS_MARGIN = 24f;
    /** Size, in screen pixels, of the power-distribution HUD widget - placeholder until tuned by feel. */
    private static final float HUD_POWER_SIZE = 220f;
    /** Horizontal gap, in screen pixels, between the ship-status and power-distribution HUD widgets. */
    private static final float HUD_POWER_GAP = 16f;
    /** How long a power-distribution keybind must be held before it maximizes its system instead of just incrementing it - untuned placeholder. */
    private static final float HOLD_TO_MAXIMIZE_SECONDS = 0.4f;

    private final ShipType selectedShipType;

    private SpriteBatch batch;
    private TextureAtlas shipsAtlas;
    private final Map<ShipType, TextureRegion> shipRegionsByType = new EnumMap<>(ShipType.class);
    private TextureAtlas projectilesAtlas;
    private TextureRegion ownProjectileRegion;
    private TextureRegion enemyProjectileRegion;
    private ParallaxBackground background;
    private ShipStatusHud statusHud;
    private PowerDistributionHud powerHud;
    private OrthographicCamera camera;
    private Viewport viewport;
    private OrthographicCamera hudCamera;

    private NetworkClient networkClient;
    private final Queue<Runnable> pendingUpdates = new ConcurrentLinkedQueue<>();
    private final Map<Integer, RemoteShip> ships = new HashMap<>();
    private final Map<Integer, RemoteProjectile> projectiles = new HashMap<>();
    private int myPlayerId = -1;

    private World localWorld;
    private PhysicsSystem localPhysicsSystem;
    private Body myBody;
    private ShipType myShipType;
    private float myPreviousX;
    private float myPreviousY;
    private float myPreviousAngle;
    private float myHullCurrent;
    private float myHullMax;
    private float myShieldCurrent;
    private float myShieldMax;
    private PowerDistribution myPowerDistribution = PowerDistribution.even();
    private final PowerKeyHold shieldsHold = new PowerKeyHold();
    private final PowerKeyHold weaponsHold = new PowerKeyHold();
    private final PowerKeyHold enginesHold = new PowerKeyHold();

    /**
     * Creates the gameplay screen.
     *
     * @param selectedShipType the ship type chosen on the Ship Selection
     *                         screen (design.md 5.1), sent to the server at
     *                         handshake to spawn as
     */
    public Client(ShipType selectedShipType) {
        this.selectedShipType = selectedShipType;
    }

    @Override
    public void show() {
        Box2D.init();

        batch = new SpriteBatch();
        shipsAtlas = new TextureAtlas(Gdx.files.internal("textures/ships.atlas"));
        for (ShipType type : ShipType.values()) {
            shipRegionsByType.put(type, shipsAtlas.findRegion(hullRegionName(type), 20));
        }
        projectilesAtlas = new TextureAtlas(Gdx.files.internal("textures/projectiles.atlas"));
        ownProjectileRegion = projectilesAtlas.findRegion("red_dot");
        enemyProjectileRegion = projectilesAtlas.findRegion("blue_dot");

        background = new ParallaxBackground(
            new ParallaxBackground.Layer(new Texture(
                Gdx.files.internal("textures/backgrounds/blue_nebula.png")), 0.1f),
            new ParallaxBackground.Layer(PlaceholderStarfield.generate(512, 120, 1L), 0.4f)
        );
        statusHud = new ShipStatusHud();
        powerHud = new PowerDistributionHud();

        camera = new OrthographicCamera();
        viewport = new ScreenViewport(camera);
        viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);

        // A separate, un-zoomed, un-panned camera for the HUD layer - screen pixel coordinates
        // with (0,0) at the bottom-left, unrelated to the world camera's position/zoom.
        hudCamera = new OrthographicCamera();
        hudCamera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        connectToServer();
    }

    /**
     * Maps a ship type to its {@code textures/ships.atlas} hull sprite
     * region name — unlike the portrait regions (uniformly
     * {@code <resourceName>/portrait}), hull frame region names don't
     * follow one convention (each ship's source filename varies — e.g.
     * {@code falcon256_0020.png}, {@code tie_fighter128_0020.png}), so this
     * is an explicit table, same reasoning as
     * {@code ShipSelectionScreen.descriptionRegionName}.
     */
    private static String hullRegionName(ShipType type) {
        return switch (type) {
            case XWING -> "xwing/xwing128";
            case FALCON -> "falcon/falcon256";
            case SNOWSPEEDER -> "snowspeeder/snowspeeder";
            case STARDESTROYER -> "stardestroyer/stardestroyer256";
            case TIEFIGHTER -> "tiefighter/tie_fighter128";
            case TIEINTERCEPTOR -> "tieinterceptor/interceptor256";
        };
    }

    private void connectToServer() {
        networkClient = new NetworkClient() {
            @Override
            protected void onReceived(Object object) {
                // Runs on KryoNet's network thread - only ever enqueue here, never touch
                // `ships`/`projectiles`/`myBody`/`localWorld` directly (see class Javadoc).
                if (object instanceof ShipSpawnedMessage spawned) {
                    pendingUpdates.add(() -> onShipSpawned(spawned));
                } else if (object instanceof WorldSnapshotMessage snapshot) {
                    pendingUpdates.add(() -> onWorldSnapshot(snapshot));
                } else if (object instanceof PlayerLeftMessage left) {
                    pendingUpdates.add(() -> ships.remove(left.getPlayerId()));
                } else if (object instanceof ShipDestroyedMessage destroyed) {
                    pendingUpdates.add(() -> onShipDestroyed(destroyed));
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
        networkClient.sendHandshake(DISPLAY_NAME, selectedShipType);
    }

    private void onShipSpawned(ShipSpawnedMessage spawned) {
        myPlayerId = spawned.getPlayerId();
        myShipType = spawned.getShipType();
        ShipStats myStats = ShipStats.forType(myShipType);

        if (localWorld == null) {
            localWorld = new World(new Vector2(0, 0), true);
            localPhysicsSystem = new PhysicsSystem(localWorld);
        } else if (myBody != null) {
            // Respawning after death (see onShipDestroyed) - the old body was already destroyed.
            localWorld.destroyBody(myBody);
        }
        myBody = ShipFactory.createBody(localWorld, spawned.getSpawnX(), spawned.getSpawnY(), myStats);
        myPreviousX = myBody.getPosition().x;
        myPreviousY = myBody.getPosition().y;
        myPreviousAngle = myBody.getAngle();

        // Full hull/shield until the first WorldSnapshotMessage arrives - otherwise the HUD
        // would flash empty for a frame or two right after spawning/respawning.
        myHullMax = myHullCurrent = myStats.getMaxHealth();
        myShieldMax = myShieldCurrent = myStats.getShieldMaxCapacity();

        // A fresh ship (spawn or respawn) always gets a fresh PowerDistributionComponent on the
        // server too (ShipFactory.createShip), so resetting the local mirror here keeps the two in
        // sync trivially, same reasoning as the hull/shield reset above.
        myPowerDistribution = PowerDistribution.even();
        shieldsHold.reset();
        weaponsHold.reset();
        enginesHold.reset();
    }

    private void onShipDestroyed(ShipDestroyedMessage destroyed) {
        if (destroyed.getPlayerId() == myPlayerId) {
            if (myBody != null) {
                localWorld.destroyBody(myBody);
                myBody = null;
            }
        } else {
            ships.remove(destroyed.getPlayerId());
        }
    }

    private void onWorldSnapshot(WorldSnapshotMessage snapshot) {
        for (ShipState state : snapshot.getShips()) {
            if (state.getPlayerId() == myPlayerId) {
                reconcileWithServer(state);
                myHullCurrent = state.getHullCurrent();
                myHullMax = state.getHullMax();
                myShieldCurrent = state.getShieldCurrent();
                myShieldMax = state.getShieldMax();
                continue;
            }
            float x = state.getX() * PhysicsConstants.PIXELS_PER_METER;
            float y = state.getY() * PhysicsConstants.PIXELS_PER_METER;
            RemoteShip ship = ships.computeIfAbsent(state.getPlayerId(),
                id -> new RemoteShip(x, y, state.getAngle(), state.getShipType()));
            ship.updateFromSnapshot(x, y, state.getAngle(),
                state.getVelocityX() * PhysicsConstants.PIXELS_PER_METER,
                state.getVelocityY() * PhysicsConstants.PIXELS_PER_METER,
                state.getAngularVelocity());
        }

        // Projectiles have no destroyed-notification of their own (design.md 3.5's
        // ProjectileState note) - presence in this snapshot means alive, so anything not
        // present anymore gets pruned below.
        Set<Integer> presentIds = new HashSet<>();
        for (ProjectileState state : snapshot.getProjectiles()) {
            presentIds.add(state.getProjectileId());
            float x = state.getX() * PhysicsConstants.PIXELS_PER_METER;
            float y = state.getY() * PhysicsConstants.PIXELS_PER_METER;
            RemoteProjectile projectile = projectiles.computeIfAbsent(state.getProjectileId(), id ->
                new RemoteProjectile(state.getOwnerPlayerId(), x, y, state.getAngle()));
            projectile.updateFromSnapshot(x, y, state.getAngle());
        }
        projectiles.keySet().removeIf(id -> !presentIds.contains(id));
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
        if (myBody == null) {
            return;
        }
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
    public void render(float deltaTime) {
        ScreenUtils.clear(0.05f, 0.05f, 0.08f, 1f);

        Runnable update;
        while ((update = pendingUpdates.poll()) != null) {
            update.run();
        }

        if (myBody != null) {
            boolean thrustForward = Gdx.input.isKeyPressed(Input.Keys.W);
            boolean thrustReverse = Gdx.input.isKeyPressed(Input.Keys.S);
            boolean turnLeft = Gdx.input.isKeyPressed(Input.Keys.A);
            boolean turnRight = Gdx.input.isKeyPressed(Input.Keys.D);
            boolean firing = Gdx.input.isKeyPressed(Input.Keys.SPACE);

            networkClient.sendUDP(new PlayerInputMessage(thrustForward, thrustReverse, turnLeft, turnRight, firing));
            predictLocalShip(thrustForward, thrustReverse, turnLeft, turnRight, deltaTime);
            handlePowerDistributionInput(deltaTime);
        }

        extrapolateRemoteShips(deltaTime);
        extrapolateProjectiles(deltaTime);
        updateCamera(deltaTime);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        background.render(batch, camera);
        drawRemoteShips();
        drawLocalShip();
        drawProjectiles();
        batch.end();

        // Separate begin/end pair with the HUD's own screen-space camera - SpriteBatch doesn't
        // reliably re-flush already-queued sprites if the projection matrix were swapped
        // mid-batch instead.
        batch.setProjectionMatrix(hudCamera.combined);
        batch.begin();
        drawHud();
        batch.end();
    }

    private void drawHud() {
        if (myBody == null) {
            return;
        }
        float hullFraction = myHullMax > 0f ? myHullCurrent / myHullMax : 0f;
        float shieldFraction = myShieldMax > 0f ? myShieldCurrent / myShieldMax : 0f;
        statusHud.render(batch, ShipStats.forType(myShipType), HUD_STATUS_MARGIN, HUD_STATUS_MARGIN, HUD_STATUS_SIZE,
            hullFraction, shieldFraction);
        powerHud.render(batch, HUD_STATUS_MARGIN + HUD_STATUS_SIZE + HUD_POWER_GAP, HUD_STATUS_MARGIN, HUD_POWER_SIZE,
            myPowerDistribution);
    }

    /**
     * Reads the power-distribution keybinds (design.md 5.3: J/I/L to shift
     * toward Shields/Weapons/Engines — matching the HUD's left-to-right
     * Shields/Weapons/Engines bar order, K to reset). A tap shifts the
     * split by exactly one increment ({@link #adjustPower}); holding a key
     * for {@link #HOLD_TO_MAXIMIZE_SECONDS} instead jumps that system
     * straight to its maximum ({@link #maximizePower}) — tracked per key via
     * {@link #shieldsHold}/{@link #weaponsHold}/{@link #enginesHold} so each
     * key's held-duration and whether it's already maximized this press are
     * independent of the others. Updates the local mirror immediately (for
     * instant HUD feedback and correct engine-thrust prediction this same
     * frame) and sends the same event to the server over the reliable
     * channel — see {@code PowerDistributionComponent}'s Javadoc for why the
     * two never diverge despite each applying this independently.
     *
     * @param deltaTime time since the last frame, in seconds — used to
     *                  accumulate how long a key has been held
     */
    private void handlePowerDistributionInput(float deltaTime) {
        handlePowerKey(Input.Keys.J, PowerSystem.SHIELDS, shieldsHold, deltaTime);
        handlePowerKey(Input.Keys.I, PowerSystem.WEAPONS, weaponsHold, deltaTime);
        handlePowerKey(Input.Keys.L, PowerSystem.ENGINES, enginesHold, deltaTime);

        if (Gdx.input.isKeyJustPressed(Input.Keys.K)) {
            myPowerDistribution = myPowerDistribution.reset();
            networkClient.sendTCP(new PowerAdjustMessage(PowerAdjustMessage.Kind.RESET, null));
        }
    }

    private void handlePowerKey(int keycode, PowerSystem target, PowerKeyHold hold, float deltaTime) {
        if (Gdx.input.isKeyJustPressed(keycode)) {
            adjustPower(target);
        }

        if (Gdx.input.isKeyPressed(keycode)) {
            hold.heldSeconds += deltaTime;
            if (!hold.maximized && hold.heldSeconds >= HOLD_TO_MAXIMIZE_SECONDS) {
                hold.maximized = true;
                maximizePower(target);
            }
        } else {
            hold.heldSeconds = 0f;
            hold.maximized = false;
        }
    }

    private void adjustPower(PowerSystem target) {
        myPowerDistribution = myPowerDistribution.adjust(target);
        networkClient.sendTCP(new PowerAdjustMessage(PowerAdjustMessage.Kind.ADJUST, target));
    }

    private void maximizePower(PowerSystem target) {
        myPowerDistribution = myPowerDistribution.maximize(target);
        networkClient.sendTCP(new PowerAdjustMessage(PowerAdjustMessage.Kind.MAXIMIZE, target));
    }

    private void predictLocalShip(boolean thrustForward, boolean thrustReverse, boolean turnLeft, boolean turnRight, float deltaTime) {
        myPreviousX = myBody.getPosition().x;
        myPreviousY = myBody.getPosition().y;
        myPreviousAngle = myBody.getAngle();

        ShipStats myStats = ShipStats.forType(myShipType);
        float enginesMultiplier = myPowerDistribution.multiplierFor(PowerSystem.ENGINES);
        // Reapply input before every individual physics step (see PhysicsSystem#update(float,
        // Runnable)), not just once here - a frame hitch can make this need more than one step,
        // and Box2D clears applied forces/torque after each one.
        localPhysicsSystem.update(deltaTime, () ->
            ShipControlSystem.applyInput(myBody, myStats.getThrustForce() * enginesMultiplier,
                myStats.getTurnTorque() * enginesMultiplier,
                thrustForward, thrustReverse, turnLeft, turnRight));
    }

    private void extrapolateRemoteShips(float deltaTime) {
        for (RemoteShip ship : ships.values()) {
            ship.extrapolate(deltaTime);
        }
    }

    private void extrapolateProjectiles(float deltaTime) {
        // Projectiles fly in a fixed direction at a fixed known speed for their whole flight
        // (no thrust/torque, no prediction) - so unlike ships, velocity doesn't need to come
        // from the server at all, it's fully determined by the weapon's stats and the angle
        // already in each snapshot.
        float speedPixels = WeaponStats.BLASTER.getProjectileSpeed() * PhysicsConstants.PIXELS_PER_METER;
        for (RemoteProjectile projectile : projectiles.values()) {
            projectile.extrapolate(deltaTime, speedPixels);
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
        batch.setColor(OTHER_SHIP_TINT);
        for (RemoteShip ship : ships.values()) {
            ShipStats stats = ShipStats.forType(ship.shipType);
            float widthPixels = stats.getSpriteWidthMeters() * PhysicsConstants.PIXELS_PER_METER;
            float heightPixels = stats.getSpriteHeightMeters() * PhysicsConstants.PIXELS_PER_METER;

            // The source art faces up/north when unrotated (design.md 4.3), and the server's
            // ShipControlSystem treats angle 0 as "facing north" too - so the ship's angle
            // maps directly onto the region's rotation with no offset needed.
            batch.draw(shipRegionsByType.get(ship.shipType),
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

        ShipStats myStats = ShipStats.forType(myShipType);
        float widthPixels = myStats.getSpriteWidthMeters() * PhysicsConstants.PIXELS_PER_METER;
        float heightPixels = myStats.getSpriteHeightMeters() * PhysicsConstants.PIXELS_PER_METER;

        batch.draw(shipRegionsByType.get(myShipType),
            x - widthPixels / 2f, y - heightPixels / 2f,
            widthPixels / 2f, heightPixels / 2f,
            widthPixels, heightPixels,
            1f, 1f,
            angle * MathUtils.radiansToDegrees);
    }

    private void drawProjectiles() {
        float sizePixels = WeaponStats.BLASTER.getProjectileRadiusMeters() * 2f * PhysicsConstants.PIXELS_PER_METER;

        for (RemoteProjectile projectile : projectiles.values()) {
            // Own shots draw red, everyone else's draw blue - purely a rendering choice
            // (design.md 3.5), the server treats every projectile identically.
            TextureRegion region = projectile.ownerPlayerId == myPlayerId ? ownProjectileRegion : enemyProjectileRegion;
            batch.draw(region,
                projectile.renderX - sizePixels / 2f, projectile.renderY - sizePixels / 2f,
                sizePixels / 2f, sizePixels / 2f,
                sizePixels, sizePixels,
                1f, 1f,
                projectile.angle * MathUtils.radiansToDegrees);
        }
    }

    @Override
    public void resize(int width, int height) {
        // false: don't recenter the camera on the world origin, keep wherever it's currently
        // following the ship - only the visible area changes, matching the current position.
        viewport.update(width, height, false);
        hudCamera.setToOrtho(false, width, height);
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
        projectilesAtlas.dispose();
        background.dispose();
        statusHud.dispose();
        powerHud.dispose();
    }

    /**
     * No-op — this screen has no state that needs pausing (unlike a mobile
     * app losing focus, the desktop target doesn't currently act on this).
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
     * No-op — nothing needs to release resources just because this screen
     * stops being the active one; {@link #dispose()} (called explicitly by
     * whoever switches away, see {@link ShipSelectionScreen}) handles actual
     * cleanup once the screen is really done, not merely hidden.
     */
    @Override
    public void hide() {
    }

    /**
     * Another player's ship. Rendered by dead reckoning — extrapolated
     * forward from the last snapshot's position/angle using its reported
     * velocity/angular velocity and time elapsed since that snapshot arrived
     * — rather than eased toward it, which would otherwise lag behind by an
     * amount proportional to how fast the ship is actually moving (see class
     * Javadoc).
     */
    private static final class RemoteShip {
        final ShipType shipType;
        float baseX;
        float baseY;
        float baseAngle;
        float velocityX;
        float velocityY;
        float angularVelocity;
        float elapsedSinceUpdate;
        float renderX;
        float renderY;
        float renderAngle;

        RemoteShip(float x, float y, float angle, ShipType shipType) {
            this.shipType = shipType;
            baseX = renderX = x;
            baseY = renderY = y;
            baseAngle = renderAngle = angle;
        }

        void updateFromSnapshot(float x, float y, float angle, float velocityX, float velocityY, float angularVelocity) {
            baseX = x;
            baseY = y;
            baseAngle = angle;
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            this.angularVelocity = angularVelocity;
            elapsedSinceUpdate = 0f;
        }

        void extrapolate(float deltaTime) {
            elapsedSinceUpdate += deltaTime;
            renderX = baseX + velocityX * elapsedSinceUpdate;
            renderY = baseY + velocityY * elapsedSinceUpdate;
            renderAngle = baseAngle + angularVelocity * elapsedSinceUpdate;
        }
    }

    /**
     * A projectile (anyone's, including the local player's own). Rendered by
     * dead reckoning, same reasoning as {@link RemoteShip} — extrapolated
     * along its fixed travel direction at the weapon's known constant speed,
     * rather than eased toward the latest snapshot.
     */
    private static final class RemoteProjectile {
        private static final Vector2 DIRECTION = new Vector2();

        final int ownerPlayerId;
        float baseX;
        float baseY;
        float angle;
        float elapsedSinceUpdate;
        float renderX;
        float renderY;

        RemoteProjectile(int ownerPlayerId, float x, float y, float angle) {
            this.ownerPlayerId = ownerPlayerId;
            baseX = renderX = x;
            baseY = renderY = y;
            this.angle = angle;
        }

        void updateFromSnapshot(float x, float y, float angle) {
            baseX = x;
            baseY = y;
            this.angle = angle;
            elapsedSinceUpdate = 0f;
        }

        void extrapolate(float deltaTime, float speedPixels) {
            elapsedSinceUpdate += deltaTime;
            DIRECTION.set(0, 1).rotateRad(angle).scl(speedPixels * elapsedSinceUpdate);
            renderX = baseX + DIRECTION.x;
            renderY = baseY + DIRECTION.y;
        }
    }

    /**
     * Tracks how long one power-distribution keybind has been continuously
     * held, and whether it's already triggered {@link #maximizePower} for
     * the current hold — so holding past {@link #HOLD_TO_MAXIMIZE_SECONDS}
     * maximizes exactly once per press, not repeatedly every frame the key
     * stays down.
     */
    private static final class PowerKeyHold {
        float heldSeconds;
        boolean maximized;

        void reset() {
            heldSeconds = 0f;
            maximized = false;
        }
    }
}
