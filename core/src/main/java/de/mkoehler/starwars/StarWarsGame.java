package de.mkoehler.starwars;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.audio.Music;
import de.mkoehler.starwars.input.KeyBindings;
import de.mkoehler.starwars.remote.RemoteControlQueue;
import de.mkoehler.starwars.render.GameAssets;
import de.mkoehler.starwars.render.QuoteDeck;

import java.util.Random;

/**
 * The actual {@code ApplicationListener} for the desktop client, now that
 * there's more than one {@link com.badlogic.gdx.Screen} (design.md 5.1) —
 * {@link Client} (gameplay) previously filled this role directly back when
 * it was the only screen that existed.
 * <p>
 * {@link Game} takes care of delegating {@code render}/{@code resize}/
 * {@code pause}/{@code resume}/{@code dispose} to whichever {@link
 * com.badlogic.gdx.Screen} is current; switching screens (and disposing the
 * one being left) is each screen's own responsibility, not this class's —
 * see {@link ShipSelectionScreen}'s Start handling for where that happens.
 * <p>
 * Starts on {@link SplashScreen} (design.md — asset loading), which loads
 * every shared texture/atlas (see {@link #assetManager}/{@link #getAssets()})
 * once up front before handing off to {@link ConnectScreen} — logging into
 * a player account (design.md 3.6) is the first thing the player actually
 * interacts with, but not the first thing the game does anymore.
 * <p>
 * Also owns the pieces of state that need to survive across repeated screen
 * instances for the whole run of the app rather than living on any single
 * screen: {@link #assetManager} itself, so every screen after
 * {@link SplashScreen} reads already-resident assets instead of
 * loading/disposing its own copies of the same files on every transition
 * (design.md — asset loading; this is what actually used to cause a
 * noticeable pause switching screens, per CLAUDE.md); {@link #quoteDeck},
 * so {@link DeathScreen} doesn't repeat a quote until every other one has
 * been shown, across as many deaths (and therefore as many fresh
 * {@code DeathScreen}/{@code Client} instances) as happen in one sitting;
 * and {@link #fadingMusic} (see {@link #fadeOutAndDisposeMusic}), since
 * fading a track out takes real time that outlives whichever screen started
 * the fade — by the time it's fully faded, that screen has usually already
 * been disposed and replaced. {@link #keyBindings} (design.md 3.8) is the
 * same story again: loaded once here from the local keybinds file, then
 * shared by every screen that reads or edits a binding — {@link Client}
 * reads it every frame for gameplay input, {@link KeybindScreen} mutates it
 * (and re-saves immediately) when the player rebinds an action. Every other screen only ever needs a
 * {@link Game} reference to switch away from itself, but constructors are
 * typed to this concrete class instead so they can reach
 * {@link #getQuoteDeck()}/{@link #getAssets()}/{@link #fadeOutAndDisposeMusic}
 * too — there's only ever one {@code Game} implementation in this project,
 * so nothing is lost by not depending on the interface.
 */
public class StarWarsGame extends Game {

    /** How long a handed-off track takes to reach silence - untuned, first value that felt right. */
    private static final float MUSIC_FADE_OUT_SECONDS = 1.5f;

    private final QuoteDeck quoteDeck = new QuoteDeck(GameAssets.AFTER_DEATH_QUOTE_COUNT, new Random());
    private final AssetManager assetManager = new AssetManager();
    /**
     * Not loaded as a field initializer like {@link #assetManager}/{@link #quoteDeck} above -
     * {@link KeyBindings#load()} touches {@code Gdx.files}, which isn't set up yet at the point
     * this object is constructed (it's built as a constructor argument to
     * {@code Lwjgl3Application}, i.e. before that application backend has initialized any
     * {@code Gdx.*} statics) - loaded instead in {@link #create()}, the same lifecycle point
     * every other {@code Gdx.files}-touching code in this project already waits for.
     */
    private KeyBindings keyBindings;

    private Music fadingMusic;
    private float fadingMusicElapsedSeconds;

    @Override
    public void create() {
        keyBindings = KeyBindings.load();
        setScreen(new SplashScreen(this));
    }

    /**
     * Advances any in-progress {@link #fadeOutAndDisposeMusic} fade by one
     * frame, on top of the usual {@link Game#render()} delegation to the
     * current screen - overridden here (rather than done per-screen)
     * specifically so a fade keeps playing across a screen transition
     * instead of being cut off by whichever screen started it disposing
     * itself.
     * <p>
     * Also drains {@link RemoteControlQueue} first, before delegating to the
     * current screen — the embedded dev-only MCP server (design.md 3.13)
     * queues its actions there from its own thread(s), same reasoning as
     * every other cross-thread queue in this codebase.
     */
    @Override
    public void render() {
        RemoteControlQueue.drain();
        super.render();
        if (fadingMusic != null) {
            fadingMusicElapsedSeconds += Gdx.graphics.getDeltaTime();
            float volume = 1f - fadingMusicElapsedSeconds / MUSIC_FADE_OUT_SECONDS;
            if (volume <= 0f) {
                fadingMusic.stop();
                fadingMusic.dispose();
                fadingMusic = null;
            } else {
                fadingMusic.setVolume(volume);
            }
        }
    }

    /**
     * Returns the shared, session-local (never persisted) quote deck
     * {@link DeathScreen} draws from.
     *
     * @return the quote deck
     */
    public QuoteDeck getQuoteDeck() {
        return quoteDeck;
    }

    /**
     * Returns the app-wide {@link AssetManager} — populated once by
     * {@link SplashScreen} before any other screen is shown, then read
     * (never loaded/disposed piecemeal) by every screen/HUD widget that
     * needs a shared texture or atlas.
     *
     * @return the shared asset manager
     */
    public AssetManager getAssets() {
        return assetManager;
    }

    /**
     * Returns the player's live, shared keybinds (design.md 3.8) — loaded
     * once from the local keybinds file in {@link #create()}; every screen
     * that reads or edits a binding shares this exact instance rather than
     * loading/saving its own copy.
     *
     * @return the shared keybinds
     */
    public KeyBindings getKeyBindings() {
        return keyBindings;
    }

    /**
     * Disposes the current screen (via {@link Game#dispose() the inherited
     * behavior}, which only calls {@link com.badlogic.gdx.Screen#hide()})
     * and then {@link #assetManager} itself, freeing every texture/atlas it
     * holds — called once, when the application actually closes.
     */
    @Override
    public void dispose() {
        super.dispose();
        assetManager.dispose();
    }

    /**
     * Takes ownership of a still-playing {@link Music} track and fades it
     * out to silence over {@link #MUSIC_FADE_OUT_SECONDS}, disposing it once
     * silent - for a screen (e.g. {@link ConnectScreen}) that wants its
     * background music to keep playing, gradually quieting down, for a
     * moment after the player has already left it, rather than cutting off
     * mid-note the instant the screen itself is disposed.
     *
     * @param music the track to fade out and dispose; a no-op if {@code null}
     */
    public void fadeOutAndDisposeMusic(Music music) {
        if (music == null) {
            return;
        }
        // Only one fade is ever tracked at a time - if another was still in progress (shouldn't
        // normally happen, only one screen plays music right now), finish it immediately rather
        // than leaking its handle.
        if (fadingMusic != null) {
            fadingMusic.stop();
            fadingMusic.dispose();
        }
        fadingMusic = music;
        fadingMusicElapsedSeconds = 0f;
    }
}
