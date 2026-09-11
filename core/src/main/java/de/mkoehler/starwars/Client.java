package de.mkoehler.starwars;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;
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
import de.mkoehler.starwars.input.GameAction;
import de.mkoehler.starwars.input.KeyBindings;
import de.mkoehler.starwars.net.NetworkClient;
import de.mkoehler.starwars.net.NetworkConstants;
import de.mkoehler.starwars.net.messages.AsteroidState;
import de.mkoehler.starwars.net.messages.HandshakeResponse;
import de.mkoehler.starwars.net.messages.LeaveMatchDeniedMessage;
import de.mkoehler.starwars.net.messages.LeaveMatchRequest;
import de.mkoehler.starwars.net.messages.MineDetonatedMessage;
import de.mkoehler.starwars.net.messages.MineState;
import de.mkoehler.starwars.net.messages.MissileFireRequest;
import de.mkoehler.starwars.net.messages.PlayerInputMessage;
import de.mkoehler.starwars.net.messages.PlayerLeftMessage;
import de.mkoehler.starwars.net.messages.PlayerScoreEntry;
import de.mkoehler.starwars.net.messages.PowerAdjustMessage;
import de.mkoehler.starwars.net.messages.PowerUpState;
import de.mkoehler.starwars.net.messages.ProjectileHitMessage;
import de.mkoehler.starwars.net.messages.ProjectileState;
import de.mkoehler.starwars.net.messages.RadarPulseRequest;
import de.mkoehler.starwars.net.messages.ScoreboardMessage;
import de.mkoehler.starwars.net.messages.ShipDestroyedMessage;
import de.mkoehler.starwars.net.messages.ShipSpawnedMessage;
import de.mkoehler.starwars.net.messages.ShipState;
import de.mkoehler.starwars.net.messages.SpawnRequest;
import de.mkoehler.starwars.net.messages.TurretToggleMessage;
import de.mkoehler.starwars.net.messages.WorldSnapshotMessage;
import de.mkoehler.starwars.render.ArenaBoundaryRenderer;
import de.mkoehler.starwars.render.DamageSmokeEffect;
import de.mkoehler.starwars.render.EngineAudioMath;
import de.mkoehler.starwars.render.GameAssets;
import de.mkoehler.starwars.render.ParallaxBackground;
import de.mkoehler.starwars.render.PlaceholderStarfield;
import de.mkoehler.starwars.render.MuzzleFlashEffect;
import de.mkoehler.starwars.render.PowerDistributionHud;
import de.mkoehler.starwars.render.PowerUpEffect;
import de.mkoehler.starwars.render.RadarHud;
import de.mkoehler.starwars.render.OneShotParticleEffect;
import de.mkoehler.starwars.render.ScoreboardHud;
import de.mkoehler.starwars.render.ShipLightEffect;
import de.mkoehler.starwars.render.ShipStatusHud;
import de.mkoehler.starwars.render.ThrusterEffect;
import de.mkoehler.starwars.sim.ArenaBounds;
import de.mkoehler.starwars.sim.AsteroidFactory;
import de.mkoehler.starwars.sim.AsteroidType;
import de.mkoehler.starwars.sim.MineFactory;
import de.mkoehler.starwars.sim.MissileStats;
import de.mkoehler.starwars.sim.PhysicsConstants;
import de.mkoehler.starwars.sim.PowerDistribution;
import de.mkoehler.starwars.sim.PowerSystem;
import de.mkoehler.starwars.sim.PowerUpType;
import de.mkoehler.starwars.sim.ShipFactory;
import de.mkoehler.starwars.sim.ShipStats;
import de.mkoehler.starwars.sim.ShipType;
import de.mkoehler.starwars.sim.TurnResponseCurve;
import de.mkoehler.starwars.sim.WeaponStats;
import de.mkoehler.starwars.sim.components.ProjectileComponent;
import de.mkoehler.starwars.sim.components.WeaponComponent;
import de.mkoehler.starwars.sim.metadata.PixelPoint;
import de.mkoehler.starwars.sim.metadata.TurretConfig;
import de.mkoehler.starwars.sim.systems.PhysicsSystem;
import de.mkoehler.starwars.sim.systems.ShipControlSystem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * {@code GameNetworkServer}). Every projectile is drawn from
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
 * <b>The local player's own shots are additionally client-predicted</b>
 * (design.md 2.4's addendum, added once server-only shots proved to lag a
 * fast-moving shooter's own visibly-ahead-of-server-truth predicted ship
 * position, proportional to round-trip latency): {@link #predictLocalWeapon}
 * mirrors {@code WeaponSystem}'s cooldown/capacitor/attachment-point firing
 * logic locally, spawning a cosmetic {@link RemoteProjectile} into
 * {@link #predictedProjectiles} immediately rather than waiting for server
 * confirmation. {@link #onWorldSnapshot} hands that same object off to become
 * the real, id-tracked entry in {@link #projectiles} once a matching
 * {@code ProjectileState} arrives ({@link #takeMatchingPredicted}), so it
 * keeps rendering continuously rather than popping between two different
 * objects. Other players' shots are never predicted this way — nobody else
 * has a precise enough reference to notice the same gap on someone else's
 * shot, and predicting it would need guessing another player's own input.
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

    private static final String TAG = "Client";

    /**
     * {@link #render(float)} logs a warning if called with a
     * {@code deltaTime} beyond this - temporary diagnostic instrumentation
     * added while investigating an intermittent screen-transition pause
     * (CLAUDE.md): a frame this slow means the render thread was
     * blocked/stalled since the previous frame, which (via
     * {@code PhysicsSystem}'s existing {@code MAX_STEPS_PER_FRAME} clamp on
     * {@link #localPhysicsSystem}) can take many subsequent frames to fully
     * catch up from, showing up as erratic local-prediction movement for a
     * while afterward even once the actual stall is over.
     */
    private static final float RENDER_STALL_WARN_SECONDS = 0.5f;

    /** How quickly the camera eases toward the local ship each frame; not the full model from design.md 4.1. */
    private static final float CAMERA_FOLLOW_SPEED = 3f;

    /**
     * Speed (design.md 4.1's "speed-linked zoom") at which the camera
     * reaches its full {@link #SPEED_ZOOM_MAX_INCREASE} zoom-out — a single
     * shared reference across every ship type, not authored per-type,
     * matching the user's own "ballpark, doesn't need to be exact" framing
     * for this feature. Anchored to the X-wing's own empirically-observed
     * terminal velocity (~93.6 m/s, measured live during the terminal-
     * velocity-jitter investigation), rounded down. Untuned starting point.
     */
    private static final float SPEED_ZOOM_REFERENCE_METERS_PER_SECOND = 90f;
    /** How far out the camera zooms at/above {@link #SPEED_ZOOM_REFERENCE_METERS_PER_SECOND} — the user's own spec. */
    private static final float SPEED_ZOOM_MAX_INCREASE = 0.25f;
    /**
     * How quickly the camera's zoom eases toward its speed-driven target —
     * independent of, and deliberately slower than, {@link #CAMERA_FOLLOW_SPEED}'s
     * position tracking, so a brief thrust burst doesn't visibly "pulse"
     * the zoom in and out. Untuned.
     */
    private static final float CAMERA_ZOOM_FOLLOW_SPEED = 1.5f;

    /** Reconciliation error, in meters, beyond which the local prediction hard-snaps to the server's state instead of blending. */
    private static final float RECONCILE_SNAP_THRESHOLD_METERS = 3f;
    /** Fraction of a small reconciliation error corrected per snapshot, rather than all at once. */
    private static final float RECONCILE_SOFT_BLEND = 0.2f;

    private static final Color OTHER_SHIP_TINT = new Color(0.6f, 0.85f, 1f, 1f);

    /** Display-name label color (design.md — display names) - a plain, readable light gray/white, untuned. */
    private static final Color DISPLAY_NAME_COLOR = new Color(0.9f, 0.92f, 0.95f, 1f);
    /** Gap between a ship's own top edge and its display-name label above it - untuned placeholder. */
    private static final float DISPLAY_NAME_VERTICAL_MARGIN_PIXELS = 12f;
    /** libGDX's built-in default font is fairly small (~15px cap height) - scaled up a bit for legibility at typical zoom, untuned. */
    private static final float DISPLAY_NAME_FONT_SCALE = 1.15f;

    /** Scratch vector for {@link #drawTurrets} - avoids an allocation per turret per frame. */
    private static final Vector2 TURRET_OFFSET = new Vector2();
    /** Scratch vector for {@link #updateAndDrawThrusters}/{@link #updateAndDrawLights}'s attachment-point-position math - avoids an allocation per thruster/light per frame. */
    private static final Vector2 ATTACHMENT_OFFSET = new Vector2();
    /** Scratch vector for {@link #predictLocalWeapon}'s spawn-offset math - avoids an allocation per shot. */
    private static final Vector2 PREDICTED_SPAWN_OFFSET = new Vector2();
    /** Scratch vector for {@link #spawnPredictedProjectile}'s velocity math - avoids an allocation per shot. */
    private static final Vector2 PREDICTED_VELOCITY = new Vector2();
    /**
     * How close (in meters) an incoming, server-confirmed {@code ProjectileState}
     * must be to an outstanding predicted shot's current extrapolated
     * position to be treated as the same shot ({@link #takeMatchingPredicted}),
     * rather than spawning a brand-new, unpredicted {@link RemoteProjectile}.
     * Generously larger than the actual expected gap (local prediction and
     * the server's own simulation should agree to well under a meter for a
     * shot fired moments ago) so a real match isn't missed over ordinary
     * prediction/authority drift, while still ruling out matching against a
     * clearly-unrelated shot.
     */
    private static final float PREDICTED_MATCH_DISTANCE_METERS = 3f;
    /**
     * How long an outstanding predicted shot ({@link #predictedProjectiles})
     * is kept waiting for server confirmation before it's given up on and
     * removed ({@link #extrapolateProjectiles}) — deliberately much shorter
     * than {@link WeaponStats#getProjectileLifetimeSeconds()}, see that
     * method's Javadoc for why. A generous few multiples of a snapshot
     * interval ({@code NetworkConstants.SIMULATION_TICK_RATE_HZ}, ~33ms),
     * not tied to that constant directly since this is a UI-feel choice
     * (how long a wrong local guess is allowed to visibly linger), not a
     * protocol-correctness one.
     */
    private static final float PREDICTED_PROJECTILE_MAX_UNMATCHED_SECONDS = 0.5f;

    /**
     * How long {@link #mineAnimation} holds each of its 64 baked rotation
     * frames (design.md — mines) — untuned, flagged for the user's own
     * feel-testing like every other timing constant in this project; at
     * this rate a mine completes one full visual rotation roughly every
     * 64 * this = ~2.13s.
     */
    private static final float MINE_ANIMATION_FRAME_DURATION_SECONDS = 1f / 30f;

    /** Size, in screen pixels, of the ship status HUD widget - placeholder until tuned by feel. */
    private static final float HUD_STATUS_SIZE = 220f;
    /** Screen-pixel margin from the bottom-left corner for the ship status HUD widget. */
    private static final float HUD_STATUS_MARGIN = 24f;
    /** Size, in screen pixels, of the power-distribution HUD widget - placeholder until tuned by feel. */
    private static final float HUD_POWER_SIZE = 220f;
    /** Horizontal gap, in screen pixels, between the ship-status and power-distribution HUD widgets. */
    private static final float HUD_POWER_GAP = 16f;
    /**
     * Size, in screen pixels, of the radar/minimap HUD widget (design.md
     * 2.14) - top-right corner, not part of the bottom-left status/power
     * row, so it's sized independently; doubled from its original 220 after
     * the first play-test found it too small to read at a glance.
     */
    private static final float HUD_RADAR_SIZE = 440f;
    /** Screen-pixel margin from the top-right corner for the radar HUD widget. */
    private static final float HUD_RADAR_MARGIN = 24f;
    /** How long a power-distribution keybind must be held before it maximizes its system instead of just incrementing it - untuned placeholder. */
    private static final float HOLD_TO_MAXIMIZE_SECONDS = 0.4f;

    /**
     * The missile lock reticle's base on-screen size, as a multiple of the
     * locked ship's own diameter (design.md — missiles) - untuned placeholder,
     * picked to comfortably ring the ship rather than exactly hug it.
     */
    private static final float MISSILE_LOCK_RETICLE_SCALE = 1.6f;
    /** The outer reticle ring's sine-wave scale pulse: 100%-110% (design.md — missiles). */
    private static final float MISSILE_LOCK_RETICLE_PULSE_MIN_SCALE = 1.0f;
    private static final float MISSILE_LOCK_RETICLE_PULSE_AMPLITUDE = 0.05f;
    /** How fast the pulse cycles - untuned placeholder (one full cycle every ~2 seconds). */
    private static final float MISSILE_LOCK_RETICLE_PULSE_RADIANS_PER_SECOND = MathUtils.PI2 / 2f;
    /** The inner reticle ring's constant rotation rate (design.md — missiles: "90 degrees per second", clockwise). */
    private static final float MISSILE_LOCK_RETICLE_INNER_ROTATION_DEGREES_PER_SECOND = 90f;

    /** How long the combat-lock warning banner stays on screen - untuned placeholder. */
    private static final float WARNING_MESSAGE_DURATION_SECONDS = 2.5f;
    /** On-screen width of the combat-lock warning banner - height follows from the source art's aspect ratio. */
    private static final float WARNING_BANNER_WIDTH = 720f;
    /** Gap from the top of the screen to the banner's top edge - kept near the top, deliberately away from the player's own ship (which stays near screen-center via camera-follow) since this fires during tense moments. */
    private static final float WARNING_BANNER_TOP_MARGIN = 48f;

    /** How long the engine-loop sound's fade-in/fade-out takes (design.md — engine sound), user-specified. */
    private static final float ENGINE_SOUND_FADE_SECONDS = 0.25f;
    /**
     * Beyond this distance, another player's engine loop or one-shot combat
     * sound (weapon fire, turret fire, missile launch) is fully inaudible
     * (design.md — engine sound / weapon sound), user-specified — the same
     * distance/falloff calculation is deliberately shared by both.
     */
    private static final float REMOTE_SOUND_MAX_AUDIBLE_RANGE_METERS = 40f;

    private static final PlayerScoreEntry[] NO_SCORES = new PlayerScoreEntry[0];
    /**
     * Scoreboard row order (design.md 2.11 doesn't specify one): most kills
     * first, ties broken by XP, then by name, so the order stays stable
     * without depending on server-side iteration order.
     */
    private static final Comparator<PlayerScoreEntry> SCOREBOARD_ORDER =
        Comparator.comparingInt(PlayerScoreEntry::getKills).reversed()
            .thenComparing(Comparator.comparingInt(PlayerScoreEntry::getXp).reversed())
            .thenComparing(PlayerScoreEntry::getDisplayName);

    private final StarWarsGame game;
    private final ShipType selectedShipType;
    private final ConnectionInfo connectionInfo;

    /**
     * The player's shared, remappable keybinds (design.md 3.8) — read via
     * {@link KeyBindings#isPressed}/{@link KeyBindings#isJustPressed} for
     * every gameplay action below, in place of a hardcoded
     * {@code Gdx.input.isKeyPressed(Input.Keys.*)}. <b>ESC (leave match)
     * stays hardcoded</b>, deliberately never routed through here — see
     * {@link GameAction}'s Javadoc for why.
     */
    private KeyBindings keyBindings;

    private SpriteBatch batch;
    private TextureAtlas shipsAtlas;
    private final Map<ShipType, TextureRegion> shipRegionsByType = new EnumMap<>(ShipType.class);
    private final Map<ShipType, TextureRegion> turretRegionsByType = new EnumMap<>(ShipType.class);
    private TextureAtlas projectilesAtlas;
    private TextureRegion ownProjectileRegion;
    private TextureRegion enemyProjectileRegion;
    private TextureRegion missileRegion;
    private TextureRegion missileLockReticleOuterRegion;
    private TextureRegion missileLockReticleInnerRegion;
    private TextureRegion missileLockReticleCenterRegion;
    private TextureAtlas asteroidsAtlas;
    private final Map<AsteroidType, TextureRegion> asteroidRegionsByType = new EnumMap<>(AsteroidType.class);
    private TextureAtlas powerupsAtlas;
    private final Map<PowerUpType, TextureRegion> powerUpRegionsByType = new EnumMap<>(PowerUpType.class);
    private TextureAtlas minesAtlas;
    /**
     * A mine's rotating animation (design.md — mines: a baked 64-frame
     * sequence, {@code assets-raw/mine/mine_0000.png}..{@code mine_0063.png}).
     * One shared instance/state-time for every currently-active mine — the
     * user asked for a rotating mine, not independently-phased mines, so
     * there's no reason to track a per-mine animation clock.
     */
    private Animation<TextureRegion> mineAnimation;
    private float mineAnimationStateTime;
    private ParallaxBackground background;
    private ArenaBoundaryRenderer arenaBoundaryRenderer;
    private ShipStatusHud statusHud;
    private PowerDistributionHud powerHud;
    private RadarHud radarHud;
    private ScoreboardHud scoreboardHud;
    private Texture warningBannerTexture;
    private OrthographicCamera camera;
    private Viewport viewport;
    private OrthographicCamera hudCamera;

    private NetworkClient networkClient;
    private final Queue<Runnable> pendingUpdates = new ConcurrentLinkedQueue<>();
    private final Map<Integer, RemoteShip> ships = new HashMap<>();
    private final Map<Integer, RemoteProjectile> projectiles = new HashMap<>();
    private final Map<Integer, RemoteAsteroid> asteroids = new HashMap<>();
    private final Map<Integer, RemotePowerUp> powerUps = new HashMap<>();
    private final Map<Integer, RemoteMine> mines = new HashMap<>();
    /**
     * Client-side "puppet" Box2D bodies mirroring each currently-active
     * asteroid into {@link #localWorld} (design.md — asteroids' addendum) —
     * without these, the locally-predicted {@link #myBody} would fly
     * straight through an asteroid the server is actually bouncing it off,
     * same reasoning {@link ArenaBounds#createBoundary} already documents
     * for the arena wall. Kept separate from {@link #asteroids} (the
     * render-only, dead-reckoned state) since this map only exists once
     * {@link #localWorld} does (a snapshot can in principle arrive before
     * the first {@code ShipSpawnedMessage} creates it).
     */
    private final Map<Integer, Body> localAsteroidBodies = new HashMap<>();
    /**
     * Locally-predicted shots (design.md 2.4's addendum) that this client has
     * already spawned and started rendering, but that the server hasn't
     * confirmed with a real {@code projectileId} yet - drawn and extrapolated
     * the same as any {@link RemoteProjectile}, just not yet keyed into
     * {@link #projectiles} since there's no id to key it by until
     * {@link #onWorldSnapshot} matches it ({@link #takeMatchingPredicted})
     * against an incoming {@code ProjectileState}. An entry that's never
     * matched (e.g. local capacitor prediction drifted from the server's own)
     * simply expires on its own simulated lifetime instead of lingering
     * forever - see {@link #extrapolateProjectiles}.
     */
    private final List<RemoteProjectile> predictedProjectiles = new ArrayList<>();
    private int myPlayerId = -1;
    /**
     * The local player's own engine thruster glow(s) (design.md — engine
     * particle effects), one per {@code "ENGINE"} attachment point on the
     * current {@link #myShipType}, paired with that point's local-frame
     * pixel offset - rebuilt from scratch on every {@link #onShipSpawned}
     * (spawn or respawn) since a different ship type may have a different
     * engine effect/attachment layout, or none at all. Empty (not null) for
     * a ship type with no engine effect configured yet. Every other visible
     * ship gets its own equivalent list too ({@code RemoteShip.thrusters}) -
     * {@code ShipState} broadcasts whether each ship is currently thrusting
     * (design.md's addendum), so this isn't local-player-only anymore.
     */
    private final List<EngineThruster> myThrusters = new ArrayList<>();
    /**
     * The local player's own continuous engine-loop sound (design.md —
     * engine sound), shared with whichever other ship(s) of the same type
     * exist (an {@link com.badlogic.gdx.assets.AssetManager}-owned
     * {@link Sound} — one loaded file, many independent {@code loop()}
     * instances). Unlike {@link #myThrusters}, this is <em>not</em> rebuilt
     * on every {@link #onShipSpawned} - restarting {@code loop()} on every
     * spawn/respawn would glitch the loop audibly even when the ship type
     * hasn't actually changed, so {@link #ensureLocalEngineSound} only
     * swaps it out when {@link #myEngineSoundShipType} actually differs
     * from {@link #myShipType}. {@code null} if this ship type has no
     * engine sound loaded (see {@link GameAssets#engineSoundPath}).
     */
    private Sound myEngineSound;
    /** The specific {@code loop()} instance id of {@link #myEngineSound} - see {@link Sound#setVolume(long, float)}/{@link Sound#stop(long)}. */
    private long myEngineSoundId = -1;
    /** Which ship type {@link #myEngineSound} was loaded for - see {@link #ensureLocalEngineSound}. */
    private ShipType myEngineSoundShipType;
    /** This frame's engine-loop volume, eased toward 0/1 by {@link #updateLocalEngineSound} - see {@link EngineAudioMath#approachFraction}. */
    private float myEngineVolumeFraction;
    /**
     * The local player's own positioning lights (design.md — positioning
     * lights), one per {@code "LIGHT_RED"}/{@code "LIGHT_GREEN"} attachment
     * point on the current {@link #myShipType} - rebuilt alongside
     * {@link #myThrusters} on every {@link #onShipSpawned}. Unlike engine
     * thrusters, lights are never toggled by input - they simply emit for as
     * long as the ship exists, so no per-ship "is it on" wire state is
     * needed for {@code RemoteShip.lights} to work the same way.
     */
    private final List<ShipLight> myLights = new ArrayList<>();
    /**
     * The local player's own damage smoke plume(s) (design.md — damage
     * smoke), one per {@code "DAMAGE_SMOKE"} attachment point on the
     * current {@link #myShipType} - rebuilt alongside {@link #myThrusters}/
     * {@link #myLights} on every {@link #onShipSpawned}, each activating
     * once this ship's hull damage crosses that point's own threshold
     * ({@link #buildDamageSmokePoints}). No per-ship "is it on" wire state
     * is needed for {@code RemoteShip.damageSmoke} either - hull current/max
     * are already broadcast for every ship (design.md 2.5/2.14), so every
     * client can compute the same threshold crossing for anyone's ship.
     */
    private final List<DamageSmokePoint> myDamageSmoke = new ArrayList<>();
    /**
     * The local player's own radar pulse "energy wave" (design.md 2.14's
     * rendering addendum) - one shared instance, built once in {@link #show()}
     * (not per-spawn, unlike {@link #myThrusters}/{@link #myLights}/
     * {@link #myDamageSmoke} - this effect doesn't depend on ship-type
     * attachment metadata at all, just the ship's own center). Triggered by
     * {@link #onWorldSnapshot} detecting a rising edge in
     * {@link #myRadarPulseCooldownRemaining} (the only way this value can
     * increase frame-to-frame is the pulse actually just firing server-side
     * - it otherwise only ever ticks down).
     */
    private OneShotParticleEffect myRadarPulseEffect;
    /**
     * The local player's own muzzle flash(es) (design.md — muzzle flash),
     * one per {@code "PROJECTILE"} attachment point on the current
     * {@link #myShipType} - rebuilt alongside {@link #myThrusters}/
     * {@link #myLights}/{@link #myDamageSmoke} on every
     * {@link #onShipSpawned}. Triggered directly from
     * {@link #predictLocalWeapon} at the exact moment of firing - the same
     * "predict locally, don't wait for server confirmation" reasoning
     * already used for the shot itself (design.md 2.4's addendum), so the
     * flash isn't visibly delayed by round-trip latency.
     */
    private final List<MuzzleFlashPoint> myMuzzleFlashes = new ArrayList<>();
    /**
     * A small, self-growing pool of muzzle flash instances for other
     * players' shots (design.md — muzzle flash's addendum) - not attached
     * to any particular {@link RemoteShip}, since a flash is triggered
     * directly off a brand-new {@code ProjectileState} (already broadcast
     * to everyone unfiltered, design.md 2.14) rather than needing to know
     * which ship or attachment point fired it; a pool (rather than one
     * instance per shot) is needed since more than one enemy shot can be
     * mid-flash at once. Entries are reused once {@link MuzzleFlashEffect#isPlaying()}
     * goes false rather than growing unboundedly.
     */
    private final List<MuzzleFlashEffect> remoteMuzzleFlashPool = new ArrayList<>();
    /**
     * A small, self-growing pool of small impact explosions (design.md —
     * explosions), one per {@link ProjectileHitMessage} received - same
     * "not attached to any particular ship, pooled since more than one can
     * be mid-explosion at once" reasoning as {@link #remoteMuzzleFlashPool},
     * just triggered by an explicit server message (a real contact event)
     * rather than inferred from a new projectile appearing. Each entry
     * pairs its {@link OneShotParticleEffect} with the fixed world
     * position it was last triggered at - unlike {@link #myRadarPulseEffect}/
     * {@link RemoteShip#radarPulseEffect}, an explosion has nothing moving
     * to re-center on every frame, so its position is set once at trigger
     * time and simply repeated on every later {@link #updateAndDrawExplosions} call.
     */
    private final List<PositionedOneShotEffect> hitExplosionPool = new ArrayList<>();
    /**
     * A small, self-growing pool of full ship-destruction explosions
     * (design.md — explosions), one per {@link ShipDestroyedMessage} for
     * another player's ship - see {@link #onShipDestroyed} for why the
     * local player's own destruction doesn't get one (the screen
     * transitions away in the same instant, so it would never actually
     * render). Same fixed-position-per-entry shape as {@link #hitExplosionPool}.
     */
    private final List<PositionedOneShotEffect> shipExplosionPool = new ArrayList<>();
    /**
     * A small, self-growing pool of mine-detonation explosions (design.md —
     * mines), one per {@link MineDetonatedMessage} — reuses the same big
     * explosion particle effect/sound as {@link #shipExplosionPool}, per
     * the user's own explicit ask, but in its own pool since a mine
     * detonation is a distinct event from a ship's own destruction. Same
     * fixed-position-per-entry shape as {@link #hitExplosionPool}/
     * {@link #shipExplosionPool}.
     */
    private final List<PositionedOneShotEffect> mineExplosionPool = new ArrayList<>();

    private World localWorld;
    private PhysicsSystem localPhysicsSystem;
    private Body myBody;
    private ShipType myShipType;
    private float myPreviousX;
    private float myPreviousY;
    private float myPreviousAngle;
    /** This frame's interpolated on-screen position of the local player's own ship, cached by {@link #drawLocalShip()} for {@link #drawMissileLockReticle()}. */
    private float myRenderScreenX;
    private float myRenderScreenY;
    /**
     * Wall-clock seconds elapsed since the local player's own last
     * {@link WorldSnapshotMessage} entry was reconciled — accumulated every
     * frame in {@link #render(float)}, reset to zero inside
     * {@link #reconcileWithServer}. See that method's Javadoc for why this
     * exists: a snapshot is already stale by however long it took to
     * arrive/queue, and without compensating for that, reconciliation was
     * treating pure snapshot staleness as prediction error.
     */
    private float mySnapshotElapsedSeconds;
    private float myHullCurrent;
    private float myHullMax;
    private float myShieldCurrent;
    private float myShieldMax;
    private float[] myTurretAimAngles = new float[0];
    /**
     * How much longer until this ship's radar pulse (design.md 2.14, "R")
     * can be triggered again - drives {@link RadarHud}'s pulse-cooldown
     * indicator LED (2026-09-09 addendum): green once this reaches
     * {@code <= 0}, red otherwise.
     */
    private float myRadarPulseCooldownRemaining;
    /**
     * This ship's current missile lock target (design.md — missiles), read
     * from the local player's own {@code ShipState} each snapshot — drives
     * the lock-reticle HUD ({@link #drawMissileLockReticle}). Sentinel
     * {@link ShipState#NO_MISSILE_LOCK_TARGET} for no current lock.
     */
    private int myMissileLockTargetPlayerId = ShipState.NO_MISSILE_LOCK_TARGET;
    /** Whether {@link #myMissileLockTargetPlayerId}'s lock is fully acquired (vs. still acquiring). */
    private boolean myMissileLockAcquired;
    /**
     * Which of the two looping missile-lock sounds (design.md — missiles'
     * audio addendum), if any, is currently playing for the local player's
     * own lock attempt — kept as explicit state (rather than re-derived
     * every frame) so {@link #updateMissileLockAudioState} only needs to
     * act on an actual transition, not repeat an already-correct start/stop
     * every time it's called.
     */
    private enum MissileLockAudioState { NONE, TRYING, ACQUIRED }
    private MissileLockAudioState myMissileLockAudioState = MissileLockAudioState.NONE;
    /** The currently-playing {@link #myMissileLockAudioState} loop's instance id, or {@code -1} if {@link #myMissileLockAudioState} is {@code NONE}. */
    private long myMissileLockSoundId = -1;
    /**
     * Whether any enemy currently has *this* ship as their own missile lock
     * target (design.md — missiles' addendum) — the victim's side of the
     * same reticle, read from the local player's own {@code ShipState} each
     * snapshot, same as {@link #myMissileLockTargetPlayerId} above but
     * facing the other way. Without this, only the attacker ever saw the
     * lock building on their target; the target had no idea.
     */
    private boolean myTargetedByMissileLock;
    /** Whether any lock on this ship (see {@link #myTargetedByMissileLock}) is fully acquired. */
    private boolean myTargetedByMissileLockAcquired;
    /**
     * Free-running clock driving the lock reticle's animation (design.md —
     * missiles: the outer ring's sine-wave scale pulse, the inner ring's
     * constant-rate rotation) - incremented unconditionally every
     * {@link #render(float)} call, not reset per-target, since both
     * animations are simple periodic functions with no meaningful "start
     * phase" to reset.
     */
    private float missileReticleAnimationSeconds;
    /** Latest scoreboard from the server (design.md 2.11) - only drawn while TAB is held. */
    private PlayerScoreEntry[] scoreboardEntries = NO_SCORES;
    /**
     * Whether every other visible player's display name is currently drawn
     * above their ship (design.md — display names) - toggled by
     * {@link de.mkoehler.starwars.input.GameAction#SHOW_DISPLAY_NAMES}
     * ("N" by default). Defaults on - an untuned default, not confirmed
     * with the user either way, easy to flip if it turns out
     * distracting-by-default is the wrong call.
     */
    private boolean showDisplayNames = true;
    /**
     * Draws every other visible ship's name label (design.md — display
     * names) - deliberately libGDX's own built-in default font, not the
     * game's "SF Distant Galaxy" ({@link de.mkoehler.starwars.render.GameFonts}),
     * per explicit user request: a plainer, more legible face for a small
     * floating label, as opposed to that font's much more stylized look
     * (already used everywhere else - logos, dialog chrome, HUD readouts).
     * Needs no bundled asset at all ({@code new BitmapFont()} loads a
     * baked-in bitmap font shipped inside libGDX itself), so unlike every
     * other font this project uses, there's no licensing question and
     * nothing to add to {@code assets/}.
     */
    private BitmapFont displayNameFont;
    private final GlyphLayout displayNameLayout = new GlyphLayout();
    private PowerDistribution myPowerDistribution = PowerDistribution.even();
    /**
     * Local mirror of this ship's server-broadcast {@code PowerBoostComponent}
     * multiplier (design.md — power-ups' BOOST effect) — {@code 1f} normally,
     * {@code 2f} while a boost is active, read directly from every snapshot's
     * own {@code ShipState} entry (see {@link #onWorldSnapshot}) rather than
     * independently timed, unlike {@link #myPowerDistribution}'s keypress-
     * driven mirror. Multiplies the engines/weapons multipliers in
     * {@link #predictLocalShip}/{@link #predictLocalWeapon} so local
     * prediction stays in sync with the server's authoritative rate.
     */
    private float myPowerBoostMultiplier = 1f;
    /**
     * A local mirror of the server's own {@code WeaponComponent} for this
     * ship (design.md 2.4's addendum) - reset alongside every spawn/respawn
     * ({@link #onShipSpawned}, same as {@link #myPowerDistribution}), ticked
     * every frame in {@link #predictLocalWeapon} using the identical
     * cooldown/capacitor math {@code WeaponSystem} runs server-side, purely
     * to decide when the local player's own held fire input should spawn a
     * cosmetic predicted shot ahead of server confirmation. Never sent over
     * the network and never itself authoritative - the server always has the
     * real say over whether a shot actually fires and deals damage; this
     * only decides what to draw a little early.
     */
    private WeaponComponent myWeapon;
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
    public Client(StarWarsGame game, ShipType selectedShipType, ConnectionInfo connectionInfo) {
        this.game = game;
        this.selectedShipType = selectedShipType;
        this.connectionInfo = connectionInfo;
    }

    @Override
    public void show() {
        // Temporary diagnostic timing while investigating an intermittent screen-transition
        // pause (CLAUDE.md) - logged once show() finishes, below.
        long showStartMillis = System.currentTimeMillis();

        Box2D.init();
        long box2dInitMillis = System.currentTimeMillis();

        keyBindings = game.getKeyBindings();

        batch = new SpriteBatch();
        shipsAtlas = game.getAssets().get(GameAssets.SHIPS_ATLAS, TextureAtlas.class);
        for (ShipType type : ShipType.values()) {
            shipRegionsByType.put(type, shipsAtlas.findRegion(hullRegionName(type), 20));
        }
        // Only the two ship types that actually have turrets (design.md — turret weapons) get an
        // entry here; every other ship type simply has none, which drawTurrets treats as "nothing
        // to draw" rather than an error.
        turretRegionsByType.put(ShipType.FALCON, shipsAtlas.findRegion("turrets/turret40"));
        turretRegionsByType.put(ShipType.STARDESTROYER, shipsAtlas.findRegion("turrets/turret32"));
        projectilesAtlas = game.getAssets().get(GameAssets.PROJECTILES_ATLAS, TextureAtlas.class);
        ownProjectileRegion = projectilesAtlas.findRegion("red_oval");
        enemyProjectileRegion = projectilesAtlas.findRegion("blue_oval");
        missileRegion = projectilesAtlas.findRegion("missile");
        missileLockReticleOuterRegion = projectilesAtlas.findRegion("Missile_Lock_Reticle_Outer");
        missileLockReticleInnerRegion = projectilesAtlas.findRegion("Missile_Lock_Reticle_Inner");
        missileLockReticleCenterRegion = projectilesAtlas.findRegion("Missile_Lock_Reticle_Center");
        asteroidsAtlas = game.getAssets().get(GameAssets.ASTEROIDS_ATLAS, TextureAtlas.class);
        for (AsteroidType type : AsteroidType.values()) {
            asteroidRegionsByType.put(type, asteroidsAtlas.findRegion(type.getResourceName()));
        }
        powerupsAtlas = game.getAssets().get(GameAssets.POWERUPS_ATLAS, TextureAtlas.class);
        for (PowerUpType type : PowerUpType.values()) {
            powerUpRegionsByType.put(type, powerupsAtlas.findRegion(type.getResourceName()));
        }
        minesAtlas = game.getAssets().get(GameAssets.MINES_ATLAS, TextureAtlas.class);
        // findRegions (plural), not findRegion - the 64 "mine_0000.png".."mine_0063.png" source
        // files pack into one indexed region set (TexturePacker's own numeric-suffix detection,
        // same mechanism a ship's own bank-angle frames use), retrievable as one ordered Array.
        // Untuned frame duration (design.md — mines): 64 frames at this rate is a ~2.13s full
        // rotation.
        mineAnimation = new Animation<>(MINE_ANIMATION_FRAME_DURATION_SECONDS,
            minesAtlas.findRegions("mine"), Animation.PlayMode.LOOP);
        // One shared effect regardless of ship type, unlike myThrusters/myLights/myDamageSmoke -
        // built once here rather than rebuilt per-spawn, since it depends on no ship-type-specific
        // attachment metadata.
        myRadarPulseEffect = createOneShotEffect(GameAssets.RADAR_PULSE_PARTICLE);

        // libGDX's own built-in default font (design.md — display names) - no asset to load,
        // deliberately plainer than GameFonts' "SF Distant Galaxy", per explicit user request.
        displayNameFont = new BitmapFont();
        displayNameFont.getData().setScale(DISPLAY_NAME_FONT_SCALE);

        background = new ParallaxBackground(
            // false: this texture is owned by StarWarsGame#getAssets() (design.md - asset
            // loading), not this layer - unlike the procedurally-generated starfield below, which
            // is regenerated (and must be disposed) fresh every time this screen is shown.
            new ParallaxBackground.Layer(game.getAssets().get(GameAssets.BLUE_NEBULA, Texture.class), 0.1f, false),
            new ParallaxBackground.Layer(PlaceholderStarfield.generate(512, 120, 1L), 0.4f)
        );
        arenaBoundaryRenderer = new ArenaBoundaryRenderer(game.getAssets());
        statusHud = new ShipStatusHud(game.getAssets());
        powerHud = new PowerDistributionHud(game.getAssets());
        radarHud = new RadarHud(game.getAssets());
        scoreboardHud = new ScoreboardHud(game.getAssets());
        warningBannerTexture = game.getAssets().get(GameAssets.WARNING_BANNER, Texture.class);

        camera = new OrthographicCamera();
        viewport = new ScreenViewport(camera);
        viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);

        // A separate, un-zoomed, un-panned camera for the HUD layer - screen pixel coordinates
        // with (0,0) at the bottom-left, unrelated to the world camera's position/zoom.
        hudCamera = new OrthographicCamera();
        hudCamera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        connectToServer();

        Gdx.app.log(TAG, "show() took " + (System.currentTimeMillis() - showStartMillis)
            + "ms total (Box2D.init() " + (box2dInitMillis - showStartMillis) + "ms)");
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
            case AWING -> "awing/awing";
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
                    pendingUpdates.add(() -> {
                        RemoteShip ship = ships.remove(left.getPlayerId());
                        if (ship != null) {
                            ship.stopEngineSound();
                        }
                    });
                } else if (object instanceof ShipDestroyedMessage destroyed) {
                    pendingUpdates.add(() -> onShipDestroyed(destroyed));
                } else if (object instanceof ProjectileHitMessage hit) {
                    pendingUpdates.add(() -> onProjectileHit(hit));
                } else if (object instanceof MineDetonatedMessage detonated) {
                    pendingUpdates.add(() -> onMineDetonated(detonated));
                } else if (object instanceof LeaveMatchDeniedMessage) {
                    pendingUpdates.add(Client.this::onLeaveMatchDenied);
                } else if (object instanceof ScoreboardMessage scoreboard) {
                    pendingUpdates.add(() -> scoreboardEntries = scoreboard.getEntries());
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
            // Same boundary GameNetworkServer builds into its own authoritative World - a bounce
            // predicted differently here than the server actually resolves would otherwise fight
            // reconciliation every time the local ship touches a wall (design.md - arena bounds).
            ArenaBounds.createBoundary(localWorld);
        } else if (myBody != null) {
            // Respawning after death (see onShipDestroyed) - the old body was already destroyed.
            localWorld.destroyBody(myBody);
        }
        myBody = ShipFactory.createBody(localWorld, spawned.getSpawnX(), spawned.getSpawnY(), myStats);
        myPreviousX = myBody.getPosition().x;
        myPreviousY = myBody.getPosition().y;
        myPreviousAngle = myBody.getAngle();
        // A fresh spawn/respawn starts at rest - reset the speed-linked zoom (design.md 4.1)
        // back to its resting value immediately rather than easing down from whatever it was at
        // the moment of death/leaving.
        camera.zoom = 1f;
        // A fresh body means no meaningful "elapsed since last reconciled snapshot" yet either -
        // avoids extrapolating the very first post-spawn snapshot using a stale accumulated value.
        mySnapshotElapsedSeconds = 0f;

        // Full hull/shield until the first WorldSnapshotMessage arrives - otherwise the HUD
        // would flash empty for a frame or two right after spawning/respawning.
        myHullMax = myHullCurrent = myStats.getMaxHealth();
        myShieldMax = myShieldCurrent = myStats.getShieldMaxCapacity();
        myTurretAimAngles = new float[0];
        // A fresh ship also gets a fresh MissileLockComponent server-side, if its type has one -
        // mirror that here too, so the reticle doesn't briefly show a stale lock from before a
        // death/respawn.
        myMissileLockTargetPlayerId = ShipState.NO_MISSILE_LOCK_TARGET;
        myMissileLockAcquired = false;
        stopMissileLockSound();
        myTargetedByMissileLock = false;
        myTargetedByMissileLockAcquired = false;

        // A fresh ship (spawn or respawn) always gets a fresh PowerDistributionComponent on the
        // server too (ShipFactory.createShip), so resetting the local mirror here keeps the two in
        // sync trivially, same reasoning as the hull/shield reset above.
        myPowerDistribution = PowerDistribution.even();
        // Same reasoning - a fresh ship also gets a fresh (inactive) PowerBoostComponent
        // server-side (design.md - power-ups), so a stale multiplier from a previous life
        // doesn't leak across a respawn until the first snapshot corrects it.
        myPowerBoostMultiplier = 1f;
        shieldsHold.reset();
        weaponsHold.reset();
        enginesHold.reset();

        // A fresh ship also gets a fresh WeaponComponent server-side (ShipFactory.createShip,
        // full capacitor) - mirror that here too, same reasoning as the power-distribution reset
        // above. Any shots predicted under the old body (already destroyed above on a respawn)
        // are meaningless now - drop them rather than let them keep extrapolating from a stale
        // position with no server projectile left to ever confirm them.
        myWeapon = new WeaponComponent(WeaponStats.BLASTER);
        predictedProjectiles.clear();

        myThrusters.clear();
        myThrusters.addAll(buildEngineThrusters(myStats));
        myLights.clear();
        myLights.addAll(buildShipLights(myStats));
        myDamageSmoke.clear();
        myDamageSmoke.addAll(buildDamageSmokePoints(myStats));
        myMuzzleFlashes.clear();
        myMuzzleFlashes.addAll(buildMuzzleFlashPoints(myStats));

        ensureLocalEngineSound(myShipType);
    }

    private void onShipDestroyed(ShipDestroyedMessage destroyed) {
        if (destroyed.getPlayerId() == myPlayerId) {
            // Deliberately no explosion *particle* triggered here (design.md — explosions): this
            // same method disposes this whole screen a few lines below (returnToShipSelection/
            // goToDeathScreen), so this Client instance never renders another frame afterward -
            // triggering one would be dead code, never actually seen. The explosion *sound*
            // doesn't have that problem - a Sound.play() call is owned by AssetManager, not this
            // screen, so it keeps playing across the transition to Death Screen/Ship Selection
            // regardless. Played at myRenderScreenX/Y (trivially ~0m from itself, so always full
            // volume) rather than skipped, matching the ordinary expectation of hearing your own
            // ship's own destruction.
            playPositionalSound(game.getAssets().get(GameAssets.EXPLOSION_SOUND, Sound.class),
                myRenderScreenX, myRenderScreenY, game.getAudioSettings().getEffectiveSoundEffectsVolume());
            if (myBody != null) {
                localWorld.destroyBody(myBody);
                myBody = null;
            }
            if (leavingMatch) {
                // This destruction is the server granting our own leave request (design.md 2.3),
                // not a combat death - the two share this exact same message (so other clients
                // see an identical explosion either way), told apart here purely by whether we're
                // the one who asked to leave.
                returnToShipSelection();
            } else {
                // A real combat death (design.md 5.1) - leaves the match immediately rather than
                // waiting here for the server's automatic mid-match respawn (see DeathScreen's
                // class Javadoc for why that timer is no longer exercised by this client).
                goToDeathScreen();
            }
        } else {
            RemoteShip ship = ships.remove(destroyed.getPlayerId());
            if (ship != null) {
                ship.stopEngineSound();
                triggerPooledExplosion(shipExplosionPool, GameAssets.EXPLOSION_PARTICLE, ship.renderX, ship.renderY);
                playPositionalSound(game.getAssets().get(GameAssets.EXPLOSION_SOUND, Sound.class),
                    ship.renderX, ship.renderY, game.getAudioSettings().getEffectiveSoundEffectsVolume());
            }
        }
    }

    /**
     * Handles a {@link ProjectileHitMessage} (design.md — explosions) by
     * triggering a small impact explosion at the reported contact point —
     * a real hit, unlike a projectile simply expiring after its lifetime
     * (which broadcasts nothing at all, see that message's Javadoc).
     *
     * @param hit the hit message
     */
    private void onProjectileHit(ProjectileHitMessage hit) {
        triggerPooledExplosion(hitExplosionPool, GameAssets.EXPLOSION_SMALL_PARTICLE,
            hit.getX() * PhysicsConstants.PIXELS_PER_METER, hit.getY() * PhysicsConstants.PIXELS_PER_METER);
    }

    /**
     * Handles a {@link MineDetonatedMessage} (design.md — mines) by
     * triggering the same big explosion particle effect + sound
     * {@link #onShipDestroyed} plays for another player's own destruction —
     * the user's own explicit ask, to reuse existing VFX rather than author
     * anything new for a mine's own explosion. The detonated mine itself
     * needs no explicit removal here — same as a picked-up power-up or a
     * destroyed asteroid, it simply stops appearing in the next snapshot,
     * and {@link #onWorldSnapshot}'s own presence-based prune (see
     * {@link #mines}) takes it out of the map from there.
     *
     * @param detonated the mine-detonated message
     */
    private void onMineDetonated(MineDetonatedMessage detonated) {
        float x = detonated.getX() * PhysicsConstants.PIXELS_PER_METER;
        float y = detonated.getY() * PhysicsConstants.PIXELS_PER_METER;
        triggerPooledExplosion(mineExplosionPool, GameAssets.EXPLOSION_PARTICLE, x, y);
        playPositionalSound(game.getAssets().get(GameAssets.EXPLOSION_SOUND, Sound.class), x, y,
            game.getAudioSettings().getEffectiveSoundEffectsVolume());
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

    /**
     * Leaves this match and shows {@link DeathScreen} (design.md 5.1) after
     * a real combat death - same disposal pattern/reasoning as
     * {@link #returnToShipSelection()}. Hands over a snapshot of the local
     * player's own {@link #findMyScoreEntry()} so that screen can show at
     * least the player's own stats (design.md 5.1's addendum) despite
     * having no live server connection of its own.
     */
    private void goToDeathScreen() {
        transitionedAway = true;
        game.setScreen(new DeathScreen(game, connectionInfo, findMyScoreEntry()));
        dispose();
    }

    /**
     * Returns the local player's own row from the latest
     * {@link #scoreboardEntries} - the server broadcasts a fresh one
     * immediately on every death (see {@code GameNetworkServer.handleShipDestroyed}),
     * ordered ahead of the {@link ShipDestroyedMessage} that triggers
     * {@link #goToDeathScreen()}, over the same reliable/ordered TCP
     * channel, so by the time this runs the entry already reflects this
     * very death. Falls back to a zeroed entry (using
     * {@link ConnectionInfo#displayName()}) in the unexpected case no
     * scoreboard broadcast has arrived yet at all.
     *
     * @return the local player's own current score entry
     */
    private PlayerScoreEntry findMyScoreEntry() {
        for (PlayerScoreEntry entry : scoreboardEntries) {
            if (entry.getPlayerId() == myPlayerId) {
                return entry;
            }
        }
        return new PlayerScoreEntry(myPlayerId, connectionInfo.displayName(), 0, 0, 0);
    }

    private void onWorldSnapshot(WorldSnapshotMessage snapshot) {
        // Radar (design.md 2.14): a ship no longer appears here at all once this player's radar
        // stops detecting it - the server never sends more than the local player's own ship plus
        // whichever enemies it currently detects. presentShipIds drives the same
        // present-in-this-snapshot-or-remove pruning already used for projectiles just below, so
        // a ship that drops out of radar range actually disappears from this client's world
        // instead of freezing in its last known position forever.
        Set<Integer> presentShipIds = new HashSet<>();
        for (ShipState state : snapshot.getShips()) {
            if (state.getPlayerId() == myPlayerId) {
                reconcileWithServer(state);
                myHullCurrent = state.getHullCurrent();
                myHullMax = state.getHullMax();
                myShieldCurrent = state.getShieldCurrent();
                myShieldMax = state.getShieldMax();
                myTurretAimAngles = state.getTurretAimAngles();
                // A cooldown can only ever tick down on its own - the only way it goes UP
                // frame-to-frame is the pulse actually firing again server-side just now, so this
                // rising edge is the trigger for the one-shot wave effect (design.md 2.14's
                // rendering addendum) - no separate "pulse fired" message needed.
                float newRadarPulseCooldown = state.getRadarPulseCooldownRemaining();
                if (newRadarPulseCooldown > myRadarPulseCooldownRemaining) {
                    myRadarPulseEffect.trigger();
                }
                myRadarPulseCooldownRemaining = newRadarPulseCooldown;
                myMissileLockTargetPlayerId = state.getMissileLockTargetPlayerId();
                myMissileLockAcquired = state.isMissileLockAcquired();
                updateMissileLockAudioState();
                myTargetedByMissileLock = state.isTargetedByMissileLock();
                myTargetedByMissileLockAcquired = state.isTargetedByMissileLockAcquired();
                // Design.md - power-ups' BOOST effect: read directly from the snapshot (not
                // independently timed client-side, unlike myPowerDistribution's keypress-driven
                // mirror) purely so local thrust/torque/weapon-capacitor prediction stays in sync
                // with the server's authoritative multiplier for the whole boost duration.
                myPowerBoostMultiplier = state.getPowerGenerationMultiplier();
                continue;
            }
            presentShipIds.add(state.getPlayerId());
            float x = state.getX() * PhysicsConstants.PIXELS_PER_METER;
            float y = state.getY() * PhysicsConstants.PIXELS_PER_METER;
            RemoteShip ship = ships.computeIfAbsent(state.getPlayerId(), id -> {
                ShipStats remoteStats = ShipStats.forType(state.getShipType());
                // One long-lived loop() instance for this ship's own engine sound (design.md —
                // engine sound), started muted (updateRemoteEngineSounds fades it in/out every
                // frame) and stopped only when this ship is later removed from `ships`.
                Sound engineSound = null;
                long engineSoundId = -1;
                String enginePath = GameAssets.engineSoundPath(state.getShipType());
                if (game.getAssets().isLoaded(enginePath, Sound.class)) {
                    engineSound = game.getAssets().get(enginePath, Sound.class);
                    engineSoundId = engineSound.loop(0f);
                }
                // Seeded with this ship's current cooldown (not 0) so a ship first seen mid-cooldown,
                // or the instant it pulses, doesn't spuriously fire the wave effect the moment it's
                // first detected - see the edge-detection comment below for why that would otherwise
                // look identical to a real trigger.
                return new RemoteShip(x, y, state.getAngle(), state.getShipType(),
                    buildEngineThrusters(remoteStats), buildShipLights(remoteStats), buildDamageSmokePoints(remoteStats),
                    createOneShotEffect(GameAssets.RADAR_PULSE_PARTICLE), state.getRadarPulseCooldownRemaining(),
                    engineSound, engineSoundId);
            });
            ship.updateFromSnapshot(x, y, state.getAngle(),
                state.getVelocityX() * PhysicsConstants.PIXELS_PER_METER,
                state.getVelocityY() * PhysicsConstants.PIXELS_PER_METER,
                state.getAngularVelocity());
            ship.turretAimAngles = state.getTurretAimAngles();
            ship.thrusting = state.isThrusting();
            ship.hullCurrent = state.getHullCurrent();
            ship.hullMax = state.getHullMax();
            // Same rising-edge trigger as the local player's own pulse effect above.
            float newShipRadarPulseCooldown = state.getRadarPulseCooldownRemaining();
            if (newShipRadarPulseCooldown > ship.radarPulseCooldownRemaining) {
                ship.radarPulseEffect.trigger();
            }
            ship.radarPulseCooldownRemaining = newShipRadarPulseCooldown;
        }
        Iterator<Map.Entry<Integer, RemoteShip>> shipIterator = ships.entrySet().iterator();
        while (shipIterator.hasNext()) {
            Map.Entry<Integer, RemoteShip> entry = shipIterator.next();
            if (!presentShipIds.contains(entry.getKey())) {
                entry.getValue().stopEngineSound();
                shipIterator.remove();
            }
        }

        // Projectiles have no destroyed-notification of their own (design.md 3.5's
        // ProjectileState note) - presence in this snapshot means alive, so anything not
        // present anymore gets pruned below.
        Set<Integer> presentIds = new HashSet<>();
        // Deduped per owner, per snapshot (design.md — weapon sound): a ship with more than one
        // PROJECTILE attachment point, or more than one turret mount, fires multiple projectiles
        // in the same tick/snapshot - only one weapon/turret sound should be heard for that whole
        // volley, not one per projectile.
        Set<Integer> weaponSoundOwnersThisSnapshot = new HashSet<>();
        Set<Integer> turretSoundOwnersThisSnapshot = new HashSet<>();
        for (ProjectileState state : snapshot.getProjectiles()) {
            presentIds.add(state.getProjectileId());
            float x = state.getX() * PhysicsConstants.PIXELS_PER_METER;
            float y = state.getY() * PhysicsConstants.PIXELS_PER_METER;
            RemoteProjectile projectile = projectiles.get(state.getProjectileId());
            if (projectile == null) {
                // First time this real projectile id has appeared - if it's the local player's
                // own and a locally-predicted shot is still outstanding for it (design.md 2.4's
                // addendum), adopt that predicted object instead of starting a fresh one, so its
                // already-rendering, already-extrapolating position/velocity carries over rather
                // than popping to a brand-new object at the same spot.
                float adoptedElapsedSeconds = 0f;
                // Missiles are never locally predicted (design.md — missiles: firing only sends a
                // MissileFireRequest and waits for confirmation, same as a turret) - only attempt
                // to adopt a predicted object for an ordinary blaster bolt, or a missile could in
                // theory match against a stray unmatched predicted shot at a similar spawn point.
                if (state.getOwnerPlayerId() == myPlayerId
                    && state.getTrackedTargetPlayerId() == ProjectileComponent.NO_TRACKED_TARGET) {
                    projectile = takeMatchingPredicted(x, y);
                    if (projectile != null) {
                        // Seed with this object's own already-accumulated flight time instead of
                        // resetting to zero below - see the 5-arg updateFromSnapshot's Javadoc for
                        // why: this state's x/y is this same shot's true spawn point, already
                        // however-long-ago by the time it's received, and the predicted object's
                        // own elapsed time is exactly that "how long ago," no network-latency
                        // estimate needed.
                        adoptedElapsedSeconds = projectile.elapsedSinceUpdate;
                    }
                }
                if (projectile == null) {
                    projectile = new RemoteProjectile(state.getOwnerPlayerId(), x, y, state.getTrackedTargetPlayerId());
                    // Muzzle flash (design.md — muzzle flash's addendum): reaching here for an
                    // ordinary (non-missile) bolt means it's genuinely new and unadopted - i.e. not
                    // the local player's own shot (those are always matched/adopted above, already
                    // flashed immediately when fired, see predictLocalWeapon) - so this is another
                    // player's shot. Flashed directly at its own spawn position/travel direction,
                    // no shooter-ship lookup needed for *position* at all, since every projectile is
                    // already broadcast to everyone unfiltered (design.md 2.14) - only looked up here
                    // for its current velocity, so the flash can be carried along with it the same
                    // way a real shot inherits it (design.md — explosions' addendum on this exact
                    // bug). Zero if the shooter isn't currently a detected/rendered RemoteShip -
                    // exactly as good as it gets without that ship's own data to draw on.
                    if (state.getTrackedTargetPlayerId() == ProjectileComponent.NO_TRACKED_TARGET) {
                        float travelAngleDegrees = MathUtils.atan2(-state.getVelocityX(), state.getVelocityY())
                            * MathUtils.radiansToDegrees;
                        RemoteShip shooter = ships.get(state.getOwnerPlayerId());
                        float shooterVelocityX = shooter != null ? shooter.velocityX : 0f;
                        float shooterVelocityY = shooter != null ? shooter.velocityY : 0f;
                        obtainPooledMuzzleFlash().trigger(x, y, travelAngleDegrees, shooterVelocityX, shooterVelocityY);
                        // Weapon sound (design.md — weapon sound): same "genuinely new and
                        // unadopted" reasoning as the muzzle flash above covers every case that
                        // reaches here - another player's main-gun shot, or any turret shot
                        // (turret fire is never locally predicted, even the local player's own).
                        if (state.isTurretShot()) {
                            if (turretSoundOwnersThisSnapshot.add(state.getOwnerPlayerId())) {
                                playPositionalSound(game.getAssets().get(GameAssets.TURRET_SOUND, Sound.class), x, y,
                                    game.getAudioSettings().getEffectiveWeaponsVolume());
                            }
                        } else if (weaponSoundOwnersThisSnapshot.add(state.getOwnerPlayerId())) {
                            playWeaponSound(state.getOwnerPlayerId(), x, y);
                        }
                    } else {
                        // A missile launch (design.md — missiles' audio addendum) - never locally
                        // predicted either, so this is the one place any missile's launch, including
                        // the local player's own, is ever observed as "new."
                        playPositionalSound(game.getAssets().get(GameAssets.MISSILE_LAUNCH_SOUND, Sound.class), x, y,
                            game.getAudioSettings().getEffectiveWeaponsVolume());
                    }
                }
                projectiles.put(state.getProjectileId(), projectile);
                projectile.updateFromSnapshot(x, y,
                    state.getVelocityX() * PhysicsConstants.PIXELS_PER_METER,
                    state.getVelocityY() * PhysicsConstants.PIXELS_PER_METER,
                    adoptedElapsedSeconds);
                continue;
            }
            projectile.updateFromSnapshot(x, y,
                state.getVelocityX() * PhysicsConstants.PIXELS_PER_METER,
                state.getVelocityY() * PhysicsConstants.PIXELS_PER_METER);
        }
        projectiles.keySet().removeIf(id -> !presentIds.contains(id));

        // Same presence-based prune as ships/projectiles above - an asteroid has no
        // despawned notification of its own (design.md — asteroids), a client infers it's gone
        // simply by its id no longer appearing in a subsequent snapshot.
        Set<Integer> presentAsteroidIds = new HashSet<>();
        for (AsteroidState state : snapshot.getAsteroids()) {
            presentAsteroidIds.add(state.getAsteroidId());
            float x = state.getX() * PhysicsConstants.PIXELS_PER_METER;
            float y = state.getY() * PhysicsConstants.PIXELS_PER_METER;
            RemoteAsteroid asteroid = asteroids.computeIfAbsent(state.getAsteroidId(),
                id -> new RemoteAsteroid(x, y, state.getAngle(), state.getType()));
            asteroid.updateFromSnapshot(x, y, state.getAngle(),
                state.getVelocityX() * PhysicsConstants.PIXELS_PER_METER,
                state.getVelocityY() * PhysicsConstants.PIXELS_PER_METER,
                state.getAngularVelocity());

            // Local-prediction physics mirror (see #localAsteroidBodies' own Javadoc) - meters,
            // not the pixel-scaled x/y above; only once localWorld actually exists (it's created
            // on the first ShipSpawnedMessage, which a snapshot could in principle race ahead of).
            if (localWorld != null) {
                Body localBody = localAsteroidBodies.computeIfAbsent(state.getAsteroidId(),
                    id -> AsteroidFactory.createLocalMirrorBody(localWorld, state.getType(),
                        state.getX(), state.getY(), state.getAngle()));
                localBody.setTransform(state.getX(), state.getY(), state.getAngle());
                localBody.setLinearVelocity(state.getVelocityX(), state.getVelocityY());
                localBody.setAngularVelocity(state.getAngularVelocity());
            }
        }
        asteroids.keySet().removeIf(id -> !presentAsteroidIds.contains(id));
        if (localWorld != null) {
            Iterator<Map.Entry<Integer, Body>> localAsteroidIterator = localAsteroidBodies.entrySet().iterator();
            while (localAsteroidIterator.hasNext()) {
                Map.Entry<Integer, Body> entry = localAsteroidIterator.next();
                if (!presentAsteroidIds.contains(entry.getKey())) {
                    localWorld.destroyBody(entry.getValue());
                    localAsteroidIterator.remove();
                }
            }
        }

        // Same presence-based prune as above - a power-up has no destroyed/despawned
        // notification of its own either (design.md - power-ups), a client infers it's gone
        // (picked up, or drifted out of the arena and respawned elsewhere) the same way. No local-
        // prediction physics mirror needed here, unlike asteroids - a ship never physically
        // collides with a power-up (design.md - power-ups' sensor-fixture pickup design), so
        // there's nothing for the local prediction body to diverge from by not tracking one.
        Set<Integer> presentPowerUpIds = new HashSet<>();
        for (PowerUpState state : snapshot.getPowerUps()) {
            presentPowerUpIds.add(state.getPowerUpId());
            float x = state.getX() * PhysicsConstants.PIXELS_PER_METER;
            float y = state.getY() * PhysicsConstants.PIXELS_PER_METER;
            RemotePowerUp powerUp = powerUps.computeIfAbsent(state.getPowerUpId(),
                id -> new RemotePowerUp(x, y, state.getAngle(), state.getType(), createPowerUpEffect()));
            powerUp.updateFromSnapshot(x, y, state.getAngle(),
                state.getVelocityX() * PhysicsConstants.PIXELS_PER_METER,
                state.getVelocityY() * PhysicsConstants.PIXELS_PER_METER,
                state.getAngularVelocity());
        }
        powerUps.keySet().removeIf(id -> !presentPowerUpIds.contains(id));

        // Same presence-based prune as above - a mine has no destroyed/despawned notification of
        // its own in a WorldSnapshotMessage either (design.md - mines, MineDetonatedMessage is
        // purely a VFX trigger, see onMineDetonated), a client infers it's gone the same way.
        // Simpler than RemoteAsteroid/RemotePowerUp - a mine never moves, so there's no velocity/
        // angle to track and nothing to extrapolate between snapshots.
        Set<Integer> presentMineIds = new HashSet<>();
        for (MineState state : snapshot.getMines()) {
            presentMineIds.add(state.getMineId());
            float x = state.getX() * PhysicsConstants.PIXELS_PER_METER;
            float y = state.getY() * PhysicsConstants.PIXELS_PER_METER;
            mines.computeIfAbsent(state.getMineId(), id -> new RemoteMine(x, y));
        }
        mines.keySet().removeIf(id -> !presentMineIds.contains(id));
    }

    private PowerUpEffect createPowerUpEffect() {
        ParticleEffect template = game.getAssets().get(GameAssets.POWERUP_PARTICLE, ParticleEffect.class);
        return new PowerUpEffect(template);
    }

    /**
     * Finds and removes whichever {@link #predictedProjectiles} entry is
     * closest to {@code (xPixels, yPixels)} — the true spawn position of a
     * just-confirmed real projectile — within {@link #PREDICTED_MATCH_DISTANCE_METERS},
     * so {@link #onWorldSnapshot} can hand that predicted object off to
     * become the real, id-tracked {@link RemoteProjectile} instead of
     * creating a new one. Nearest-position matching rather than plain FIFO
     * order, since a single volley can fire more than one shot at once (one
     * per {@code PROJECTILE} attachment point) with no other way to tell
     * which predicted entry corresponds to which real one.
     * <p>
     * Compares against each candidate's own immutable {@link RemoteProjectile#spawnX}/
     * {@link RemoteProjectile#spawnY}, not its current, already-extrapolated
     * render position — the render position drifts further from the true
     * spawn point the longer the shot has been flying (round-trip latency ×
     * velocity), which would make {@link #PREDICTED_MATCH_DISTANCE_METERS}
     * meaningless as a fixed threshold. Two independent spawn-position
     * estimates of the *same* fire event — one from local prediction, one
     * from the server — should only ever differ by ordinary prediction/
     * authority drift, not by anything latency-dependent.
     *
     * @param xPixels the confirmed projectile's true spawn X, in screen pixels
     * @param yPixels the confirmed projectile's true spawn Y, in screen pixels
     * @return the matched predicted projectile, already removed from
     * {@link #predictedProjectiles}; {@code null} if none was close enough
     */
    private RemoteProjectile takeMatchingPredicted(float xPixels, float yPixels) {
        float maxDistancePixels = PREDICTED_MATCH_DISTANCE_METERS * PhysicsConstants.PIXELS_PER_METER;
        RemoteProjectile best = null;
        float bestDistanceSq = maxDistancePixels * maxDistancePixels;
        for (RemoteProjectile candidate : predictedProjectiles) {
            float dx = candidate.spawnX - xPixels;
            float dy = candidate.spawnY - yPixels;
            float distanceSq = dx * dx + dy * dy;
            if (distanceSq <= bestDistanceSq) {
                bestDistanceSq = distanceSq;
                best = candidate;
            }
        }
        if (best != null) {
            predictedProjectiles.remove(best);
        }
        return best;
    }

    /**
     * Corrects the local prediction body against the server's authoritative
     * state for it: a small blend for a small error (smooths out normal
     * prediction/authority drift without a visible pop), or a hard snap
     * (including velocity) for a large one, so a bad desync — e.g. from a
     * burst of dropped packets — can't leave the local prediction
     * permanently wrong.
     * <p>
     * <b>Extrapolates {@code state} forward before comparing it against
     * {@link #myBody}, by {@link #mySnapshotElapsedSeconds} — the wall-clock
     * time since the previous snapshot was reconciled.</b> Fixes a user-
     * reported high-speed jitter while holding a constant, single-input
     * thrust (design.md 3.5's addendum). {@code state} describes the ship's
     * position as of whenever the server captured it — already stale by the
     * time it's applied here, by at least one tick interval (design.md 3.5,
     * {@code SIMULATION_TICK_RATE_HZ}) plus transit/queueing time. Comparing
     * that raw, already-old position directly against {@link #myBody}'s
     * live, up-to-the-current-frame prediction manufactures a phantom
     * "error" out of pure staleness, not actual divergence — and since that
     * phantom error is {@code velocity × staleness}, it scales directly
     * with speed. Extrapolating {@code state} forward by the elapsed time
     * using its own reported velocity — the same dead-reckoning
     * {@link RemoteShip} already uses for every other player's ship, just
     * applied to this one's reconciliation target too — cancels that
     * phantom error out, leaving only genuine prediction drift for the
     * blend/snap logic below to actually correct.
     * <p>
     * <b>Known limitation:</b> this compensates for a snapshot's average
     * staleness, not variance in it — snapshot-delivery timing noise can
     * still produce a real (much smaller) residual error, and since that
     * error also scales with speed, a large enough delivery-timing outlier
     * at high speed can still cross {@link #RECONCILE_SNAP_THRESHOLD_METERS}
     * and trigger a visible snap (design.md 3.5's addendum has the specifics
     * and two identified, unfixed contributors).
     *
     * @param state the local player's ship state from the latest snapshot
     */
    private void reconcileWithServer(ShipState state) {
        if (myBody == null) {
            return;
        }
        float extrapolatedX = state.getX() + state.getVelocityX() * mySnapshotElapsedSeconds;
        float extrapolatedY = state.getY() + state.getVelocityY() * mySnapshotElapsedSeconds;
        float extrapolatedAngle = state.getAngle() + state.getAngularVelocity() * mySnapshotElapsedSeconds;
        mySnapshotElapsedSeconds = 0f;

        float dx = extrapolatedX - myBody.getPosition().x;
        float dy = extrapolatedY - myBody.getPosition().y;
        float errorMeters = (float) Math.sqrt(dx * dx + dy * dy);

        if (errorMeters > RECONCILE_SNAP_THRESHOLD_METERS) {
            myBody.setTransform(extrapolatedX, extrapolatedY, extrapolatedAngle);
            myBody.setLinearVelocity(state.getVelocityX(), state.getVelocityY());
            myBody.setAngularVelocity(state.getAngularVelocity());
        } else {
            float blendedX = MathUtils.lerp(myBody.getPosition().x, extrapolatedX, RECONCILE_SOFT_BLEND);
            float blendedY = MathUtils.lerp(myBody.getPosition().y, extrapolatedY, RECONCILE_SOFT_BLEND);
            float blendedAngle = MathUtils.lerpAngle(myBody.getAngle(), extrapolatedAngle, RECONCILE_SOFT_BLEND);
            myBody.setTransform(blendedX, blendedY, blendedAngle);
        }
    }

    @Override
    public void render(float deltaTime) {
        if (deltaTime > RENDER_STALL_WARN_SECONDS) {
            Gdx.app.log(TAG, "render() called with deltaTime=" + deltaTime
                + "s - the render thread was likely blocked/stalled since the previous frame");
        }

        ScreenUtils.clear(0.05f, 0.05f, 0.08f, 1f);

        // Accumulated before draining pendingUpdates below, so a snapshot processed this very
        // frame (reconcileWithServer) sees an elapsed value that includes this frame's own
        // deltaTime - see mySnapshotElapsedSeconds' and reconcileWithServer's Javadoc.
        mySnapshotElapsedSeconds += deltaTime;

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
            boolean thrustForward = keyBindings.isPressed(GameAction.THRUST_FORWARD);
            boolean turnLeft = keyBindings.isPressed(GameAction.TURN_LEFT);
            boolean turnRight = keyBindings.isPressed(GameAction.TURN_RIGHT);
            boolean firing = keyBindings.isPressed(GameAction.FIRE_WEAPON);

            networkClient.sendUDP(new PlayerInputMessage(thrustForward, turnLeft, turnRight, firing));
            predictLocalShip(thrustForward, turnLeft, turnRight, deltaTime);
            predictLocalWeapon(firing, deltaTime);
            handlePowerDistributionInput(deltaTime);

            if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) && !leavingMatch) {
                leavingMatch = true;
                networkClient.sendTCP(new LeaveMatchRequest());
            }

            if (keyBindings.isJustPressed(GameAction.TOGGLE_TURRET)) {
                networkClient.sendTCP(new TurretToggleMessage());
            }

            // Radar pulse (design.md 2.14) - no local cooldown gating needed, same as every
            // other server-validated action here: the server just drops it harmlessly if the
            // pulse is disabled for this ship type or still on cooldown.
            if (keyBindings.isJustPressed(GameAction.RADAR_PULSE)) {
                networkClient.sendTCP(new RadarPulseRequest());
            }

            // Missile fire (design.md — missiles) - same "no local gating, let the server
            // just drop an invalid request harmlessly" treatment as the radar pulse above: the
            // client never predicts a lock or a missile shot, only sends the request and waits for
            // confirmation via the next ShipState/ProjectileState (same as a turret).
            if (keyBindings.isJustPressed(GameAction.FIRE_MISSILE)) {
                networkClient.sendTCP(new MissileFireRequest());
            }

            if (keyBindings.isJustPressed(GameAction.SHOW_DISPLAY_NAMES)) {
                showDisplayNames = !showDisplayNames;
            }
        }

        extrapolateRemoteShips(deltaTime);
        extrapolateProjectiles(deltaTime);
        extrapolateAsteroids(deltaTime);
        extrapolatePowerUps(deltaTime);
        updateCamera(deltaTime);
        missileReticleAnimationSeconds += deltaTime;
        mineAnimationStateTime += deltaTime;

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        background.render(batch, camera);
        arenaBoundaryRenderer.render(batch);
        drawAsteroids();
        drawPowerUps(deltaTime);
        drawMines();
        // Missiles draw *before* ships, deliberately (design.md - missiles' addendum): they now
        // spawn at the firing ship's exact center, so drawing every ship on top of them is what
        // makes a fresh missile read as launched from underneath the ship and emerging as it
        // flies clear, rather than popping into view already ahead of the hull. Ordinary blaster
        // shots are unaffected - still drawn after ships, same as before.
        drawMissiles();
        drawRemoteShips(deltaTime);
        drawLocalShip(deltaTime);
        updateRemoteEngineSounds(deltaTime);
        drawBlasterProjectiles();
        updateAndDrawRemoteMuzzleFlashes(deltaTime);
        updateAndDrawExplosions(hitExplosionPool, deltaTime);
        updateAndDrawExplosions(shipExplosionPool, deltaTime);
        updateAndDrawExplosions(mineExplosionPool, deltaTime);
        drawMissileLockReticle();
        batch.end();

        // Separate begin/end pair with the HUD's own screen-space camera - SpriteBatch doesn't
        // reliably re-flush already-queued sprites if the projection matrix were swapped
        // mid-batch instead.
        batch.setProjectionMatrix(hudCamera.combined);
        batch.begin();
        drawHud();
        drawWarningMessage();
        if (keyBindings.isPressed(GameAction.SHOW_SCOREBOARD)) {
            drawScoreboard();
        }
        batch.end();
    }

    /**
     * Draws the scoreboard overlay (design.md 2.11) centered on screen, at
     * its panel's native pixel size, while TAB is held - rows sorted by
     * {@link #SCOREBOARD_ORDER}, applied to a copy so the underlying array
     * (replaced wholesale by the next {@link ScoreboardMessage}) is never
     * mutated in place.
     */
    private void drawScoreboard() {
        if (scoreboardEntries.length == 0) {
            return;
        }
        PlayerScoreEntry[] sorted = Arrays.copyOf(scoreboardEntries, scoreboardEntries.length);
        Arrays.sort(sorted, SCOREBOARD_ORDER);

        float x = (Gdx.graphics.getWidth() - scoreboardHud.getPanelWidth()) / 2f;
        float y = (Gdx.graphics.getHeight() - scoreboardHud.getPanelHeight()) / 2f;
        scoreboardHud.render(batch, x, y, sorted);
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

        // Every ship currently in `ships` is already exactly what this player's radar detects
        // (design.md 2.14 - the server only ever sends detected contacts), so no client-side
        // filtering is needed here, just converting each one's render position back to meters.
        List<Vector2> contactPositionsMeters = new ArrayList<>(ships.size());
        for (RemoteShip ship : ships.values()) {
            contactPositionsMeters.add(new Vector2(
                ship.renderX / PhysicsConstants.PIXELS_PER_METER, ship.renderY / PhysicsConstants.PIXELS_PER_METER));
        }
        // Asteroids (design.md — asteroids' radar addendum): broadcast unfiltered to everyone
        // (unlike ship contacts), so - same as `asteroids` itself - no server-side radar
        // filtering to mirror here either, just a units conversion.
        List<Vector2> asteroidPositionsMeters = new ArrayList<>(asteroids.size());
        for (RemoteAsteroid asteroid : asteroids.values()) {
            asteroidPositionsMeters.add(new Vector2(
                asteroid.renderX / PhysicsConstants.PIXELS_PER_METER, asteroid.renderY / PhysicsConstants.PIXELS_PER_METER));
        }
        // Top-right corner, not part of the bottom-left status/power row (design.md 2.14) - the
        // first play-test found it too small/cramped down there to actually read at a glance.
        float radarX = Gdx.graphics.getWidth() - HUD_RADAR_SIZE - HUD_RADAR_MARGIN;
        float radarY = Gdx.graphics.getHeight() - HUD_RADAR_SIZE - HUD_RADAR_MARGIN;
        radarHud.render(batch, ShipStats.forType(myShipType), radarX, radarY, HUD_RADAR_SIZE,
            myBody.getPosition().x, myBody.getPosition().y, myBody.getAngle(), contactPositionsMeters,
            asteroidPositionsMeters, myRadarPulseCooldownRemaining);
    }

    /**
     * Reads the power-distribution keybinds (design.md 5.3: J/I/L by
     * default, remappable via {@link KeybindScreen} — design.md 3.8 — to
     * shift toward Shields/Weapons/Engines, matching the HUD's left-to-right
     * Shields/Weapons/Engines bar order; K by default to reset). A tap shifts the
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
        handlePowerKey(GameAction.POWER_SHIELDS, PowerSystem.SHIELDS, shieldsHold, deltaTime);
        handlePowerKey(GameAction.POWER_WEAPONS, PowerSystem.WEAPONS, weaponsHold, deltaTime);
        handlePowerKey(GameAction.POWER_ENGINES, PowerSystem.ENGINES, enginesHold, deltaTime);

        if (keyBindings.isJustPressed(GameAction.POWER_RESET)) {
            myPowerDistribution = myPowerDistribution.reset();
            networkClient.sendTCP(new PowerAdjustMessage(PowerAdjustMessage.Kind.RESET, null));
        }
    }

    private void handlePowerKey(GameAction action, PowerSystem target, PowerKeyHold hold, float deltaTime) {
        if (keyBindings.isJustPressed(action)) {
            adjustPower(target);
        }

        if (keyBindings.isPressed(action)) {
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

    private void predictLocalShip(boolean thrustForward, boolean turnLeft, boolean turnRight, float deltaTime) {
        ShipStats myStats = ShipStats.forType(myShipType);
        // Design.md - power-ups' BOOST effect: multiplies on top of the distribution's own
        // multiplier, same as ShipControlSystem's server-side math - must match exactly, or this
        // prediction would constantly need correcting for reasons other than differing input.
        float enginesMultiplier = myPowerDistribution.multiplierFor(PowerSystem.ENGINES) * myPowerBoostMultiplier;
        // Thrust stays on the plain linear multiplier; only torque goes through the per-ship-type
        // response curve (design.md 2.2's addendum) - must match ShipControlSystem's own server-side
        // math exactly, or local prediction would constantly need correcting for reasons other than
        // differing input.
        float turnMultiplier = TurnResponseCurve.apply(enginesMultiplier, myStats.getEngineTurnResponseExponent());
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
                myStats.getTurnTorque() * turnMultiplier,
                thrustForward, turnLeft, turnRight);
        });
    }

    /**
     * Predicts the local player's own shots (design.md 2.4's addendum),
     * mirroring {@code WeaponSystem#processEntity}'s exact
     * cooldown/capacitor/attachment-point logic so this local decision
     * matches the server's own as closely as possible: only a genuine
     * divergence (e.g. a dropped/reordered packet briefly desyncing
     * {@link #myWeapon} from the server's real capacitor) shows up as a
     * predicted shot that's never matched (harmless — it just expires,
     * {@link #extrapolateProjectiles}) or a server shot with no predicted
     * counterpart (harmless — it's drawn as a normal, not-locally-predicted
     * {@link RemoteProjectile} instead, {@link #onWorldSnapshot}). This
     * exists purely to close the round-trip-latency gap between the local
     * player's own (always up-to-date, client-predicted) ship and their own
     * shots (previously only ever drawn once the server round trip
     * confirmed them) — see the class Javadoc and design.md 2.4's addendum
     * for the full "why."
     *
     * @param firing    whether the fire key is currently held
     * @param deltaTime time since the last frame, in seconds
     */
    private void predictLocalWeapon(boolean firing, float deltaTime) {
        if (myWeapon == null) {
            return;
        }
        myWeapon.tickCooldown(deltaTime);
        // Design.md - power-ups' BOOST effect, same "multiply on top of the distribution's own
        // multiplier" treatment as predictLocalShip's engines multiplier.
        float weaponsMultiplier = myPowerDistribution.multiplierFor(PowerSystem.WEAPONS) * myPowerBoostMultiplier;
        myWeapon.rechargeCapacitor(deltaTime, weaponsMultiplier);

        if (!firing || !myWeapon.canFire()) {
            return;
        }

        ShipStats myStats = ShipStats.forType(myShipType);
        List<PixelPoint> spawnPoints = myStats.getSpriteMetadata()
            .map(metadata -> metadata.getAttachmentPoints().get(WeaponStats.PROJECTILE_ATTACHMENT_NAME))
            .orElse(null);

        if (spawnPoints == null || spawnPoints.isEmpty()) {
            float spawnDistance = myStats.getRadiusMeters() + WeaponStats.BLASTER.getProjectileRadiusMeters() + 0.1f;
            PREDICTED_SPAWN_OFFSET.set(0, 1).rotateRad(myBody.getAngle()).scl(spawnDistance);
            spawnPredictedProjectile(PREDICTED_SPAWN_OFFSET.x, PREDICTED_SPAWN_OFFSET.y);
        } else {
            float pixelsPerMeter = myStats.getPixelsPerMeter();
            // Same authored order as myMuzzleFlashes (both read straight from this ship type's
            // one PROJECTILE attachment list), so index i here always lines up with that shot's
            // own muzzle - see buildMuzzleFlashPoints.
            float shipAngleDegrees = myBody.getAngle() * MathUtils.radiansToDegrees;
            for (int i = 0; i < spawnPoints.size(); i++) {
                PixelPoint point = spawnPoints.get(i);
                PREDICTED_SPAWN_OFFSET.set(point.getX() / pixelsPerMeter, point.getY() / pixelsPerMeter)
                    .rotateRad(myBody.getAngle());
                spawnPredictedProjectile(PREDICTED_SPAWN_OFFSET.x, PREDICTED_SPAWN_OFFSET.y);
                if (i < myMuzzleFlashes.size()) {
                    float flashXPixels = (myBody.getPosition().x + PREDICTED_SPAWN_OFFSET.x) * PhysicsConstants.PIXELS_PER_METER;
                    float flashYPixels = (myBody.getPosition().y + PREDICTED_SPAWN_OFFSET.y) * PhysicsConstants.PIXELS_PER_METER;
                    myMuzzleFlashes.get(i).effect.trigger(flashXPixels, flashYPixels, shipAngleDegrees,
                        myBody.getLinearVelocity().x * PhysicsConstants.PIXELS_PER_METER,
                        myBody.getLinearVelocity().y * PhysicsConstants.PIXELS_PER_METER);
                }
            }
        }

        myWeapon.consumeShot();
        // Played once per firing volley here, not once per spawnPredictedProjectile call above -
        // a multi-PROJECTILE-point ship (design.md — weapon sound) should only be heard firing
        // once, the same "one sound per volley" rule GameNetworkServer.WeaponSystem's real
        // capacitor draw already applies to the shot's energy cost.
        playWeaponSound(myPlayerId, myBody.getPosition().x * PhysicsConstants.PIXELS_PER_METER,
            myBody.getPosition().y * PhysicsConstants.PIXELS_PER_METER);
    }

    /**
     * Spawns one cosmetic predicted shot at {@code myBody}'s current position
     * plus the given local-frame offset (already rotated to the ship's
     * current facing by the caller), travelling at the same true world-frame
     * velocity {@code ProjectileFactory} computes server-side (muzzle speed
     * along the ship's facing, plus the ship's own current velocity) — added
     * to {@link #predictedProjectiles}, not {@link #projectiles}, since it
     * has no real {@code projectileId} yet.
     *
     * @param offsetXMeters the spawn offset from {@code myBody}'s position, in meters
     * @param offsetYMeters the spawn offset from {@code myBody}'s position, in meters
     */
    private void spawnPredictedProjectile(float offsetXMeters, float offsetYMeters) {
        float spawnXMeters = myBody.getPosition().x + offsetXMeters;
        float spawnYMeters = myBody.getPosition().y + offsetYMeters;
        PREDICTED_VELOCITY.set(0, 1).rotateRad(myBody.getAngle()).scl(WeaponStats.BLASTER.getProjectileSpeed())
            .add(myBody.getLinearVelocity());

        float xPixels = spawnXMeters * PhysicsConstants.PIXELS_PER_METER;
        float yPixels = spawnYMeters * PhysicsConstants.PIXELS_PER_METER;
        RemoteProjectile projectile = new RemoteProjectile(myPlayerId, xPixels, yPixels);
        projectile.updateFromSnapshot(xPixels, yPixels,
            PREDICTED_VELOCITY.x * PhysicsConstants.PIXELS_PER_METER,
            PREDICTED_VELOCITY.y * PhysicsConstants.PIXELS_PER_METER);
        predictedProjectiles.add(projectile);
    }

    private void extrapolateRemoteShips(float deltaTime) {
        for (RemoteShip ship : ships.values()) {
            ship.extrapolate(deltaTime);
        }
    }

    /**
     * Extrapolates every confirmed projectile the same as always, plus every
     * still-unconfirmed {@link #predictedProjectiles} entry — and expires any
     * predicted entry that's gone unmatched for longer than
     * {@link #PREDICTED_PROJECTILE_MAX_UNMATCHED_SECONDS}, so a prediction
     * that never gets confirmed doesn't linger on screen. Deliberately a much
     * shorter window than the weapon's own full projectile lifetime: an
     * unconfirmed prediction can mean either the shot simply hasn't been
     * broadcast back yet (normal, resolves within a snapshot or two) or it
     * hit something and was destroyed server-side almost immediately (never
     * broadcast at all, since a destroyed projectile just stops appearing in
     * snapshots — design.md 3.5's {@code ProjectileState} note) — either way,
     * a confirmation that hasn't arrived within a handful of ticks isn't
     * coming, and there's no reason to keep a phantom shot flying for the
     * weapon's full multi-second lifetime waiting for one.
     *
     * @param deltaTime time since the last frame, in seconds
     */
    private void extrapolateProjectiles(float deltaTime) {
        for (RemoteProjectile projectile : projectiles.values()) {
            projectile.extrapolate(deltaTime);
        }
        Iterator<RemoteProjectile> predictedIterator = predictedProjectiles.iterator();
        while (predictedIterator.hasNext()) {
            RemoteProjectile projectile = predictedIterator.next();
            projectile.extrapolate(deltaTime);
            if (projectile.elapsedSinceUpdate > PREDICTED_PROJECTILE_MAX_UNMATCHED_SECONDS) {
                predictedIterator.remove();
            }
        }
    }

    private void extrapolateAsteroids(float deltaTime) {
        for (RemoteAsteroid asteroid : asteroids.values()) {
            asteroid.extrapolate(deltaTime);
        }
    }

    private void extrapolatePowerUps(float deltaTime) {
        for (RemotePowerUp powerUp : powerUps.values()) {
            powerUp.extrapolate(deltaTime);
        }
    }

    /**
     * Draws every currently-active asteroid (design.md — asteroids) — dead
     * reckoned the same way as {@link #drawRemoteShips}/{@link #drawBlasterProjectiles},
     * but with no tint, thrusters, lights, or turrets to consider (an
     * asteroid is a plain environmental obstacle, not a player-owned ship).
     */
    private void drawAsteroids() {
        for (RemoteAsteroid asteroid : asteroids.values()) {
            TextureRegion region = asteroidRegionsByType.get(asteroid.type);
            float screenScale = PhysicsConstants.PIXELS_PER_METER / asteroid.type.getPixelsPerMeter();
            float widthPixels = region.getRegionWidth() * screenScale;
            float heightPixels = region.getRegionHeight() * screenScale;
            batch.draw(region,
                asteroid.renderX - widthPixels / 2f, asteroid.renderY - heightPixels / 2f,
                widthPixels / 2f, heightPixels / 2f,
                widthPixels, heightPixels,
                1f, 1f,
                asteroid.renderAngle * MathUtils.radiansToDegrees);
        }
    }

    /**
     * Draws every currently-active power-up (design.md — power-ups) — dead
     * reckoned the same way as {@link #drawAsteroids}. Per the user's own
     * instruction, each power-up's particle glow is updated and drawn
     * <em>before</em> its texture, both centered at the same
     * ({@code renderX}, {@code renderY}) — {@link SpriteBatch} has no depth
     * buffer, so draw order alone determines what layers on top of what.
     * Every power-up type shares the same fixed 256×256 texture size at
     * {@link PowerUpType#PIXELS_PER_METER}, unlike {@link #drawAsteroids}'s
     * per-type scale.
     *
     * @param deltaTime time since the last frame, in seconds — advances each power-up's glow
     */
    private void drawPowerUps(float deltaTime) {
        float screenScale = PhysicsConstants.PIXELS_PER_METER / PowerUpType.PIXELS_PER_METER;
        for (RemotePowerUp powerUp : powerUps.values()) {
            powerUp.effect.update(powerUp.renderX, powerUp.renderY, deltaTime);
            powerUp.effect.draw(batch);

            TextureRegion region = powerUpRegionsByType.get(powerUp.type);
            float widthPixels = region.getRegionWidth() * screenScale;
            float heightPixels = region.getRegionHeight() * screenScale;
            batch.draw(region,
                powerUp.renderX - widthPixels / 2f, powerUp.renderY - heightPixels / 2f,
                widthPixels / 2f, heightPixels / 2f,
                widthPixels, heightPixels,
                1f, 1f,
                powerUp.renderAngle * MathUtils.radiansToDegrees);
        }
    }

    /**
     * Draws every currently-active mine (design.md — mines) — no dead
     * reckoning needed at all, unlike {@link #drawAsteroids}/{@link #drawPowerUps}:
     * a mine never moves, so its position is exactly whatever the last
     * snapshot reported. The visible "rotation" is purely
     * {@link #mineAnimation}'s baked 64-frame sequence, driven by
     * {@link #mineAnimationStateTime}, not any server-authoritative angle.
     */
    private void drawMines() {
        for (RemoteMine mine : mines.values()) {
            TextureRegion region = mineAnimation.getKeyFrame(mineAnimationStateTime);
            float screenScale = PhysicsConstants.PIXELS_PER_METER / MineFactory.PIXELS_PER_METER;
            float widthPixels = region.getRegionWidth() * screenScale;
            float heightPixels = region.getRegionHeight() * screenScale;
            batch.draw(region,
                mine.x - widthPixels / 2f, mine.y - heightPixels / 2f,
                widthPixels, heightPixels);
        }
    }

    /**
     * Eases the camera toward the local ship's position (design.md 4.1's
     * "camera inertia") and its zoom toward a speed-driven target
     * (design.md 4.1's "speed-linked zoom", implemented 2026-09-09) — the
     * faster the ship is currently flying, the further out the camera
     * pulls back, trading detail for the forward visibility a player needs
     * to react to an obstacle (an asteroid, another ship, the arena
     * boundary) in time at speed.
     *
     * @param deltaTime time since the last frame, in seconds
     */
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

        float speedFraction = MathUtils.clamp(
            myBody.getLinearVelocity().len() / SPEED_ZOOM_REFERENCE_METERS_PER_SECOND, 0f, 1f);
        float targetZoom = 1f + speedFraction * SPEED_ZOOM_MAX_INCREASE;
        float zoomLerp = MathUtils.clamp(CAMERA_ZOOM_FOLLOW_SPEED * deltaTime, 0f, 1f);
        camera.zoom += (targetZoom - camera.zoom) * zoomLerp;

        camera.update();
    }

    private void drawRemoteShips(float deltaTime) {
        batch.setColor(OTHER_SHIP_TINT);
        for (Map.Entry<Integer, RemoteShip> entry : ships.entrySet()) {
            RemoteShip ship = entry.getValue();
            ShipStats stats = ShipStats.forType(ship.shipType);
            TextureRegion region = shipRegionsByType.get(ship.shipType);
            float screenScale = PhysicsConstants.PIXELS_PER_METER / stats.getPixelsPerMeter();
            float widthPixels = region.getRegionWidth() * screenScale;
            float heightPixels = region.getRegionHeight() * screenScale;

            // Drawn before the hull (design.md — engine particle effects' addendum), same
            // "opaque hull occludes the overlapping part of the flame" reasoning as
            // drawLocalShip - not affected by the OTHER_SHIP_TINT color above either way, since
            // ParticleEmitter draws each Particle (a Sprite) with its own already-baked vertex
            // color, unlike the TextureRegion-based hull/turret draw calls below, which do read
            // the batch's current default color.
            updateAndDrawThrusters(ship.thrusters, stats.getPixelsPerMeter(),
                ship.renderX, ship.renderY, ship.renderAngle, ship.thrusting, deltaTime);
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
            updateAndDrawLights(ship.lights, stats.getPixelsPerMeter(),
                ship.renderX, ship.renderY, ship.renderAngle, deltaTime);
            float shipDamageFraction = ship.hullMax > 0f ? 1f - (ship.hullCurrent / ship.hullMax) : 0f;
            updateAndDrawDamageSmoke(ship.damageSmoke, stats.getPixelsPerMeter(),
                ship.renderX, ship.renderY, ship.renderAngle, shipDamageFraction, deltaTime);
            ship.radarPulseEffect.update(ship.renderX, ship.renderY, deltaTime);
            ship.radarPulseEffect.draw(batch);
            if (showDisplayNames) {
                drawDisplayName(entry.getKey(), ship.renderX, ship.renderY + heightPixels / 2f + DISPLAY_NAME_VERTICAL_MARGIN_PIXELS);
            }
        }
        batch.setColor(Color.WHITE);
    }

    /**
     * Draws {@code playerId}'s display name, horizontally centered on
     * {@code labelBottomY} (design.md — display names) - a no-op if that
     * player's name isn't known yet ({@link #scoreboardEntries} hasn't
     * included them in a broadcast yet, e.g. the first second after they
     * connect). Never drawn for the local player - {@link #drawRemoteShips}
     * is this method's only caller, and the local player is never present
     * in {@link #ships} to begin with (design.md 2.14/3.5).
     *
     * @param playerId    the ship's owning player id, looked up in {@link #scoreboardEntries}
     * @param centerX     screen X to center the label on
     * @param labelBottomY the label text's own bottom edge, in screen coordinates
     */
    private void drawDisplayName(int playerId, float centerX, float labelBottomY) {
        String displayName = findDisplayName(playerId);
        if (displayName == null) {
            return;
        }
        displayNameLayout.setText(displayNameFont, displayName);
        displayNameFont.setColor(DISPLAY_NAME_COLOR);
        displayNameFont.draw(batch, displayNameLayout, centerX - displayNameLayout.width / 2f,
            labelBottomY + displayNameLayout.height);
    }

    /**
     * Looks up {@code playerId}'s display name from the latest
     * {@link #scoreboardEntries} broadcast (design.md 2.11) - the only
     * place any client learns another player's display name at all, since
     * neither {@code ShipState} nor any other per-tick message carries it.
     *
     * @param playerId the player id to look up
     * @return that player's display name, or {@code null} if not yet known
     */
    private String findDisplayName(int playerId) {
        for (PlayerScoreEntry entry : scoreboardEntries) {
            if (entry.getPlayerId() == playerId) {
                return entry.getDisplayName();
            }
        }
        return null;
    }

    /**
     * Fades every visible {@link RemoteShip}'s engine-loop volume toward
     * {@code ship.thrusting}'s implied 0/1 target, then scales it down by
     * distance from the local player's own ship (design.md — engine sound's
     * optional distance-falloff addendum: full volume up close, silent
     * beyond {@link #REMOTE_SOUND_MAX_AUDIBLE_RANGE_METERS}). Deliberately
     * called <em>after</em> {@link #drawLocalShip} in {@code render()} (not
     * alongside {@link #drawRemoteShips}, which runs first) so
     * {@link #myRenderScreenX}/{@link #myRenderScreenY} are this same
     * frame's fresh values, not one frame stale.
     *
     * @param deltaTime time since the last frame, in seconds
     */
    private void updateRemoteEngineSounds(float deltaTime) {
        for (RemoteShip ship : ships.values()) {
            if (ship.engineSound == null) {
                continue;
            }
            float distanceMeters = distanceFromLocalShipMeters(ship.renderX, ship.renderY);
            ship.engineVolumeFraction = EngineAudioMath.approachFraction(ship.engineVolumeFraction,
                ship.thrusting ? 1f : 0f, deltaTime, ENGINE_SOUND_FADE_SECONDS);
            float distanceFraction = EngineAudioMath.distanceVolumeFraction(distanceMeters, REMOTE_SOUND_MAX_AUDIBLE_RANGE_METERS);
            ship.engineSound.setVolume(ship.engineSoundId,
                ship.engineVolumeFraction * distanceFraction * game.getAudioSettings().getEffectiveEnginesVolume());
        }
    }

    /**
     * Returns the local player's own ship's distance from the given screen
     * position, in meters — shared by {@link #updateRemoteEngineSounds} and
     * every one-shot combat sound ({@link #playPositionalSound}), since both
     * use the same "distance from the local ship" volume falloff (design.md
     * — weapon sound). Uses {@link #myRenderScreenX}/{@link #myRenderScreenY}
     * as they stand at the moment of the call — for the local player's own
     * shots this is always effectively 0, so no separate "am I the shooter"
     * case is needed anywhere that calls this.
     *
     * @param xPixels the position to measure from, in screen/world pixels
     * @param yPixels the position to measure from, in screen/world pixels
     * @return the distance, in meters
     */
    private float distanceFromLocalShipMeters(float xPixels, float yPixels) {
        float dxMeters = (xPixels - myRenderScreenX) / PhysicsConstants.PIXELS_PER_METER;
        float dyMeters = (yPixels - myRenderScreenY) / PhysicsConstants.PIXELS_PER_METER;
        return (float) Math.sqrt(dxMeters * dxMeters + dyMeters * dyMeters);
    }

    /**
     * Plays {@code sound} once, as a one-shot effect, at a volume determined
     * by its distance from the local player's own ship (design.md — weapon
     * sound: the same volume/distance calculation as
     * {@link #updateRemoteEngineSounds}'s engine loops) scaled by
     * {@code categoryVolume} (design.md — audio settings: the player's own
     * master × category volume slider) — full volume up close (including,
     * trivially, for the local player's own shots, whose position is always
     * ~0m from {@link #myRenderScreenX}/{@link #myRenderScreenY}), silent
     * beyond {@link #REMOTE_SOUND_MAX_AUDIBLE_RANGE_METERS} or once
     * {@code categoryVolume} itself is 0. Skips playing entirely once the
     * resulting volume is 0, rather than starting an inaudible instance.
     *
     * @param sound          the sound to play, or {@code null} if not loaded (a no-op)
     * @param xPixels        where this sound's source is, in screen/world pixels
     * @param yPixels        where this sound's source is, in screen/world pixels
     * @param categoryVolume the effective category volume to scale by, in {@code [0, 1]}
     *                       (e.g. {@link de.mkoehler.starwars.audio.AudioSettings#getEffectiveWeaponsVolume()})
     */
    private void playPositionalSound(Sound sound, float xPixels, float yPixels, float categoryVolume) {
        if (sound == null || categoryVolume <= 0f) {
            return;
        }
        float distanceMeters = distanceFromLocalShipMeters(xPixels, yPixels);
        float distanceFraction = EngineAudioMath.distanceVolumeFraction(distanceMeters, REMOTE_SOUND_MAX_AUDIBLE_RANGE_METERS);
        float volume = distanceFraction * categoryVolume;
        if (volume > 0f) {
            sound.play(volume);
        }
    }

    /**
     * Resolves {@code ownerPlayerId}'s ship type, for picking which one-shot
     * combat sound clip to play (design.md — weapon sound) — the local
     * player's own type for its own id (not present in {@link #ships}, which
     * only ever holds other players), otherwise whichever {@link RemoteShip}
     * currently detected for that id, or {@code null} if that shooter isn't
     * currently a detected/rendered ship at all (as good as it gets without
     * that ship's own data to draw on, same fallback shape as the muzzle
     * flash's shooter-velocity lookup).
     *
     * @param ownerPlayerId the shooter's player id
     * @return the shooter's ship type, or {@code null} if unknown
     */
    private ShipType resolveShipType(int ownerPlayerId) {
        if (ownerPlayerId == myPlayerId) {
            return myShipType;
        }
        RemoteShip ship = ships.get(ownerPlayerId);
        return ship != null ? ship.shipType : null;
    }

    /**
     * Plays {@code ownerPlayerId}'s ship type's own main-gun firing sound
     * (design.md — weapon sound), if that ship type has one loaded and is
     * currently resolvable ({@link #resolveShipType}).
     *
     * @param ownerPlayerId the shooter's player id
     * @param xPixels       where the shot spawned, in screen/world pixels
     * @param yPixels       where the shot spawned, in screen/world pixels
     */
    private void playWeaponSound(int ownerPlayerId, float xPixels, float yPixels) {
        ShipType shooterType = resolveShipType(ownerPlayerId);
        if (shooterType == null) {
            return;
        }
        String path = GameAssets.weaponSoundPath(shooterType);
        if (!game.getAssets().isLoaded(path, Sound.class)) {
            return;
        }
        playPositionalSound(game.getAssets().get(path, Sound.class), xPixels, yPixels,
            game.getAudioSettings().getEffectiveWeaponsVolume());
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

    private void drawLocalShip(float deltaTime) {
        if (myBody == null) {
            return;
        }
        float alpha = localPhysicsSystem.getAlpha();
        float x = MathUtils.lerp(myPreviousX, myBody.getPosition().x, alpha) * PhysicsConstants.PIXELS_PER_METER;
        float y = MathUtils.lerp(myPreviousY, myBody.getPosition().y, alpha) * PhysicsConstants.PIXELS_PER_METER;
        float angle = MathUtils.lerpAngle(myPreviousAngle, myBody.getAngle(), alpha);
        // Cached so drawMissileLockReticle can anchor the victim-side reticle (design.md —
        // missiles' addendum) to the exact same screen position this frame's own ship sprite was
        // just drawn at, without recomputing the interpolation itself.
        myRenderScreenX = x;
        myRenderScreenY = y;

        ShipStats myStats = ShipStats.forType(myShipType);
        TextureRegion region = shipRegionsByType.get(myShipType);
        float screenScale = PhysicsConstants.PIXELS_PER_METER / myStats.getPixelsPerMeter();
        float widthPixels = region.getRegionWidth() * screenScale;
        float heightPixels = region.getRegionHeight() * screenScale;

        // Drawn before the hull (design.md — engine particle effects' addendum), not after: the
        // opaque hull sprite then paints over whatever part of the flame overlaps it, so only the
        // portion actually extending past the tail is visible - reads as the engine sitting
        // underneath/behind the hull instead of floating on top of it.
        boolean thrustForwardHeld = keyBindings.isPressed(GameAction.THRUST_FORWARD);
        updateAndDrawThrusters(myThrusters, myStats.getPixelsPerMeter(), x, y, angle,
            thrustForwardHeld, deltaTime);
        updateLocalEngineSound(thrustForwardHeld, deltaTime);
        batch.draw(region,
            x - widthPixels / 2f, y - heightPixels / 2f,
            widthPixels / 2f, heightPixels / 2f,
            widthPixels, heightPixels,
            1f, 1f,
            angle * MathUtils.radiansToDegrees);
        drawTurrets(myShipType, myStats, x, y, angle, myTurretAimAngles);
        updateAndDrawLights(myLights, myStats.getPixelsPerMeter(), x, y, angle, deltaTime);
        float myDamageFraction = myHullMax > 0f ? 1f - (myHullCurrent / myHullMax) : 0f;
        updateAndDrawDamageSmoke(myDamageSmoke, myStats.getPixelsPerMeter(), x, y, angle, myDamageFraction, deltaTime);
        myRadarPulseEffect.update(x, y, deltaTime);
        myRadarPulseEffect.draw(batch);
        for (MuzzleFlashPoint point : myMuzzleFlashes) {
            point.effect.update(deltaTime);
            point.effect.draw(batch);
        }
    }

    /**
     * Updates and draws one ship's engine thruster glow(s), if any
     * (design.md — engine particle effects) — only actually visible while
     * {@code thrusting} is {@code true}, see {@link ThrusterEffect#update}.
     * Shared by both the local player's own ship ({@link #drawLocalShip})
     * and every other visible ship ({@link #drawRemoteShips}) — the latter
     * only possible since {@code ShipState} now broadcasts whether a ship is
     * currently holding its forward-thrust input (design.md's addendum).
     *
     * @param thrusters        this ship's thrusters, empty for a ship type with none configured
     * @param pixelsPerMeter   this ship type's own pixels-per-meter, for converting attachment offsets
     * @param shipScreenX      the ship's current on-screen position
     * @param shipScreenY      the ship's current on-screen position
     * @param shipAngleRadians the ship's current facing
     * @param thrusting        whether this ship is currently holding its forward-thrust input
     * @param deltaTime        time since the last frame, in seconds
     */
    private void updateAndDrawThrusters(List<EngineThruster> thrusters, float pixelsPerMeter,
                                         float shipScreenX, float shipScreenY, float shipAngleRadians,
                                         boolean thrusting, float deltaTime) {
        if (thrusters.isEmpty()) {
            return;
        }
        float shipAngleDegrees = shipAngleRadians * MathUtils.radiansToDegrees;
        for (EngineThruster thruster : thrusters) {
            PixelPoint point = thruster.attachmentPoint;
            ATTACHMENT_OFFSET.set(point.getX() / pixelsPerMeter * PhysicsConstants.PIXELS_PER_METER,
                point.getY() / pixelsPerMeter * PhysicsConstants.PIXELS_PER_METER).rotateRad(shipAngleRadians);
            thruster.effect.update(shipScreenX + ATTACHMENT_OFFSET.x, shipScreenY + ATTACHMENT_OFFSET.y,
                shipAngleDegrees, thrusting, deltaTime);
            thruster.effect.draw(batch);
        }
    }

    /**
     * Builds one ship type's engine thrusters (design.md — engine particle
     * effects) — one {@link ThrusterEffect} per {@code "ENGINE"} attachment
     * point, sharing that type's configured particle effect template. Empty
     * (never {@code null}) if the type has no attachment points, or no
     * effect configured, for one.
     *
     * @param stats the ship type's stats
     * @return that type's thrusters, or an empty list if it has none
     */
    private List<EngineThruster> buildEngineThrusters(ShipStats stats) {
        List<PixelPoint> enginePoints = stats.getSpriteMetadata()
            .map(metadata -> metadata.getAttachmentPoints().get(ThrusterEffect.ENGINE_ATTACHMENT_NAME))
            .orElse(null);
        if (enginePoints == null || enginePoints.isEmpty()) {
            return List.of();
        }
        Optional<String> effectName = stats.getEngineParticleEffect();
        if (effectName.isEmpty()) {
            return List.of();
        }
        ParticleEffect template = game.getAssets().get(GameAssets.particleEffectPath(effectName.get()), ParticleEffect.class);
        List<EngineThruster> thrusters = new ArrayList<>();
        for (PixelPoint point : enginePoints) {
            thrusters.add(new EngineThruster(point, new ThrusterEffect(template)));
        }
        return thrusters;
    }

    /**
     * Loads (or swaps) the local player's own {@link #myEngineSound} for
     * {@code shipType}, if not already loaded for that exact type —
     * deliberately does <em>not</em> restart an already-loaded, already-
     * playing loop, since a respawn with the same ship type would otherwise
     * glitch the audio for no gameplay reason (see {@link #myEngineSound}'s
     * Javadoc). Calling this with the same {@code shipType} repeatedly
     * (every {@link #onShipSpawned}) is therefore cheap and safe.
     *
     * @param shipType the ship type to ensure an engine loop is playing for
     */
    private void ensureLocalEngineSound(ShipType shipType) {
        if (myEngineSound != null && myEngineSoundShipType == shipType) {
            return;
        }
        stopLocalEngineSound();
        String path = GameAssets.engineSoundPath(shipType);
        if (!game.getAssets().isLoaded(path, Sound.class)) {
            return;
        }
        myEngineSound = game.getAssets().get(path, Sound.class);
        myEngineSoundShipType = shipType;
        myEngineVolumeFraction = 0f;
        myEngineSoundId = myEngineSound.loop(0f);
    }

    /**
     * Stops and forgets the local player's own {@link #myEngineSound}
     * instance, if any — called before loading a different ship type's loop
     * ({@link #ensureLocalEngineSound}) and from {@link #dispose()}, so this
     * client never leaves a shared, {@code AssetManager}-owned {@link Sound}
     * looping forever in the background after this screen is gone.
     */
    private void stopLocalEngineSound() {
        if (myEngineSound != null) {
            myEngineSound.stop(myEngineSoundId);
        }
        myEngineSound = null;
        myEngineSoundShipType = null;
        myEngineSoundId = -1;
    }

    /**
     * Starts/stops the local player's own missile-lock loop sounds (design.md
     * — missiles' audio addendum) to match {@link #myMissileLockTargetPlayerId}/
     * {@link #myMissileLockAcquired}'s current state — called every time
     * {@link #onWorldSnapshot} updates either of them. The two clips are
     * mutually exclusive (acquired <em>replaces</em> trying, never layers on
     * top of it), so both "instantly start/stop" per the user's own spec
     * fall out for free from a plain state-transition diff: firing a missile
     * or losing the lock both reset {@link #myMissileLockTargetPlayerId} to
     * {@link ShipState#NO_MISSILE_LOCK_TARGET} server-side
     * ({@code MissileLockSystem}/{@code GameNetworkServer.processMissileFireRequests}),
     * which this method sees as an ordinary transition to {@link MissileLockAudioState#NONE},
     * no special-casing needed for either event specifically.
     */
    private void updateMissileLockAudioState() {
        MissileLockAudioState desired;
        if (myMissileLockTargetPlayerId == ShipState.NO_MISSILE_LOCK_TARGET) {
            desired = MissileLockAudioState.NONE;
        } else {
            desired = myMissileLockAcquired ? MissileLockAudioState.ACQUIRED : MissileLockAudioState.TRYING;
        }
        if (desired == myMissileLockAudioState) {
            return;
        }
        stopMissileLockSound();
        myMissileLockAudioState = desired;
        if (desired == MissileLockAudioState.NONE) {
            return;
        }
        String path = missileLockSoundPath(desired);
        if (game.getAssets().isLoaded(path, Sound.class)) {
            myMissileLockSoundId = game.getAssets().get(path, Sound.class)
                .loop(game.getAudioSettings().getEffectiveWeaponsVolume());
        }
    }

    /**
     * Stops whichever missile-lock loop {@link #myMissileLockAudioState}
     * currently says is playing, if any - called both from
     * {@link #updateMissileLockAudioState} (right before switching to a
     * different state) and from {@link #dispose()}, so a shared,
     * {@code AssetManager}-owned {@link Sound} never keeps looping in the
     * background after this screen is gone.
     */
    private void stopMissileLockSound() {
        if (myMissileLockAudioState == MissileLockAudioState.NONE) {
            return;
        }
        String path = missileLockSoundPath(myMissileLockAudioState);
        if (game.getAssets().isLoaded(path, Sound.class)) {
            game.getAssets().get(path, Sound.class).stop(myMissileLockSoundId);
        }
        myMissileLockAudioState = MissileLockAudioState.NONE;
        myMissileLockSoundId = -1;
    }

    private String missileLockSoundPath(MissileLockAudioState state) {
        return switch (state) {
            case TRYING -> GameAssets.MISSILE_LOCK_TRYING_SOUND;
            case ACQUIRED -> GameAssets.MISSILE_LOCK_ACQUIRED_SOUND;
            case NONE -> throw new IllegalArgumentException("NONE has no sound to look up");
        };
    }

    /**
     * Fades the local player's own engine loop's volume toward 1 while
     * {@code thrustForwardHeld}, toward 0 otherwise (design.md — engine
     * sound: "the thrust button simply controlling the volume"), over
     * {@link #ENGINE_SOUND_FADE_SECONDS} either way — never restarting the
     * loop itself, just adjusting its volume via
     * {@link Sound#setVolume(long, float)} on the one long-lived
     * {@link #myEngineSoundId} instance.
     *
     * @param thrustForwardHeld whether the thrust key is currently held
     * @param deltaTime         time since the last frame, in seconds
     */
    private void updateLocalEngineSound(boolean thrustForwardHeld, float deltaTime) {
        if (myEngineSound == null) {
            return;
        }
        myEngineVolumeFraction = EngineAudioMath.approachFraction(myEngineVolumeFraction,
            thrustForwardHeld ? 1f : 0f, deltaTime, ENGINE_SOUND_FADE_SECONDS);
        myEngineSound.setVolume(myEngineSoundId, myEngineVolumeFraction * game.getAudioSettings().getEffectiveEnginesVolume());
    }

    /**
     * Updates and draws one ship's positioning light(s), if any (design.md
     * — positioning lights) — unlike {@link #updateAndDrawThrusters}, these
     * are never gated on player input; they simply run for as long as the
     * ship exists. Shared by both the local player's own ship
     * ({@link #drawLocalShip}) and every other visible ship
     * ({@link #drawRemoteShips}) — no wire state is needed for the latter,
     * since a positioning light's "on" state doesn't depend on anything a
     * remote client wouldn't already know (the ship exists and has one).
     *
     * @param lights           this ship's lights, empty for a ship type with none configured
     * @param pixelsPerMeter   this ship type's own pixels-per-meter, for converting attachment offsets
     * @param shipScreenX      the ship's current on-screen position
     * @param shipScreenY      the ship's current on-screen position
     * @param shipAngleRadians the ship's current facing
     * @param deltaTime        time since the last frame, in seconds
     */
    private void updateAndDrawLights(List<ShipLight> lights, float pixelsPerMeter,
                                      float shipScreenX, float shipScreenY, float shipAngleRadians, float deltaTime) {
        for (ShipLight light : lights) {
            PixelPoint point = light.attachmentPoint;
            ATTACHMENT_OFFSET.set(point.getX() / pixelsPerMeter * PhysicsConstants.PIXELS_PER_METER,
                point.getY() / pixelsPerMeter * PhysicsConstants.PIXELS_PER_METER).rotateRad(shipAngleRadians);
            light.effect.update(shipScreenX + ATTACHMENT_OFFSET.x, shipScreenY + ATTACHMENT_OFFSET.y, deltaTime);
            light.effect.draw(batch);
        }
    }

    /**
     * Builds one ship type's positioning lights (design.md — positioning
     * lights) — one {@link ShipLightEffect} per {@code "LIGHT_RED"}/
     * {@code "LIGHT_GREEN"} attachment point, all sharing the same two
     * global templates ({@link GameAssets#LIGHT_RED_PARTICLE}/
     * {@link GameAssets#LIGHT_GREEN_PARTICLE}) regardless of ship type.
     * Empty (never {@code null}) if the type has neither kind of attachment
     * point authored.
     *
     * @param stats the ship type's stats
     * @return that type's lights, or an empty list if it has none
     */
    private List<ShipLight> buildShipLights(ShipStats stats) {
        List<ShipLight> lights = new ArrayList<>();
        stats.getSpriteMetadata().ifPresent(metadata -> {
            addShipLights(lights, metadata.getAttachmentPoints().get(ShipLightEffect.LIGHT_RED_ATTACHMENT_NAME),
                GameAssets.LIGHT_RED_PARTICLE);
            addShipLights(lights, metadata.getAttachmentPoints().get(ShipLightEffect.LIGHT_GREEN_ATTACHMENT_NAME),
                GameAssets.LIGHT_GREEN_PARTICLE);
        });
        return lights;
    }

    private void addShipLights(List<ShipLight> lights, List<PixelPoint> points, String templatePath) {
        if (points == null || points.isEmpty()) {
            return;
        }
        ParticleEffect template = game.getAssets().get(templatePath, ParticleEffect.class);
        for (PixelPoint point : points) {
            lights.add(new ShipLight(point, new ShipLightEffect(template)));
        }
    }

    /**
     * The hull-damage fraction (1 - current/max) beyond which each
     * successive {@code "DAMAGE_SMOKE"} attachment point activates
     * (design.md — damage smoke, the user's own spec): the first point at
     * more than 10% damage, the second at more than 50% - indexed by
     * authored order. A ship type with more than two points (none does
     * today) reuses the last threshold for every point beyond the second -
     * an unconfirmed default, since the user's spec only covered exactly
     * one or two points.
     */
    private static final float[] DAMAGE_SMOKE_THRESHOLDS = {0.10f, 0.50f};

    /**
     * Updates and draws one ship's damage smoke plume(s), if any
     * (design.md — damage smoke) — each point independently active once
     * {@code damageFraction} exceeds its own threshold
     * ({@link #DAMAGE_SMOKE_THRESHOLDS}), see {@link DamageSmokeEffect#update}.
     * Shared by both the local player's own ship ({@link #drawLocalShip})
     * and every other visible ship ({@link #drawRemoteShips}) - hull
     * current/max are already broadcast for every ship, so no new wire
     * state was needed to support the latter.
     *
     * @param points           this ship's damage smoke points, empty for a ship type with none configured
     * @param pixelsPerMeter   this ship type's own pixels-per-meter, for converting attachment offsets
     * @param shipScreenX      the ship's current on-screen position
     * @param shipScreenY      the ship's current on-screen position
     * @param shipAngleRadians the ship's current facing
     * @param damageFraction   this ship's current hull damage, as a fraction of its max hull (0 = undamaged, 1 = destroyed)
     * @param deltaTime        time since the last frame, in seconds
     */
    private void updateAndDrawDamageSmoke(List<DamageSmokePoint> points, float pixelsPerMeter,
                                           float shipScreenX, float shipScreenY, float shipAngleRadians,
                                           float damageFraction, float deltaTime) {
        for (DamageSmokePoint point : points) {
            PixelPoint attachment = point.attachmentPoint;
            ATTACHMENT_OFFSET.set(attachment.getX() / pixelsPerMeter * PhysicsConstants.PIXELS_PER_METER,
                attachment.getY() / pixelsPerMeter * PhysicsConstants.PIXELS_PER_METER).rotateRad(shipAngleRadians);
            boolean active = damageFraction > point.damageThresholdFraction;
            point.effect.update(shipScreenX + ATTACHMENT_OFFSET.x, shipScreenY + ATTACHMENT_OFFSET.y, active, deltaTime);
            point.effect.draw(batch);
        }
    }

    /**
     * Builds one ship type's damage smoke points (design.md — damage
     * smoke) — one {@link DamageSmokeEffect} per {@code "DAMAGE_SMOKE"}
     * attachment point, in authored order, each paired with its own
     * threshold from {@link #DAMAGE_SMOKE_THRESHOLDS}, all sharing the
     * same global template ({@link GameAssets#DAMAGE_SMOKE_PARTICLE})
     * regardless of ship type. Empty (never {@code null}) if the type has
     * no such attachment points authored (e.g. the Star Destroyer).
     *
     * @param stats the ship type's stats
     * @return that type's damage smoke points, or an empty list if it has none
     */
    private List<DamageSmokePoint> buildDamageSmokePoints(ShipStats stats) {
        List<DamageSmokePoint> points = new ArrayList<>();
        stats.getSpriteMetadata().ifPresent(metadata -> {
            List<PixelPoint> smokePoints = metadata.getAttachmentPoints().get(DamageSmokeEffect.DAMAGE_SMOKE_ATTACHMENT_NAME);
            if (smokePoints == null || smokePoints.isEmpty()) {
                return;
            }
            ParticleEffect template = game.getAssets().get(GameAssets.DAMAGE_SMOKE_PARTICLE, ParticleEffect.class);
            for (int i = 0; i < smokePoints.size(); i++) {
                float threshold = DAMAGE_SMOKE_THRESHOLDS[Math.min(i, DAMAGE_SMOKE_THRESHOLDS.length - 1)];
                points.add(new DamageSmokePoint(smokePoints.get(i), threshold, new DamageSmokeEffect(template)));
            }
        });
        return points;
    }

    /**
     * Creates one {@link OneShotParticleEffect} instance from a template
     * path - shared factory for every one-shot effect this client uses
     * (radar pulse wave, both explosion sizes), each just a different
     * template.
     *
     * @param templatePath the effect's classpath path, see {@link GameAssets}
     * @return a fresh, not-yet-triggered one-shot effect
     */
    private OneShotParticleEffect createOneShotEffect(String templatePath) {
        ParticleEffect template = game.getAssets().get(templatePath, ParticleEffect.class);
        return new OneShotParticleEffect(template);
    }

    /**
     * Builds one ship type's muzzle flash points (design.md — muzzle
     * flash) — one {@link MuzzleFlashEffect} per {@code "PROJECTILE"}
     * attachment point, in the same authored order
     * {@link #predictLocalWeapon} already spawns predicted shots in, all
     * sharing the same global template ({@link GameAssets#MUZZLE_FLASH_PARTICLE})
     * regardless of ship type. Empty (never {@code null}) if the type has
     * no such attachment points authored.
     *
     * @param stats the ship type's stats
     * @return that type's muzzle flash points, or an empty list if it has none
     */
    private List<MuzzleFlashPoint> buildMuzzleFlashPoints(ShipStats stats) {
        List<MuzzleFlashPoint> points = new ArrayList<>();
        stats.getSpriteMetadata().ifPresent(metadata -> {
            List<PixelPoint> projectilePoints = metadata.getAttachmentPoints().get(WeaponStats.PROJECTILE_ATTACHMENT_NAME);
            if (projectilePoints == null || projectilePoints.isEmpty()) {
                return;
            }
            ParticleEffect template = game.getAssets().get(GameAssets.MUZZLE_FLASH_PARTICLE, ParticleEffect.class);
            for (PixelPoint point : projectilePoints) {
                points.add(new MuzzleFlashPoint(point, new MuzzleFlashEffect(template)));
            }
        });
        return points;
    }

    /**
     * Finds a currently-idle instance in {@link #remoteMuzzleFlashPool} to
     * reuse, or grows the pool by one if every existing instance is still
     * mid-playback.
     *
     * @return a muzzle flash effect ready to {@link MuzzleFlashEffect#trigger}
     */
    private MuzzleFlashEffect obtainPooledMuzzleFlash() {
        for (MuzzleFlashEffect flash : remoteMuzzleFlashPool) {
            if (!flash.isPlaying()) {
                return flash;
            }
        }
        ParticleEffect template = game.getAssets().get(GameAssets.MUZZLE_FLASH_PARTICLE, ParticleEffect.class);
        MuzzleFlashEffect flash = new MuzzleFlashEffect(template);
        remoteMuzzleFlashPool.add(flash);
        return flash;
    }

    /**
     * Triggers one explosion (design.md — explosions) at a fixed world
     * position, reusing a currently-idle {@link PositionedOneShotEffect}
     * from {@code pool} or growing it by one if every existing entry is
     * still mid-playback - shared by {@link #hitExplosionPool} and
     * {@link #shipExplosionPool} (unlike {@link #obtainPooledMuzzleFlash},
     * which needs its own copy of this pooling logic since
     * {@link MuzzleFlashEffect} is a different class).
     *
     * @param pool         the pool to search/grow
     * @param templatePath the effect's classpath path, used only if the pool needs to grow
     * @param xPixels      the explosion's world position, in pixels
     * @param yPixels      the explosion's world position, in pixels
     */
    private void triggerPooledExplosion(List<PositionedOneShotEffect> pool, String templatePath,
                                         float xPixels, float yPixels) {
        for (PositionedOneShotEffect entry : pool) {
            if (!entry.effect.isPlaying()) {
                entry.x = xPixels;
                entry.y = yPixels;
                entry.effect.trigger();
                return;
            }
        }
        PositionedOneShotEffect entry = new PositionedOneShotEffect(createOneShotEffect(templatePath));
        entry.x = xPixels;
        entry.y = yPixels;
        entry.effect.trigger();
        pool.add(entry);
    }

    /**
     * Updates and draws every entry in a pooled-explosion list (design.md
     * — explosions) - called once per frame per pool, not per ship, same
     * reasoning as {@link #updateAndDrawRemoteMuzzleFlashes}.
     *
     * @param pool      the pool to update/draw
     * @param deltaTime time since the last frame, in seconds
     */
    private void updateAndDrawExplosions(List<PositionedOneShotEffect> pool, float deltaTime) {
        for (PositionedOneShotEffect entry : pool) {
            entry.effect.update(entry.x, entry.y, deltaTime);
            entry.effect.draw(batch);
        }
    }

    /**
     * Updates and draws every currently in-flight {@link #remoteMuzzleFlashPool}
     * entry (design.md — muzzle flash's addendum) — unlike
     * {@link #myMuzzleFlashes}, not tied to any particular ship, so this is
     * called once per frame rather than once per ship.
     *
     * @param deltaTime time since the last frame, in seconds
     */
    private void updateAndDrawRemoteMuzzleFlashes(float deltaTime) {
        for (MuzzleFlashEffect flash : remoteMuzzleFlashPool) {
            flash.update(deltaTime);
            flash.draw(batch);
        }
    }

    /**
     * Draws every currently-known missile, filtered out of the general
     * {@link #projectiles} map by {@link RemoteProjectile#trackedTargetPlayerId}
     * — called *before* {@link #drawRemoteShips}/{@link #drawLocalShip}
     * (design.md — missiles' addendum), not after like
     * {@link #drawBlasterProjectiles}, so every ship renders on top of its
     * own (and everyone else's) missiles. A missile now spawns at its
     * firing ship's exact center with no launch offset (server-side), so
     * this draw order is what actually sells "launched from underneath the
     * ship" rather than the missile just popping into view already clear
     * of the hull. Never sourced from {@link #predictedProjectiles} — that
     * list is blaster-only local prediction (design.md 2.4's addendum), a
     * missile is never speculatively drawn before the server confirms it.
     */
    private void drawMissiles() {
        for (RemoteProjectile projectile : projectiles.values()) {
            if (projectile.trackedTargetPlayerId != ProjectileComponent.NO_TRACKED_TARGET) {
                drawProjectile(projectile, 0f); // widthPixels is unused/overwritten for a missile, see drawProjectile
            }
        }
    }

    /**
     * Draws every currently-known ordinary blaster shot (including this
     * player's own not-yet-server-confirmed {@link #predictedProjectiles})
     * — called *after* {@link #drawRemoteShips}/{@link #drawLocalShip}, same
     * top-most layering blaster shots have always had; only missiles moved
     * underneath ships (see {@link #drawMissiles}).
     */
    private void drawBlasterProjectiles() {
        // The projectile art is an elongated oval (nose-up, same authoring convention as ship
        // sprites) rather than a circle, so it visually implies speed/direction - but the actual
        // Box2D hitbox stays a circle regardless (WeaponStats.BLASTER's radius), same as a ship's
        // polygon hitbox not needing to match its sprite's bounding box exactly. Width is tied to
        // that physical diameter; height is derived from the region's own aspect ratio so the art
        // controls how elongated it looks without a second tuning constant to keep in sync.
        float widthPixels = WeaponStats.BLASTER.getProjectileRadiusMeters() * 2f * PhysicsConstants.PIXELS_PER_METER;

        for (RemoteProjectile projectile : projectiles.values()) {
            if (projectile.trackedTargetPlayerId == ProjectileComponent.NO_TRACKED_TARGET) {
                drawProjectile(projectile, widthPixels);
            }
        }
        // Locally-predicted shots (design.md 2.4's addendum) not yet confirmed by the server -
        // drawn exactly like any other of the local player's own shots (same red tint, same
        // travel-direction rotation), just sourced from predictedProjectiles instead of the
        // id-keyed projectiles map.
        for (RemoteProjectile projectile : predictedProjectiles) {
            drawProjectile(projectile, widthPixels);
        }
    }

    private void drawProjectile(RemoteProjectile projectile, float widthPixels) {
        TextureRegion region;
        float heightPixels;
        if (projectile.trackedTargetPlayerId != ProjectileComponent.NO_TRACKED_TARGET) {
            // A missile (design.md — missiles): its own art/size, not the blaster oval - sized the
            // same way a ship is (region pixel size / this entity's own pixels-per-meter), same
            // convention as every other authored-sprite entity in this project.
            region = missileRegion;
            float screenScale = PhysicsConstants.PIXELS_PER_METER / MissileStats.INSTANCE.getPixelsPerMeter();
            widthPixels = region.getRegionWidth() * screenScale;
            heightPixels = region.getRegionHeight() * screenScale;
        } else {
            // Own shots draw red, everyone else's draw blue - purely a rendering choice
            // (design.md 3.5), the server treats every projectile identically.
            region = projectile.ownerPlayerId == myPlayerId ? ownProjectileRegion : enemyProjectileRegion;
            heightPixels = widthPixels * region.getRegionHeight() / (float) region.getRegionWidth();
        }
        // Rotated to its actual travel direction (velocity), not the angle it was fired at -
        // those differ once the firing ship's own velocity is added on top of muzzle velocity
        // (design.md 2.4's addendum). Inverse of this project's angle-to-direction convention,
        // same formula TurretAiming already uses for the same reason.
        float travelAngle = MathUtils.atan2(-projectile.velocityX, projectile.velocityY);
        batch.draw(region,
            projectile.renderX - widthPixels / 2f, projectile.renderY - heightPixels / 2f,
            widthPixels / 2f, heightPixels / 2f,
            widthPixels, heightPixels,
            1f, 1f,
            travelAngle * MathUtils.radiansToDegrees);
    }

    /**
     * Draws the local player's missile lock reticle(s), three stages layered
     * on top of each other (design.md — missiles): the outer ring alone
     * while a lock is being acquired, plus the inner ring once acquired,
     * plus the static center mark once a missile is actually in flight at
     * that target. Two independent, simultaneously-possible cases, both
     * driven purely by this player's own latest {@code ShipState} (never a
     * separate lookup per enemy):
     * <ul>
     *   <li><b>Attacker side</b> ({@link #myMissileLockTargetPlayerId}) —
     *   drawn over the target's own {@link RemoteShip#renderX}/{@link RemoteShip#renderY}
     *   in {@code ships}. A lock target is always radar-detected by
     *   definition (missile lock only ever acquires within this ship's own
     *   cone radar), so it's guaranteed to already be tracked there; simply
     *   not drawn on the rare frame it isn't (e.g. the very last snapshot
     *   before the target's ship state expires).</li>
     *   <li><b>Victim side</b> ({@link #myTargetedByMissileLock}) — drawn
     *   over this player's <em>own</em> ship instead, at
     *   {@link #myRenderScreenX}/{@link #myRenderScreenY} (cached by
     *   {@link #drawLocalShip()} this same frame), so the targeted player
     *   sees the same lock building on themselves that their attacker sees —
     *   added after the first pass only showed the attacker's side, per
     *   direct user feedback ("the targeted player is totally unaware...
     *   which seems unfair").</li>
     * </ul>
     * Both cases can be true in the same frame (locking one enemy while
     * being locked by another) — they're independent draws, not mutually
     * exclusive.
     */
    private void drawMissileLockReticle() {
        if (myMissileLockTargetPlayerId != ShipState.NO_MISSILE_LOCK_TARGET) {
            RemoteShip target = ships.get(myMissileLockTargetPlayerId);
            if (target != null) {
                float baseSizePixels = ShipStats.forType(target.shipType).getRadiusMeters() * 2f
                    * PhysicsConstants.PIXELS_PER_METER * MISSILE_LOCK_RETICLE_SCALE;
                boolean missileInFlight = hasInFlightMissileAt(myMissileLockTargetPlayerId, true);
                drawMissileLockReticleStages(target.renderX, target.renderY, baseSizePixels,
                    myMissileLockAcquired, missileInFlight);
            }
        }

        if (myTargetedByMissileLock && myBody != null) {
            float baseSizePixels = ShipStats.forType(myShipType).getRadiusMeters() * 2f
                * PhysicsConstants.PIXELS_PER_METER * MISSILE_LOCK_RETICLE_SCALE;
            // Any owner, not just myPlayerId - the victim cares whether *someone's* missile is
            // inbound, not who fired it.
            boolean missileInFlight = hasInFlightMissileAt(myPlayerId, false);
            drawMissileLockReticleStages(myRenderScreenX, myRenderScreenY, baseSizePixels,
                myTargetedByMissileLockAcquired, missileInFlight);
        }
    }

    /**
     * Draws the outer/inner/center reticle stages at one screen position,
     * shared by both {@link #drawMissileLockReticle}'s attacker and victim
     * cases so the animation math (sine-wave pulse, constant-rate rotation)
     * lives in exactly one place.
     *
     * @param x              screen X to center every stage on
     * @param y              screen Y to center every stage on
     * @param baseSizePixels the un-pulsed on-screen size, common to all three stages
     * @param acquired       whether to also draw the inner ring (lock fully acquired)
     * @param missileInFlight whether to also draw the static center mark
     */
    private void drawMissileLockReticleStages(float x, float y, float baseSizePixels,
                                               boolean acquired, boolean missileInFlight) {
        float pulseScale = MISSILE_LOCK_RETICLE_PULSE_MIN_SCALE + MISSILE_LOCK_RETICLE_PULSE_AMPLITUDE
            + MISSILE_LOCK_RETICLE_PULSE_AMPLITUDE * MathUtils.sin(missileReticleAnimationSeconds * MISSILE_LOCK_RETICLE_PULSE_RADIANS_PER_SECOND);
        drawReticleStage(missileLockReticleOuterRegion, x, y, baseSizePixels * pulseScale, 0f);

        if (acquired) {
            // Clockwise, per design.md — missiles: libGDX's positive rotation is counter-clockwise,
            // so a clockwise spin needs a decrementing angle.
            float innerRotationDegrees = -missileReticleAnimationSeconds * MISSILE_LOCK_RETICLE_INNER_ROTATION_DEGREES_PER_SECOND;
            drawReticleStage(missileLockReticleInnerRegion, x, y, baseSizePixels, innerRotationDegrees);
        }

        if (missileInFlight) {
            drawReticleStage(missileLockReticleCenterRegion, x, y, baseSizePixels, 0f);
        }
    }

    /**
     * Returns whether a live in-flight missile is currently tracking
     * {@code targetPlayerId}.
     *
     * @param targetPlayerId the tracked target's player id to look for
     * @param ownedByMeOnly  {@code true} to only count missiles this player fired themselves
     *                       (the attacker's own stage-3 check); {@code false} to count any
     *                       owner's (the victim's — they care whether one is inbound at all,
     *                       not who fired it)
     * @return {@code true} if a matching in-flight missile exists
     */
    private boolean hasInFlightMissileAt(int targetPlayerId, boolean ownedByMeOnly) {
        for (RemoteProjectile projectile : projectiles.values()) {
            if (projectile.trackedTargetPlayerId != targetPlayerId) {
                continue;
            }
            if (ownedByMeOnly && projectile.ownerPlayerId != myPlayerId) {
                continue;
            }
            return true;
        }
        return false;
    }

    private void drawReticleStage(TextureRegion region, float x, float y, float sizePixels, float rotationDegrees) {
        batch.draw(region,
            x - sizePixels / 2f, y - sizePixels / 2f,
            sizePixels / 2f, sizePixels / 2f,
            sizePixels, sizePixels,
            1f, 1f,
            rotationDegrees);
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
        // Temporary diagnostic timing while investigating an intermittent screen-transition
        // pause (CLAUDE.md) - root-caused to NetworkClient#stop()'s underlying OS socket
        // teardown (see that method's Javadoc); networkClient.stopAsync() below is the fix,
        // this timing stays to confirm dispose() itself is fast now.
        long disposeStartMillis = System.currentTimeMillis();

        if (networkClient != null) {
            networkClient.stopAsync();
        }
        if (localWorld != null) {
            localWorld.dispose();
        }
        // Every engine-loop Sound instance below is AssetManager-owned (shared, outlives this
        // screen) - without explicitly stopping each loop() instance here, it would otherwise
        // keep looping in the background forever after this screen is gone (design.md — engine sound).
        stopLocalEngineSound();
        stopMissileLockSound();
        for (RemoteShip ship : ships.values()) {
            ship.stopEngineSound();
        }
        batch.dispose();
        // shipsAtlas/projectilesAtlas/asteroidsAtlas/powerupsAtlas/minesAtlas/warningBannerTexture, and statusHud/powerHud's textures, are
        // owned by StarWarsGame#getAssets() (design.md - asset loading), not this screen -
        // disposed once, at app shutdown, not here. background.dispose() below still frees the
        // procedurally-generated starfield layer, which this screen alone owns (see #show()).
        background.dispose();
        scoreboardHud.dispose();
        radarHud.dispose();
        displayNameFont.dispose();

        Gdx.app.log(TAG, "dispose() took " + (System.currentTimeMillis() - disposeStartMillis) + "ms total");
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
        /** This ship's own engine thrusters (design.md — engine particle effects), built once at creation - empty for a ship type with none configured. */
        final List<EngineThruster> thrusters;
        /** This ship's own positioning lights (design.md — positioning lights), built once at creation - empty for a ship type with none configured. */
        final List<ShipLight> lights;
        /** This ship's own damage smoke points (design.md — damage smoke), built once at creation - empty for a ship type with none configured. */
        final List<DamageSmokePoint> damageSmoke;
        /** This ship's own radar pulse "energy wave" (design.md 2.14's rendering addendum), built once at creation - every ship type gets one, unlike thrusters/lights/damage smoke. */
        final OneShotParticleEffect radarPulseEffect;
        /** Whether this ship is currently holding its forward-thrust input, straight from the latest {@code ShipState} - not extrapolated, just held. */
        boolean thrusting;
        // Hull current/max, straight from the latest ShipState - not extrapolated, just held,
        // same as turretAimAngles/thrusting above - drives damageSmoke's threshold checks.
        float hullCurrent;
        float hullMax;
        /** The last-known radar pulse cooldown, seeded at creation (see the call site) so the very first sighting of this ship can't spuriously trigger {@link #radarPulseEffect}. */
        float radarPulseCooldownRemaining;
        /**
         * This ship's own continuous engine-loop sound (design.md — engine
         * sound), one long-lived {@code loop()} instance started at creation
         * and stopped only when this ship is removed from {@code ships} -
         * {@code null} if this ship type has no engine sound loaded.
         */
        final Sound engineSound;
        /** The specific {@code loop()} instance id of {@link #engineSound} - see {@link Sound#setVolume(long, float)}/{@link Sound#stop(long)}. */
        final long engineSoundId;
        /** This ship's own engine-loop volume fraction, eased toward 0/1 by {@link #updateRemoteEngineSounds} - see {@link EngineAudioMath#approachFraction}. */
        float engineVolumeFraction;

        RemoteShip(float x, float y, float angle, ShipType shipType, List<EngineThruster> thrusters,
                   List<ShipLight> lights, List<DamageSmokePoint> damageSmoke,
                   OneShotParticleEffect radarPulseEffect, float radarPulseCooldownRemaining,
                   Sound engineSound, long engineSoundId) {
            this.shipType = shipType;
            this.thrusters = thrusters;
            this.lights = lights;
            this.damageSmoke = damageSmoke;
            this.radarPulseEffect = radarPulseEffect;
            this.radarPulseCooldownRemaining = radarPulseCooldownRemaining;
            this.engineSound = engineSound;
            this.engineSoundId = engineSoundId;
            baseX = renderX = x;
            baseY = renderY = y;
            baseAngle = renderAngle = angle;
        }

        /**
         * Stops this ship's {@link #engineSound} loop instance, if any -
         * called from every {@code ships} map removal site so a shared,
         * {@code AssetManager}-owned {@link Sound} never keeps looping in
         * the background for a ship that no longer exists/is no longer
         * detected (design.md — engine sound).
         */
        void stopEngineSound() {
            if (engineSound != null) {
                engineSound.stop(engineSoundId);
            }
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
     * A mine (design.md — mines). Unlike every other remote entity in this
     * class, needs no dead reckoning at all — a mine is a permanently
     * stationary Box2D body (design.md — mines: {@code MineFactory}'s
     * {@code StaticBody}), so its position is simply whatever the last
     * snapshot reported, held as-is.
     */
    private static final class RemoteMine {
        final float x;
        final float y;

        RemoteMine(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    /**
     * An asteroid (design.md — asteroids). Rendered by dead reckoning, same
     * shape as {@link RemoteShip} — extrapolated using its own reported
     * velocity/angular velocity, both held constant between snapshots since
     * an asteroid never accelerates or experiences drag once spawned (the
     * user's own spec) — but with none of {@link RemoteShip}'s
     * player-ship-specific state (thrusters, lights, damage smoke, turrets,
     * hull) to carry.
     */
    private static final class RemoteAsteroid {
        final AsteroidType type;
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

        RemoteAsteroid(float x, float y, float angle, AsteroidType type) {
            this.type = type;
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
     * A power-up (design.md — power-ups). Rendered by dead reckoning, same
     * shape as {@link RemoteAsteroid} — extrapolated using its own reported
     * velocity/angular velocity, held constant between snapshots since a
     * power-up never accelerates or experiences drag once in motion, same
     * as an asteroid. Also carries its own private {@link PowerUpEffect}
     * instance (created once, the first time this power-up's id is seen —
     * see {@link #createPowerUpEffect}), unlike {@link RemoteAsteroid},
     * which has no particle glow to track.
     */
    private static final class RemotePowerUp {
        final PowerUpType type;
        final PowerUpEffect effect;
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

        RemotePowerUp(float x, float y, float angle, PowerUpType type, PowerUpEffect effect) {
            this.type = type;
            this.effect = effect;
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
     * Pairs one {@link ThrusterEffect} with the local-frame pixel offset
     * (from the ship's own {@code "ENGINE"} attachment point metadata) it
     * should be positioned at each frame — see {@link #myThrusters}.
     */
    private static final class EngineThruster {
        final PixelPoint attachmentPoint;
        final ThrusterEffect effect;

        EngineThruster(PixelPoint attachmentPoint, ThrusterEffect effect) {
            this.attachmentPoint = attachmentPoint;
            this.effect = effect;
        }
    }

    /**
     * Pairs one {@link ShipLightEffect} with the local-frame pixel offset
     * (from the ship's own {@code "LIGHT_RED"}/{@code "LIGHT_GREEN"}
     * attachment point metadata) it should be positioned at each frame —
     * see {@link #myLights}.
     */
    private static final class ShipLight {
        final PixelPoint attachmentPoint;
        final ShipLightEffect effect;

        ShipLight(PixelPoint attachmentPoint, ShipLightEffect effect) {
            this.attachmentPoint = attachmentPoint;
            this.effect = effect;
        }
    }

    /**
     * Pairs one {@link DamageSmokeEffect} with the local-frame pixel offset
     * (from the ship's own {@code "DAMAGE_SMOKE"} attachment point
     * metadata) it should be positioned at each frame, and the hull-damage
     * fraction beyond which it should be active — see
     * {@link #buildDamageSmokePoints}/{@link #myDamageSmoke}.
     */
    private static final class DamageSmokePoint {
        final PixelPoint attachmentPoint;
        final float damageThresholdFraction;
        final DamageSmokeEffect effect;

        DamageSmokePoint(PixelPoint attachmentPoint, float damageThresholdFraction, DamageSmokeEffect effect) {
            this.attachmentPoint = attachmentPoint;
            this.damageThresholdFraction = damageThresholdFraction;
            this.effect = effect;
        }
    }

    /**
     * Pairs one {@link MuzzleFlashEffect} with the local-frame pixel offset
     * (from the ship's own {@code "PROJECTILE"} attachment point metadata)
     * it should fire from — see {@link #buildMuzzleFlashPoints}/
     * {@link #myMuzzleFlashes}.
     */
    private static final class MuzzleFlashPoint {
        final PixelPoint attachmentPoint;
        final MuzzleFlashEffect effect;

        MuzzleFlashPoint(PixelPoint attachmentPoint, MuzzleFlashEffect effect) {
            this.attachmentPoint = attachmentPoint;
            this.effect = effect;
        }
    }

    /**
     * Pairs one {@link OneShotParticleEffect} with the fixed world position
     * it was last {@link OneShotParticleEffect#trigger() triggered} at —
     * see {@link #hitExplosionPool}/{@link #shipExplosionPool}. Unlike
     * {@link EngineThruster}/{@link ShipLight}/{@link MuzzleFlashPoint},
     * this pairing isn't a fixed per-ship attachment offset — {@code x}/
     * {@code y} are reassigned by {@link #triggerPooledExplosion} every
     * time this entry gets reused for a new explosion.
     */
    private static final class PositionedOneShotEffect {
        final OneShotParticleEffect effect;
        float x;
        float y;

        PositionedOneShotEffect(OneShotParticleEffect effect) {
            this.effect = effect;
        }
    }

    /**
     * A projectile (anyone's, including the local player's own). Rendered by
     * dead reckoning, same reasoning and same shape as {@link RemoteShip} —
     * extrapolated forward using its actual reported world-frame velocity
     * (muzzle speed plus whatever velocity the firing ship had, {@code
     * ProjectileState}/{@code ProjectileFactory}), <b>not</b> a fixed weapon
     * speed along its facing angle: a fast-moving shooter's own velocity
     * measurably changes a shot's true speed, and assuming bare muzzle speed
     * under-extrapolated it — most visibly as a newly-fired projectile
     * appearing to spawn behind its ship's own attachment point, worse the
     * faster the ship was moving (design.md 2.4's addendum).
     */
    private static final class RemoteProjectile {
        final int ownerPlayerId;
        /**
         * The enemy player id this projectile is tracking (a missile), or
         * {@link ProjectileComponent#NO_TRACKED_TARGET} for an ordinary
         * blaster bolt (design.md — missiles) — fixed at construction, drives
         * both which sprite to draw ({@link #drawProjectile}) and the
         * lock-reticle's "in flight" stage ({@link #drawMissileLockReticle}).
         */
        final int trackedTargetPlayerId;
        /**
         * This object's own true spawn position — set once, in the
         * constructor, and never touched again (unlike {@link #baseX}/
         * {@link #baseY}, which move to each new snapshot). Used purely as a
         * stable identity for {@link #takeMatchingPredicted} to match a
         * locally-predicted shot against its later server confirmation by
         * spawn-to-spawn distance — comparing the *current*, already-
         * extrapolated {@link #renderX}/{@link #renderY} instead would grow
         * with however long the shot has already been flying (round-trip
         * latency × velocity), making a fixed match-distance threshold
         * meaningless.
         */
        final float spawnX;
        final float spawnY;
        float baseX;
        float baseY;
        float velocityX;
        float velocityY;
        float elapsedSinceUpdate;
        float renderX;
        float renderY;
        /**
         * True for exactly one {@link #extrapolate} call: the first one after
         * this projectile is created. That call's {@code deltaTime} covers the
         * interval since the *previous* render frame — before this projectile
         * existed — so applying it would advance the projectile's very first
         * visible position by up to one frame's worth of travel past its true
         * spawn point (already correct in {@link #renderX}/{@link #renderY},
         * set directly from the snapshot in the constructor) for no reason.
         * Confirmed live: a user-provided screenshot marking exactly where a
         * shot first became visible measured it consistently past its
         * ship-type's authored {@code PROJECTILE} attachment point by close to
         * this margin (design.md 2.4's addendum).
         */
        boolean skipNextExtrapolate = true;

        RemoteProjectile(int ownerPlayerId, float x, float y) {
            this(ownerPlayerId, x, y, ProjectileComponent.NO_TRACKED_TARGET);
        }

        RemoteProjectile(int ownerPlayerId, float x, float y, int trackedTargetPlayerId) {
            this.ownerPlayerId = ownerPlayerId;
            this.trackedTargetPlayerId = trackedTargetPlayerId;
            spawnX = baseX = renderX = x;
            spawnY = baseY = renderY = y;
        }

        /**
         * Updates this projectile's known base position/velocity from a fresh
         * snapshot (or, for a just-adopted predicted shot, from its first-ever
         * real confirmation).
         *
         * @param x                      new base X, in screen pixels
         * @param y                      new base Y, in screen pixels
         * @param velocityX              new velocity, in screen pixels/second
         * @param velocityY              new velocity, in screen pixels/second
         * @param startingElapsedSeconds what {@link #elapsedSinceUpdate} should
         *                               resume from, instead of the usual zero
         *                               — see {@link #updateFromSnapshot(float, float, float, float)}'s
         *                               Javadoc for why a plain snapshot update
         *                               and a predicted-shot adoption need
         *                               different values here
         */
        void updateFromSnapshot(float x, float y, float velocityX, float velocityY, float startingElapsedSeconds) {
            baseX = x;
            baseY = y;
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            elapsedSinceUpdate = startingElapsedSeconds;
        }

        /**
         * Updates this projectile's known base position/velocity from an
         * ordinary fresh snapshot of an already-confirmed projectile —
         * {@link #elapsedSinceUpdate} resets to zero, since {@code x}/{@code y}
         * are "now" as far as this object's own render state is concerned.
         * <p>
         * <b>Not used when {@link #takeMatchingPredicted} adopts a predicted
         * shot into its first real confirmation</b> — that path calls
         * {@link #updateFromSnapshot(float, float, float, float, float)}
         * directly with the predicted object's own already-accumulated
         * {@link #elapsedSinceUpdate} instead of zero. Resetting to zero there
         * would snap {@link #renderX}/{@link #renderY} backward: the confirmed
         * {@code ProjectileState} reports this shot's position as of the
         * server tick that created it, which by the time it's received is
         * already however-long-ago (design.md 2.4's addendum, "round-trip
         * latency" — the whole reason local shot prediction exists), while
         * this object's current, already-rendering position reflects that
         * same shot's true elapsed flight time. Seeding with that real elapsed
         * time instead of resetting keeps the two aligned, the same
         * "align two independently-integrating estimates of the same thing by
         * using the same real-world duration for both" trick already used by
         * {@code Client#reconcileWithServer} — not a network-staleness
         * estimate.
         *
         * @param x         new base X, in screen pixels
         * @param y         new base Y, in screen pixels
         * @param velocityX new velocity, in screen pixels/second
         * @param velocityY new velocity, in screen pixels/second
         */
        void updateFromSnapshot(float x, float y, float velocityX, float velocityY) {
            updateFromSnapshot(x, y, velocityX, velocityY, 0f);
        }

        void extrapolate(float deltaTime) {
            if (skipNextExtrapolate) {
                skipNextExtrapolate = false;
                return;
            }
            elapsedSinceUpdate += deltaTime;
            renderX = baseX + velocityX * elapsedSinceUpdate;
            renderY = baseY + velocityY * elapsedSinceUpdate;
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
