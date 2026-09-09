package de.mkoehler.starwars.render;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;

/**
 * A generic non-looping, omnidirectional particle burst, played once each
 * time some discrete event occurs — a private copy of a shared template,
 * kicked off by {@link #trigger()} and centered on a moving point every
 * frame afterward via {@link #update} (useful whether or not the
 * underlying effect is itself authored {@code attached: true} — harmless
 * to keep repositioning an unattached effect too, since its own already-
 * spawned particles simply aren't dragged along).
 * <p>
 * Used for every one-shot effect in this project that doesn't also need a
 * directional rotation to match a shooter's facing (unlike
 * {@link MuzzleFlashEffect}) — currently the radar pulse "energy wave"
 * (design.md 2.14's rendering addendum) and both explosion sizes
 * (design.md — explosions), each just a different shared template. Also
 * unlike {@link ThrusterEffect}/{@link DamageSmokeEffect}, there's no on/off
 * *gating* tied to a held or persistent condition here — only a one-shot
 * restart each time the underlying event actually happens, and
 * {@link ParticleEffect#isComplete()} tells this wrapper when to stop
 * updating/drawing on its own once playback has finished.
 */
public class OneShotParticleEffect {

    private final ParticleEffect effect;
    private boolean playing;

    /**
     * Creates a one-shot effect from a shared template.
     *
     * @param template the particle effect template to copy
     */
    public OneShotParticleEffect(ParticleEffect template) {
        this.effect = new ParticleEffect(template);
    }

    /**
     * (Re)starts playback from scratch — safe to call again before a
     * previous playback has finished (e.g. two triggers close together),
     * which simply restarts it. The actual position is picked up on the
     * next {@link #update} call, not here, since the caller (often a
     * network callback) doesn't necessarily know the current render
     * position at the exact moment the underlying event is detected.
     */
    public void trigger() {
        effect.reset();
        playing = true;
    }

    /**
     * Advances this effect by one frame, positioned at
     * ({@code xPixels}, {@code yPixels}) — a no-op once playback has
     * finished (see {@link #trigger}).
     *
     * @param xPixels   the effect's current world position, in pixels
     * @param yPixels   the effect's current world position, in pixels
     * @param deltaTime time since the last frame, in seconds
     */
    public void update(float xPixels, float yPixels, float deltaTime) {
        if (!playing) {
            return;
        }
        effect.setPosition(xPixels, yPixels);
        effect.update(deltaTime);
        if (effect.isComplete()) {
            playing = false;
        }
    }

    /**
     * Draws this effect's currently active particles, if it's still playing.
     *
     * @param batch the batch to draw with, already begun with a matching projection matrix
     */
    public void draw(Batch batch) {
        if (playing) {
            effect.draw(batch);
        }
    }

    /**
     * Returns whether this effect is currently mid-playback — used to find
     * a free instance in a pool (e.g. {@code Client}'s explosion/remote
     * muzzle flash handling) rather than always allocating a new one.
     *
     * @return {@code true} if this effect is still playing
     */
    public boolean isPlaying() {
        return playing;
    }
}
