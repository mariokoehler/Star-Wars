package de.mkoehler.starwars.render;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;
import com.badlogic.gdx.graphics.g2d.ParticleEmitter;
import com.badlogic.gdx.math.Vector2;

/**
 * A one-shot muzzle flash (design.md — muzzle flash), played once each
 * time a shot is fired from a ship's own {@code "PROJECTILE"} attachment
 * point(s) — a private copy of the shared {@code muzzle_flash.p} template,
 * fully (re)started by a single {@link #trigger} call.
 * <p>
 * Like {@link ThrusterEffect}, the authored effect fires in a fixed
 * direction (a tight forward-pointing spark burst, not an omnidirectional
 * one like {@link ShipLightEffect}/{@link DamageSmokeEffect}) that needs
 * rotating to match the shooter's current facing — done the same way, by
 * rewriting the emitter's angle range. Unlike {@link ThrusterEffect}/
 * {@link RadarPulseEffect}, though, this effect's authored life is only
 * ~50ms, so unlike those two, {@link #trigger} sets the position once and
 * for all rather than re-centering it every frame afterward — any actual
 * movement of the emitting ship within 50ms is imperceptible, so there's no
 * need for a separate per-frame {@code update(x, y, ...)} the way a
 * longer-lived attached effect needs.
 */
public class MuzzleFlashEffect {

    private final ParticleEffect effect;
    private final ParticleEmitter emitter;
    private final float baseAngleLowMin;
    private final float baseAngleLowMax;
    private final float baseAngleHighMin;
    private final float baseAngleHighMax;
    private boolean playing;

    /**
     * Creates a muzzle flash effect from a shared template.
     *
     * @param template the particle effect template to copy
     */
    public MuzzleFlashEffect(ParticleEffect template) {
        this.effect = new ParticleEffect(template);
        this.emitter = effect.getEmitters().first();
        ParticleEmitter.ScaledNumericValue angle = emitter.getAngle();
        this.baseAngleLowMin = angle.getLowMin();
        this.baseAngleLowMax = angle.getLowMax();
        this.baseAngleHighMin = angle.getHighMin();
        this.baseAngleHighMax = angle.getHighMax();
    }

    /**
     * Fires the flash at ({@code xPixels}, {@code yPixels}), rotated to
     * {@code angleDegrees} — safe to call again before a previous playback
     * has finished, which simply restarts it there instead.
     *
     * @param xPixels      the muzzle's world position at the moment of firing, in pixels
     * @param yPixels      the muzzle's world position at the moment of firing, in pixels
     * @param angleDegrees the shooting ship's current facing (or, for a shot with no known
     *                     shooter, its own travel direction as a stand-in — see
     *                     {@code Client}'s remote-flash handling), same sign convention as
     *                     {@link Vector2#rotateRad}
     */
    public void trigger(float xPixels, float yPixels, float angleDegrees) {
        ParticleEmitter.ScaledNumericValue angle = emitter.getAngle();
        angle.setLow(baseAngleLowMin + angleDegrees, baseAngleLowMax + angleDegrees);
        angle.setHigh(baseAngleHighMin + angleDegrees, baseAngleHighMax + angleDegrees);
        effect.setPosition(xPixels, yPixels);
        effect.reset();
        playing = true;
    }

    /**
     * Advances this effect by one frame — a no-op once the one-shot
     * animation has finished playing (see {@link #trigger}).
     *
     * @param deltaTime time since the last frame, in seconds
     */
    public void update(float deltaTime) {
        if (!playing) {
            return;
        }
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
     * a free instance in a pool ({@code Client}'s remote-flash handling)
     * rather than always allocating a new one.
     *
     * @return {@code true} if this effect is still playing
     */
    public boolean isPlaying() {
        return playing;
    }
}
