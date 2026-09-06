package de.mkoehler.starwars;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
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
 * Starts on {@link ConnectScreen} (design.md 5.1) — logging into a player
 * account is the very first thing the game does now that accounts
 * (design.md 3.6) exist.
 * <p>
 * Also owns the two pieces of state that need to survive across repeated
 * screen instances for the whole run of the app rather than living on any
 * single screen: {@link #quoteDeck}, so {@link DeathScreen} doesn't repeat
 * a quote until every other one has been shown, across as many deaths (and
 * therefore as many fresh {@code DeathScreen}/{@code Client} instances) as
 * happen in one sitting; and {@link #fadingMusic} (see
 * {@link #fadeOutAndDisposeMusic}), since fading a track out takes real
 * time that outlives whichever screen started the fade — by the time it's
 * fully faded, that screen has usually already been disposed and replaced.
 * Every other screen only ever needs a {@link Game} reference to switch
 * away from itself, but constructors are typed to this concrete class
 * instead so they can reach {@link #getQuoteDeck()}/
 * {@link #fadeOutAndDisposeMusic} too — there's only ever one {@code Game}
 * implementation in this project, so nothing is lost by not depending on
 * the interface.
 */
public class StarWarsGame extends Game {

    /** How long a handed-off track takes to reach silence - untuned, first value that felt right. */
    private static final float MUSIC_FADE_OUT_SECONDS = 1.5f;

    private final QuoteDeck quoteDeck = new QuoteDeck(DeathScreen.QUOTE_COUNT, new Random());

    private Music fadingMusic;
    private float fadingMusicElapsedSeconds;

    @Override
    public void create() {
        setScreen(new ConnectScreen(this));
    }

    /**
     * Advances any in-progress {@link #fadeOutAndDisposeMusic} fade by one
     * frame, on top of the usual {@link Game#render()} delegation to the
     * current screen - overridden here (rather than done per-screen)
     * specifically so a fade keeps playing across a screen transition
     * instead of being cut off by whichever screen started it disposing
     * itself.
     */
    @Override
    public void render() {
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
