package de.mkoehler.starwars;

import com.badlogic.gdx.Game;
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
import de.mkoehler.starwars.net.messages.HandshakeResponse;
import de.mkoehler.starwars.net.messages.LeaveMatchDeniedMessage;
import de.mkoehler.starwars.net.messages.LeaveMatchRequest;
import de.mkoehler.starwars.net.messages.PlayerInputMessage;
import de.mkoehler.starwars.net.messages.PlayerLeftMessage;
import de.mkoehler.starwars.net.messages.PowerAdjustMessage;
import de.mkoehler.starwars.net.messages.ProjectileState;
import de.mkoehler.starwars.net.messages.ShipDestroyedMessage;
import de.mkoehler.starwars.net.messages.ShipSpawnedMessage;
import de.mkoehler.starwars.net.messages.ShipState;
import de.mkoehler.starwars.net.messages.SpawnRequest;
import de.mkoehler.starwars.net.messages.TurretToggleMessage;
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
import de.mkoehler.starwars.sim.metadata.PixelPoint;
import de.mkoehler.starwars.sim.metadata.TurretConfig;
import de.mkoehler.starwars.sim.systems.PhysicsSystem;
import de.mkoehler.starwars.sim.systems.ShipControlSystem;

import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
 * <p>
 * Leaving a match (design.md 2.3): pressing ESC sends a
 * {@code LeaveMatchRequest} and sets {@link #leavingMatch}, then waits for
 * the server's authoritative answer — a {@link ShipDestroyedMessage} for
 * this player (granted; {@link #onShipDestroyed} sees {@link #leavingMatch}
 * set and calls {@link #returnToShipSelection}) or a
 * {@link LeaveMatchDeniedMessage} (denied; shows the warning banner and
 * clears {@link #leavingMatch}, leaving the ship untouched). This is also
 * how a granted leave is told apart from an ordinary combat death, which
 * reuses the exact same {@code ShipDestroyedMessage} — see design.md 2.3's
 * "visually indistinguishable" requirement — without needing a protocol
 * field for it. {@link #returnToShipSelection} disposes this screen
 * immediately after switching, same pattern as
 * {@code ShipSelectionScreen.startMatch()}; {@link #render}'s
 * {@link #transitionedAway} check exists for the identical reason that
 * fix does — don't touch this screen's just-disposed batch/textures later
 * in the same frame.
 */
public class Client implements Screen {

    /** How quickly the camera eases toward the local ship each frame; not the full model from design.md 4.1. */
    private static final float CAMERA_FOLLOW_SPEED = 3f;

    /** Reconciliation error, in meters, beyond which the local prediction hard-snaps to the server's state instead of blending. */
    private static final float RECONCILE_SNAP_THRESHOLD_METERS = 3f;
    /** Fraction of a small reconciliation error corrected per snapshot, rather than all at once. */
    private static final float RECONCILE_SOFT_BLEND = 0.2f;

    private static final Color OTHER_SHIP_TINT = new Color(0.6f, 0.85f, 1f, 1f);

    /** Scratch vector for {@link #drawTurrets} - avoids an allocation per turret per frame. */
    private static final Vector2 TURRET_OFFSET = new Vector2();

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

    /** How long the combat-lock warning banner stays on screen - untuned placeholder. */
    private static final float WARNING_MESSAGE_DURATION_SECONDS = 2.5f;
    /** On-screen width of the combat-lock warning banner - height follows from the source art's aspect ratio. */
    private static final float WARNING_BANNER_WIDTH = 720f;
    /** Gap from the top of the screen to the banner's top edge - kept near the top, deliberately away from the player's own ship (which stays near screen-center via camera-follow) since this fires during tense moments. */
    private static final float WARNING_BANNER_TOP_MARGIN = 48f;

    private final Game game;
    private final ShipType selectedShipType;
    private final ConnectionInfo connectionInfo;

    private SpriteBatch batch;
    private TextureAtlas shipsAtlas;
    private final Map<ShipType, TextureRegion> shipRegionsByType = new EnumMap<>(ShipType.class);
    private final Map<ShipType, TextureRegion> turretRegionsByType = new EnumMap<>(ShipType.class);
    private TextureAtlas projectilesAtlas;
    private TextureRegion ownProjectileRegion;
    private TextureRegion enemyProjectileRegion;
    private ParallaxBackground background;
    private ShipStatusHud statusHud;
    private PowerDistributionHud powerHud;
    private Texture warningBannerTexture;
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
    private float[] myTurretAimAngles = new float[0];
    private PowerDistribution myPowerDistribution = PowerDistribution.even();
    private final PowerKeyHold shieldsHold = new PowerKeyHold();
    private final PowerKeyHold weaponsHold = new PowerKeyHold();
    private final PowerKeyHold enginesHold = new PowerKeyHold();

    /** Set once an ESC leave request has been sent, until the server grants or denies it (design.md 2.3). */
    private boolean leavingMatch;
    /** Set once {@link #returnToShipSelection} disposes this screen - {@link #render} must not touch anything of it afterward, same frame. */
    private boolean transitionedAway;
    /** Counts down while the combat-lock warning banner is shown; not showing it at all once it reaches zero. */
    private float warningMessageSecondsRemaining;

    /**
     * Creates the gameplay screen.
     *
     * @param game             the game to switch back to {@link ShipSelectionScreen} from,
     *                         once the player leaves this match (design.md 2.3/5.1)
     * @param selectedShipType the ship type chosen on the Ship Selection
     *                         screen (design.md 5.1), sent to the server in
     *                         a {@link de.mkoehler.starwars.net.messages.SpawnRequest}
     *                         once (re)handshaking succeeds
     * @param connectionInfo   the already-validated login this session was
     *                         established with on the Connect Dialog
     *                         (design.md 5.1) - re-sent on this screen's own
     *                         fresh connection (design.md 3.6 - logging in
     *                         again with the same credentials is harmless)
     */
    public Client(Game game, ShipType selectedShipType, ConnectionInfo connectionInfo) {
        this.game = game;
        this.selectedShipType = selectedShipType;
        this.connectionInfo = connectionInfo;
    }

    @Override
    public void show() {
        Box2D.init();

        batch = new SpriteBatch();
        shipsAtlas = new TextureAtlas(Gdx.files.internal("textures/ships.atlas"));
        for (ShipType type : ShipType.values()) {
            shipRegionsByType.put(type, shipsAtlas.findRegion(hullRegionName(type), 20));
        }
        // Only the two ship types that actually have turrets (design.md — turret weapons) get an
        // entry here; every other ship type simply has none, which drawTurrets treats as "nothing
        // to draw" rather than an error.
        turretRegionsByType.put(ShipType.FALCON, shipsAtlas.findRegion("turrets/turret40"));
        turretRegionsByType.put(ShipType.STARDESTROYER, shipsAtlas.findRegion("turrets/turret32"));
        projectilesAtlas = new TextureAtlas(Gdx.files.internal("textures/projectiles.atlas"));
        ownProjectileRegion = projectilesAtlas.findRegion("red_oval");
        enemyProjectileRegion = projectilesAtlas.findRegion("blue_oval");

        background = new ParallaxBackground(
            new ParallaxBackground.Layer(new Texture(
                Gdx.files.internal("textures/backgrounds/blue_nebula.png")), 0.1f),
            new ParallaxBackground.Layer(PlaceholderStarfield.generate(512, 120, 1L), 0.4f)
        );
        statusHud = new ShipStatusHud();
        powerHud = new PowerDistributionHud();
        warningBannerTexture = new Texture(Gdx.files.internal("textures/hud/hud_warning_ejection_locked.png"));

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
     * {@code falcon256_0020.png}, {@code tie_fighter256_0020.png}), so this
     * is an explicit table, same reasoning as
     * {@code ShipSelectionScreen.descriptionRegionName}.
     */
    private static String hullRegionName(ShipType type) {
        return switch (type) {
            case XWING -> "xwing/xwing128";
            case FALCON -> "falcon/falcon256";
            case SNOWSPEEDER -> "snowspeeder/snowspeeder";
            case STARDESTROYER -> "stardestroyer/stardestroyer256";
            case TIEFIGHTER -> "tiefighter/tie_fighter256";
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
                } else if (object instanceof LeaveMatchDeniedMessage) {
                    pendingUpdates.add(Client.this::onLeaveMatchDenied);
                } else if (object instanceof HandshakeResponse response) {
                    if (response.isAccepted()) {
                        networkClient.sendTCP(new SpawnRequest(selectedShipType));
                    } else {
                        // ConnectScreen already validated these exact credentials moments ago
                        // (design.md 5.1) - a rejection here would mean the account changed
                        // (e.g. password edited elsewhere) in that brief window. No error
                        // screen to fall back to from mid-match-start, so this stays fatal for
                        // now, same as a connection failure below - thrown from the next
                        // render() (see the cross-thread rule above) rather than from here, so
                        // it actually surfaces instead of dying silently on KryoNet's thread.
                        pendingUpdates.add(() -> {
                            throw new IllegalStateException("Handshake rejected: " + response.getMessage());
                        });
                    }
                }
            }
        };
        try {
            networkClient.connect(NetworkConstants.CONNECTION_TIMEOUT_MILLIS, connectionInfo.serverHost(),
                NetworkConstants.TCP_PORT, NetworkConstants.UDP_PORT);
        } catch (IOException e) {
            // No error screen to fall back to from mid-match-start (the Connect Dialog already
            // validated this exact host moments ago) - a real failure here is simply fatal for now.
            throw new IllegalStateException("Failed to connect to " + connectionInfo.serverHost(), e);
        }
        networkClient.sendHandshake(connectionInfo.login(), connectionInfo.password(), connectionInfo.displayName());
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
        myTurretAimAngles = new float[0];

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
            if (leavingMatch) {
                // This destruction is the server granting our own leave request (design.md 2.3),
                // not a combat death - the two share this exact same message (so other clients
                // see an identical explosion either way), told apart here purely by whether we're
                // the one who asked to leave. A real combat death instead just waits here for the
                // server's automatic respawn (no Death Screen yet, design.md 5.1's TODO).
                returnToShipSelection();
            }
        } else {
            ships.remove(destroyed.getPlayerId());
        }
    }

    private void onLeaveMatchDenied() {
        leavingMatch = false;
        warningMessageSecondsRemaining = WARNING_MESSAGE_DURATION_SECONDS;
    }

    /**
     * Leaves this match and returns to Ship Selection (design.md 2.3/5.1) once
     * the server has granted an ESC leave request. Disposes this screen's own
     * resources immediately after switching — same pattern as
     * {@code ShipSelectionScreen.startMatch()} — so {@link #transitionedAway}
     * must be checked by {@link #render(float)} before doing anything else
     * with this screen's now-disposed batch/textures for the rest of this frame.
     */
    private void returnToShipSelection() {
        transitionedAway = true;
        game.setScreen(new ShipSelectionScreen(game, connectionInfo));
        dispose();
    }

    private void onWorldSnapshot(WorldSnapshotMessage snapshot) {
        for (ShipState state : snapshot.getShips()) {
            if (state.getPlayerId() == myPlayerId) {
                reconcileWithServer(state);
                myHullCurrent = state.getHullCurrent();
                myHullMax = state.getHullMax();
                myShieldCurrent = state.getShieldCurrent();
                myShieldMax = state.getShieldMax();
                myTurretAimAngles = state.getTurretAimAngles();
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
            ship.turretAimAngles = state.getTurretAimAngles();
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
        if (transitionedAway) {
            // returnToShipSelection() just disposed this screen's own batch/textures (switching
            // to ShipSelectionScreen) - drawing anything else this frame would use them after
            // disposal and crash, same class of bug ShipSelectionScreen.startMatch() hit first.
            return;
        }

        if (warningMessageSecondsRemaining > 0f) {
            warningMessageSecondsRemaining = Math.max(0f, warningMessageSecondsRemaining - deltaTime);
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

            if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) && !leavingMatch) {
                leavingMatch = true;
                networkClient.sendTCP(new LeaveMatchRequest());
            }

            if (Gdx.input.isKeyJustPressed(Input.Keys.T)) {
                networkClient.sendTCP(new TurretToggleMessage());
            }
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
        drawWarningMessage();
        batch.end();
    }

    private void drawWarningMessage() {
        if (warningMessageSecondsRemaining <= 0f) {
            return;
        }
        float height = WARNING_BANNER_WIDTH * warningBannerTexture.getHeight() / warningBannerTexture.getWidth();
        float x = (Gdx.graphics.getWidth() - WARNING_BANNER_WIDTH) / 2f;
        float y = Gdx.graphics.getHeight() - WARNING_BANNER_TOP_MARGIN - height;
        batch.draw(warningBannerTexture, x, y, WARNING_BANNER_WIDTH, height);
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
        ShipStats myStats = ShipStats.forType(myShipType);
        float enginesMultiplier = myPowerDistribution.multiplierFor(PowerSystem.ENGINES);
        // Snapshot the pre-step position/angle, and reapply input, immediately before *each*
        // individual physics step (see PhysicsSystem#update(float, Runnable)) - not once here
        // before the whole call. A single render() call can trigger more than one fixed step
        // whenever the accumulator carries over slightly (completely normal), and capturing
        // "previous" only once per call left it stale by a whole step-pair whenever that
        // happened: the leftover interpolation alpha right after consuming steps is small, so
        // drawLocalShip/updateCamera would render almost exactly at that stale previous position
        // instead of near the true current one, then snap forward again next frame - a real
        // double-image/ghosting artifact, worse the faster the ship is moving (found via a user
        // report + phone photo; a plain screenshot never caught it, since each one just freezes
        // one already-composited, individually-crisp frame).
        localPhysicsSystem.update(deltaTime, () -> {
            myPreviousX = myBody.getPosition().x;
            myPreviousY = myBody.getPosition().y;
            myPreviousAngle = myBody.getAngle();
            ShipControlSystem.applyInput(myBody, myStats.getThrustForce() * enginesMultiplier,
                myStats.getTurnTorque() * enginesMultiplier,
                thrustForward, thrustReverse, turnLeft, turnRight);
        });
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
            TextureRegion region = shipRegionsByType.get(ship.shipType);
            float screenScale = PhysicsConstants.PIXELS_PER_METER / stats.getPixelsPerMeter();
            float widthPixels = region.getRegionWidth() * screenScale;
            float heightPixels = region.getRegionHeight() * screenScale;

            // The source art faces up/north when unrotated (design.md 4.3), and the server's
            // ShipControlSystem treats angle 0 as "facing north" too - so the ship's angle
            // maps directly onto the region's rotation with no offset needed.
            batch.draw(region,
                ship.renderX - widthPixels / 2f, ship.renderY - heightPixels / 2f,
                widthPixels / 2f, heightPixels / 2f,
                widthPixels, heightPixels,
                1f, 1f,
                ship.renderAngle * MathUtils.radiansToDegrees);
            drawTurrets(ship.shipType, stats, ship.renderX, ship.renderY, ship.renderAngle, ship.turretAimAngles);
        }
        batch.setColor(Color.WHITE);
    }

    /**
     * Draws a ship's turret(s), if its type has any (design.md — turret
     * weapons: currently only the Falcon and Star Destroyer). Each mount's
     * screen position is the ship's own position plus its local attachment
     * offset (from that ship type's sprite metadata, in the same authored
     * order as {@code turretAimAngles}) rotated by the ship's *current*
     * facing — but each mount's own *rotation* is its independently-tracked,
     * absolute world-space {@code turretAimAngles} entry, never combined
     * with the ship's facing, since a turret keeps aiming at its target
     * regardless of which way the hull is pointed. Sized the same way as the
     * hull sprite (design.md — pixels-per-meter fix): the turret art's own
     * native pixel size, rescaled by *this ship type's* pixels-per-meter, so
     * a turret authored at a different resolution than its ship (the 32px/
     * 40px turret sprites vs. each ship's own hull resolution) still ends up
     * a consistent real-world size.
     */
    private void drawTurrets(ShipType type, ShipStats stats, float shipScreenX, float shipScreenY,
                             float shipAngleRadians, float[] turretAimAngles) {
        if (turretAimAngles.length == 0) {
            return;
        }
        TextureRegion turretRegion = turretRegionsByType.get(type);
        if (turretRegion == null) {
            return;
        }
        List<PixelPoint> mountPoints = stats.getSpriteMetadata()
            .map(metadata -> metadata.getAttachmentPoints().get(TurretConfig.ATTACHMENT_NAME))
            .orElse(null);
        if (mountPoints == null || mountPoints.isEmpty()) {
            return;
        }

        float pixelsPerMeter = stats.getPixelsPerMeter();
        float screenScale = PhysicsConstants.PIXELS_PER_METER / pixelsPerMeter;
        float widthPixels = turretRegion.getRegionWidth() * screenScale;
        float heightPixels = turretRegion.getRegionHeight() * screenScale;

        int count = Math.min(mountPoints.size(), turretAimAngles.length);
        for (int i = 0; i < count; i++) {
            PixelPoint point = mountPoints.get(i);
            TURRET_OFFSET.set(point.getX() / pixelsPerMeter * PhysicsConstants.PIXELS_PER_METER,
                point.getY() / pixelsPerMeter * PhysicsConstants.PIXELS_PER_METER).rotateRad(shipAngleRadians);
            float turretScreenX = shipScreenX + TURRET_OFFSET.x;
            float turretScreenY = shipScreenY + TURRET_OFFSET.y;

            batch.draw(turretRegion,
                turretScreenX - widthPixels / 2f, turretScreenY - heightPixels / 2f,
                widthPixels / 2f, heightPixels / 2f,
                widthPixels, heightPixels,
                1f, 1f,
                turretAimAngles[i] * MathUtils.radiansToDegrees);
        }
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
        TextureRegion region = shipRegionsByType.get(myShipType);
        float screenScale = PhysicsConstants.PIXELS_PER_METER / myStats.getPixelsPerMeter();
        float widthPixels = region.getRegionWidth() * screenScale;
        float heightPixels = region.getRegionHeight() * screenScale;

        batch.draw(region,
            x - widthPixels / 2f, y - heightPixels / 2f,
            widthPixels / 2f, heightPixels / 2f,
            widthPixels, heightPixels,
            1f, 1f,
            angle * MathUtils.radiansToDegrees);
        drawTurrets(myShipType, myStats, x, y, angle, myTurretAimAngles);
    }

    private void drawProjectiles() {
        // The projectile art is an elongated oval (nose-up, same authoring convention as ship
        // sprites) rather than a circle, so it visually implies speed/direction - but the actual
        // Box2D hitbox stays a circle regardless (WeaponStats.BLASTER's radius), same as a ship's
        // polygon hitbox not needing to match its sprite's bounding box exactly. Width is tied to
        // that physical diameter; height is derived from the region's own aspect ratio so the art
        // controls how elongated it looks without a second tuning constant to keep in sync.
        float widthPixels = WeaponStats.BLASTER.getProjectileRadiusMeters() * 2f * PhysicsConstants.PIXELS_PER_METER;

        for (RemoteProjectile projectile : projectiles.values()) {
            // Own shots draw red, everyone else's draw blue - purely a rendering choice
            // (design.md 3.5), the server treats every projectile identically.
            TextureRegion region = projectile.ownerPlayerId == myPlayerId ? ownProjectileRegion : enemyProjectileRegion;
            float heightPixels = widthPixels * region.getRegionHeight() / (float) region.getRegionWidth();
            batch.draw(region,
                projectile.renderX - widthPixels / 2f, projectile.renderY - heightPixels / 2f,
                widthPixels / 2f, heightPixels / 2f,
                widthPixels, heightPixels,
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
        warningBannerTexture.dispose();
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
        // Turret aim is entirely server-simulated and never predicted/extrapolated (same
        // reasoning as projectiles) - just held at whatever the latest snapshot reported.
        float[] turretAimAngles = new float[0];

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
