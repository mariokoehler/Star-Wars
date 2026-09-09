package de.mkoehler.starwars.render;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;

/**
 * A one-shot expanding "energy wave" (design.md 2.14's rendering addendum),
 * played once each time a ship's active radar pulse actually fires — a
 * private copy of the shared {@code radar_pulse.p} template, kicked off by
 * {@link #trigger()} and centered on the pulsing ship every frame
 * afterward via {@link #update} (the authored effect is {@code attached:
 * true}, so it tracks the ship even if it keeps moving while the wave
 * expands).
 * <p>
 * Unlike {@link ThrusterEffect}/{@link DamageSmokeEffect}, this effect is
 * authored non-looping ({@code continuous: false}, one particle) — it
 * needs no on/off gating tied to a held or persistent condition, only a
 * one-shot restart each time the underlying event actually happens, and
 * {@link ParticleEffect#isComplete()} tells this wrapper when to stop
 * updating/drawing on its own once the single wave has finished expanding
 * and fading.
 */
public class RadarPulseEffect {

    private final ParticleEffect effect;
    private boolean playing;

    /**
     * Creates a radar pulse effect from a shared template.
     *
     * @param template the particle effect template to copy
     */
    public RadarPulseEffect(ParticleEffect template) {
        this.effect = new ParticleEffect(template);
    }

    /**
     * (Re)starts the wave from scratch — safe to call again before a
     * previous playback has finished (e.g. two pulses close together),
     * which simply restarts it. The actual position is picked up on the
     * next {@link #update} call, not here, since the caller (a network
     * callback) doesn't necessarily know the ship's current render
     * position at the exact moment the underlying event is detected.
     */
    public void trigger() {
        effect.reset();
        playing = true;
    }

    /**
     * Advances this effect by one frame, positioned at
     * ({@code xPixels}, {@code yPixels}) — a no-op once the one-shot
     * animation has finished playing (see {@link #trigger}).
     *
     * @param xPixels   the pulsing ship's current world position, in pixels
     * @param yPixels   the pulsing ship's current world position, in pixels
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
}
