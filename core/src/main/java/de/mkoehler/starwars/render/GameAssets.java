package de.mkoehler.starwars.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import de.mkoehler.starwars.sim.ShipStats;
import de.mkoehler.starwars.sim.ShipType;

/**
 * Every shared texture atlas/texture this app ever needs, as classpath
 * paths, plus {@link #queueAll} to queue them all into an
 * {@link AssetManager} — the single source of truth for "what art does this
 * game load," read by {@link de.mkoehler.starwars.SplashScreen} (which
 * queues everything up front, once) and by every later screen/HUD widget,
 * which only ever call {@link AssetManager#get} against paths already
 * resident from that one load rather than constructing/disposing their own
 * {@link Texture}/{@link TextureAtlas} instances directly on every screen
 * transition.
 * <p>
 * Deliberately excludes a few asset-shaped things that stay outside the
 * {@link AssetManager} on purpose: {@link GameFonts}-generated
 * {@code BitmapFont}s (cheap to (re)generate, and each caller needs its own
 * disposable instance at its own pixel size — not worth the extra
 * {@code FreetypeFontLoader} plumbing for something this fast); the Connect
 * Screen's {@code audio/StarWarsTheme.mp3} (streamed {@code Music}, loaded
 * exactly once, not a repeated-load concern); and
 * {@link PlaceholderStarfield}'s generated texture (procedural, not a file
 * at all — see its own Javadoc for why it's still a placeholder).
 */
public final class GameAssets {

    public static final String MENU_ATLAS = "textures/menu.atlas";
    public static final String SHIPS_ATLAS = "textures/ships.atlas";
    public static final String PROJECTILES_ATLAS = "textures/projectiles.atlas";
    public static final String ASTEROIDS_ATLAS = "textures/asteroids.atlas";
    public static final String POWERUPS_ATLAS = "textures/powerups.atlas";
    public static final String MINES_ATLAS = "textures/mines.atlas";

    public static final String LOGO = "textures/menu/logo.png";
    public static final String MENU_STARFIELD = "textures/backgrounds/menu_starfield.png";
    public static final String BLUE_NEBULA = "textures/backgrounds/blue_nebula.png";
    public static final String ARENA_BOUNDARY = "textures/backgrounds/arena_boundary.png";
    public static final String WARNING_BANNER = "textures/hud/hud_warning_ejection_locked.png";
    public static final String HUD_STATUS_BACKGROUND = "textures/hud/hud_status_background.png";
    public static final String HUD_STATUS_SHIELD = "textures/hud/hud_status_shield.png";
    public static final String HUD_DISTRIBUTION_BACKGROUND = "textures/hud/hud_distribution_background.png";
    public static final String HUD_DISTRIBUTION_SHIELD = "textures/hud/hud_distribution_shield.png";
    public static final String HUD_DISTRIBUTION_WEAPONS = "textures/hud/hud_distribution_weapons.png";
    public static final String HUD_DISTRIBUTION_ENGINE = "textures/hud/hud_distribution_engine.png";
    public static final String SCOREBOARD_PANEL = "textures/hud/scoreboard.png";
    public static final String AFTER_DEATH_DIALOG_BACKGROUND = "textures/after_death/Dialog_Background.png";
    public static final String KEYBINDS_BACKGROUND = "textures/hud/hud_keybinds_background.png";
    public static final String AUDIO_SETTINGS_BACKGROUND = "textures/hud/hud_audio_settings_background.png";

    public static final String RADAR_BACKGROUND = "textures/hud/hud_radar_background.png";
    public static final String RADAR_RING = "textures/hud/hud_radar_ring.png";
    public static final String RADAR_CONE = "textures/hud/hud_radar_cone.png";
    public static final String RADAR_BLIP = "textures/hud/hud_radar_blip.png";
    public static final String RADAR_CHEVRON = "textures/hud/hud_radar_chevron.png";
    public static final String RADAR_INDICATOR_GREEN = "textures/hud/hud_radar_indicator_green.png";
    public static final String RADAR_INDICATOR_RED = "textures/hud/hud_radar_indicator_red.png";

    /**
     * The positioning-light particle effects (design.md — positioning
     * lights) — one shared red and one shared green effect for every ship
     * type's {@code "LIGHT_RED"}/{@code "LIGHT_GREEN"} attachment points,
     * unlike a ship's own configurable engine effect
     * ({@link de.mkoehler.starwars.sim.ShipTypeConfig#getEngineParticleEffect()}).
     */
    public static final String LIGHT_RED_PARTICLE = particleEffectPath("light_red");
    public static final String LIGHT_GREEN_PARTICLE = particleEffectPath("light_green");

    /**
     * The damage smoke particle effect (design.md — damage smoke) — one
     * shared effect for every ship type's {@code "DAMAGE_SMOKE"} attachment
     * points, same "one global template regardless of ship type" reasoning
     * as {@link #LIGHT_RED_PARTICLE}/{@link #LIGHT_GREEN_PARTICLE}.
     */
    public static final String DAMAGE_SMOKE_PARTICLE = particleEffectPath("smoke");

    /**
     * The radar pulse "energy wave" particle effect (design.md 2.14's
     * rendering addendum) — a one-shot (non-looping) effect played once
     * each time a ship's active radar pulse actually fires, same
     * one-shared-template-for-every-ship-type reasoning as the positioning
     * lights/damage smoke above.
     */
    public static final String RADAR_PULSE_PARTICLE = particleEffectPath("radar_pulse");

    /**
     * The muzzle flash particle effect (design.md — muzzle flash) — a
     * one-shot (non-looping) effect played once each time a shot is fired
     * from a ship's own {@code "PROJECTILE"} attachment point(s), same
     * one-shared-template-for-every-ship-type reasoning as the radar pulse
     * wave/positioning lights/damage smoke above.
     */
    public static final String MUZZLE_FLASH_PARTICLE = particleEffectPath("muzzle_flash");

    /**
     * The small impact explosion particle effect (design.md — explosions)
     * — a one-shot effect played at the contact point whenever a
     * projectile actually hits a ship (see
     * {@code de.mkoehler.starwars.net.messages.ProjectileHitMessage}), same
     * one-shared-template-for-every-ship-type reasoning as every other
     * one-shot effect above.
     */
    public static final String EXPLOSION_SMALL_PARTICLE = particleEffectPath("explosion_small");
    /**
     * The full ship-destruction explosion particle effect (design.md —
     * explosions) — a one-shot effect played centered on a ship's own
     * position whenever it's destroyed (see {@code ShipDestroyedMessage}).
     */
    public static final String EXPLOSION_PARTICLE = particleEffectPath("explosion");

    /**
     * The power-up glow particle effect (design.md — power-ups) — one shared
     * effect for every {@code PowerUpType}, same one-shared-template
     * reasoning as {@link #LIGHT_RED_PARTICLE}/{@link #DAMAGE_SMOKE_PARTICLE}.
     * Unlike those, it's continuous but not attached to a ship at all — a
     * standalone world pickup.
     */
    public static final String POWERUP_PARTICLE = particleEffectPath("powerup");

    /**
     * The ship-destruction explosion sound (design.md — explosions' audio
     * addendum) — one shared clip played whenever any ship is destroyed
     * (see {@code ShipDestroyedMessage}), same one-shared-clip-for-every-
     * ship-type reasoning as {@link #TURRET_SOUND}/{@link #MISSILE_LAUNCH_SOUND}.
     * Falls under the "Sound Effects" volume category (design.md — audio
     * settings), not "Weapons" — an explosion isn't a weapon firing.
     */
    public static final String EXPLOSION_SOUND = "audio/effects/explosion.mp3";

    /**
     * Four interchangeable ship-impact "thud" clips (design.md — impact
     * sounds) — played whenever a ship physically collides with the
     * arena boundary, an asteroid, or another ship (see
     * {@code de.mkoehler.starwars.net.messages.ShipImpactMessage}), one
     * picked at random independently by each client every time. Falls
     * under the "Sound Effects" volume category, same as
     * {@link #EXPLOSION_SOUND}/{@link #POWERUP_PICKUP_SOUND} — a physical
     * bump isn't a weapon firing.
     */
    public static final String[] IMPACT_SOUNDS = {
        "audio/effects/impact1.mp3",
        "audio/effects/impact2.mp3",
        "audio/effects/impact3.mp3",
        "audio/effects/impact4.mp3",
    };

    /**
     * The power-up pickup sound (design.md — power-ups' audio addendum)
     * — played whenever a ship picks up a power-up (see
     * {@code de.mkoehler.starwars.net.messages.PowerUpPickedUpMessage}).
     * "Sound Effects" volume category, same as {@link #EXPLOSION_SOUND}.
     */
    public static final String POWERUP_PICKUP_SOUND = "audio/effects/powerup_pickup.mp3";

    /** How many {@code textures/after_death/Quote_<n>.png} images exist (design.md — authored by the user). */
    public static final int AFTER_DEATH_QUOTE_COUNT = 23;

    private GameAssets() {
    }

    /**
     * Returns the classpath path of {@code type}'s HUD hull silhouette
     * texture (design.md 2.6/2.7). Not guaranteed to exist on disk for
     * every {@link ShipType} — {@link #queueAll} only queues it when it
     * does, and {@link ShipStatusHud} falls back to the X-wing's own art
     * for any type it doesn't find loaded.
     *
     * @param type the ship type
     * @return the texture's classpath path
     */
    public static String shipHullTexturePath(ShipType type) {
        return "textures/hud/" + type.getResourceName() + "_hull.png";
    }

    /**
     * The one-shot missile-launch sound (design.md — missiles' audio
     * addendum) — a single shared clip for every ship type's missile, same
     * "one global template regardless of ship type" reasoning as the
     * particle effects above.
     */
    public static final String MISSILE_LAUNCH_SOUND = "audio/weapons/missile_launch.mp3";
    /**
     * The one-shot turret-fire sound (design.md — turret weapons' audio
     * addendum) — a single shared clip for every turret mount on every
     * turret-equipped ship type (currently the Falcon/Star Destroyer),
     * distinct from each ship's own main-gun {@link #weaponSoundPath}.
     */
    public static final String TURRET_SOUND = "audio/weapons/turret_shooting.mp3";
    /**
     * The looping missile-lock-acquisition sound (design.md — missiles'
     * audio addendum) — played for as long as the local player's own
     * missile lock is actively trying to acquire a target (within the 5s
     * acquisition window, not yet locked), replaced by
     * {@link #MISSILE_LOCK_ACQUIRED_SOUND} the instant it succeeds.
     */
    public static final String MISSILE_LOCK_TRYING_SOUND = "audio/weapons/missile_lock_trying.mp3";
    /**
     * The looping missile-lock-acquired sound (design.md — missiles' audio
     * addendum) — played for as long as the local player's own missile
     * lock is fully acquired and ready to fire, replacing
     * {@link #MISSILE_LOCK_TRYING_SOUND} rather than layering on top of it.
     */
    public static final String MISSILE_LOCK_ACQUIRED_SOUND = "audio/weapons/missile_lock_acquired.mp3";

    /**
     * Returns the classpath path of {@code type}'s continuous engine-loop
     * sound (design.md — engine sound), loaded as a {@link Sound} (not
     * {@link com.badlogic.gdx.audio.Music}) so {@code Client} can drive one
     * long-lived {@code loop()} instance per ship and fade its volume via
     * {@code setVolume(long, float)} without ever restarting playback.
     *
     * @param type the ship type
     * @return the sound's classpath path
     */
    public static String engineSoundPath(ShipType type) {
        return "audio/engines/" + type.getResourceName() + "_engine_loop.mp3";
    }

    /**
     * Returns the classpath path of {@code type}'s one-shot main-gun firing
     * sound (design.md — weapon sound), played once per firing volley
     * regardless of how many {@code "PROJECTILE"} attachment points fire
     * together. The TIE Fighter and TIE Interceptor share one underlying
     * clip (two identical files, one per {@link ShipType#getResourceName()})
     * rather than needing a lookup exception here.
     *
     * @param type the ship type
     * @return the sound's classpath path
     */
    public static String weaponSoundPath(ShipType type) {
        return "audio/weapons/" + type.getResourceName() + "_shooting.mp3";
    }

    /**
     * Returns the classpath path of one Death Screen quote image
     * (design.md 5.1).
     *
     * @param oneBasedIndex a quote number in {@code [1, AFTER_DEATH_QUOTE_COUNT]}
     * @return the texture's classpath path
     */
    public static String afterDeathQuotePath(int oneBasedIndex) {
        return "textures/after_death/Quote_" + oneBasedIndex + ".png";
    }

    /**
     * Returns the classpath path of a particle effect resource (design.md —
     * engine particle effects), e.g. {@code "thruster_blue"} →
     * {@code "textures/particles/thruster_blue.p"}. The effect's own image
     * (referenced by filename inside the {@code .p} file itself, e.g.
     * {@code particle-fire.png}) is expected to live alongside it in the
     * same directory — {@link ParticleEffect}'s default loading behavior
     * when no atlas/images-directory is explicitly given.
     *
     * @param name the effect's resource name, see {@link de.mkoehler.starwars.sim.ShipTypeConfig#getEngineParticleEffect()}
     * @return the effect's classpath path
     */
    public static String particleEffectPath(String name) {
        return "textures/particles/" + name + ".p";
    }

    /**
     * Queues every asset above into {@code manager} for asynchronous
     * loading — called exactly once, by
     * {@link de.mkoehler.starwars.SplashScreen#show()}, before any other
     * screen is ever shown.
     *
     * @param manager the asset manager to queue into
     */
    public static void queueAll(AssetManager manager) {
        manager.load(MENU_ATLAS, TextureAtlas.class);
        manager.load(SHIPS_ATLAS, TextureAtlas.class);
        manager.load(PROJECTILES_ATLAS, TextureAtlas.class);
        manager.load(ASTEROIDS_ATLAS, TextureAtlas.class);
        manager.load(POWERUPS_ATLAS, TextureAtlas.class);
        manager.load(MINES_ATLAS, TextureAtlas.class);

        manager.load(LOGO, Texture.class);
        manager.load(MENU_STARFIELD, Texture.class);
        manager.load(BLUE_NEBULA, Texture.class);
        manager.load(ARENA_BOUNDARY, Texture.class);
        manager.load(WARNING_BANNER, Texture.class);
        manager.load(HUD_STATUS_BACKGROUND, Texture.class);
        manager.load(HUD_STATUS_SHIELD, Texture.class);
        manager.load(HUD_DISTRIBUTION_BACKGROUND, Texture.class);
        manager.load(HUD_DISTRIBUTION_SHIELD, Texture.class);
        manager.load(HUD_DISTRIBUTION_WEAPONS, Texture.class);
        manager.load(HUD_DISTRIBUTION_ENGINE, Texture.class);
        manager.load(SCOREBOARD_PANEL, Texture.class);
        manager.load(AFTER_DEATH_DIALOG_BACKGROUND, Texture.class);
        manager.load(KEYBINDS_BACKGROUND, Texture.class);
        manager.load(AUDIO_SETTINGS_BACKGROUND, Texture.class);
        manager.load(RADAR_BACKGROUND, Texture.class);
        manager.load(RADAR_RING, Texture.class);
        manager.load(RADAR_CONE, Texture.class);
        manager.load(RADAR_BLIP, Texture.class);
        manager.load(RADAR_CHEVRON, Texture.class);
        manager.load(RADAR_INDICATOR_GREEN, Texture.class);
        manager.load(RADAR_INDICATOR_RED, Texture.class);
        manager.load(LIGHT_RED_PARTICLE, ParticleEffect.class);
        manager.load(LIGHT_GREEN_PARTICLE, ParticleEffect.class);
        manager.load(DAMAGE_SMOKE_PARTICLE, ParticleEffect.class);
        manager.load(RADAR_PULSE_PARTICLE, ParticleEffect.class);
        manager.load(MUZZLE_FLASH_PARTICLE, ParticleEffect.class);
        manager.load(EXPLOSION_SMALL_PARTICLE, ParticleEffect.class);
        manager.load(EXPLOSION_PARTICLE, ParticleEffect.class);
        manager.load(POWERUP_PARTICLE, ParticleEffect.class);
        manager.load(MISSILE_LAUNCH_SOUND, Sound.class);
        manager.load(TURRET_SOUND, Sound.class);
        manager.load(EXPLOSION_SOUND, Sound.class);
        for (String impactSound : IMPACT_SOUNDS) {
            manager.load(impactSound, Sound.class);
        }
        manager.load(POWERUP_PICKUP_SOUND, Sound.class);
        manager.load(MISSILE_LOCK_TRYING_SOUND, Sound.class);
        manager.load(MISSILE_LOCK_ACQUIRED_SOUND, Sound.class);

        for (ShipType type : ShipType.values()) {
            String path = shipHullTexturePath(type);
            if (Gdx.files.internal(path).exists()) {
                manager.load(path, Texture.class);
            }
            ShipStats.forType(type).getEngineParticleEffect().ifPresent(name -> {
                String effectPath = particleEffectPath(name);
                if (Gdx.files.internal(effectPath).exists()) {
                    manager.load(effectPath, ParticleEffect.class);
                }
            });
            String enginePath = engineSoundPath(type);
            if (Gdx.files.internal(enginePath).exists()) {
                manager.load(enginePath, Sound.class);
            }
            String weaponPath = weaponSoundPath(type);
            if (Gdx.files.internal(weaponPath).exists()) {
                manager.load(weaponPath, Sound.class);
            }
        }
        for (int i = 1; i <= AFTER_DEATH_QUOTE_COUNT; i++) {
            manager.load(afterDeathQuotePath(i), Texture.class);
        }
    }
}
