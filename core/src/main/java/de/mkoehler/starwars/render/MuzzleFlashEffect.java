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
 * rewriting the emitter's angle range.
 * <p>
 * <b>Inherits the shooter's velocity, the same way a real projectile does</b>
 * (design.md — explosions' addendum on this exact bug): the authored
 * effect's own particle velocity is purely relative to the emitter, with no
 * contribution from whatever was moving when it fired — fine at a
 * standstill, but a fast-moving ship visibly outruns its own flash within
 * its ~50-100ms life otherwise, since (unlike the actual shot,
 * {@code ProjectileFactory}) nothing here was adding the shooter's own
 * velocity on top. Fixed by leaning on the effect's own {@code attached:
 * true} authoring: {@link #update} keeps nudging the emitter's position
 * forward at the shooter's velocity every frame it's still playing, and
 * {@code attached} means each already-spawned spark gets dragged along by
 * that same per-frame delta — free positional "inheritance" without
 * touching individual particles' own velocities directly (not exposed by
 * this API in a way that could be added to after the fact).
 */
public class MuzzleFlashEffect {

    private final ParticleEffect effect;
    private final ParticleEmitter emitter;
    private final float baseAngleLowMin;
    private final float baseAngleLowMax;
    private final float baseAngleHighMin;
    private final float baseAngleHighMax;
    private boolean playing;
    private float spawnX;
    private float spawnY;
    private float velocityX;
    private float velocityY;
    private float elapsedSinceTrigger;

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
     * {@code angleDegrees} and carried forward at
     * ({@code velocityXPixelsPerSecond}, {@code velocityYPixelsPerSecond})
     * for as long as it keeps playing — safe to call again before a
     * previous playback has finished, which simply restarts it there
     * instead.
     *
     * @param xPixels                 the muzzle's world position at the moment of firing, in pixels
     * @param yPixels                 the muzzle's world position at the moment of firing, in pixels
     * @param angleDegrees            the shooting ship's current facing (or, for a shot with no known
     *                                shooter, its own travel direction as a stand-in — see
     *                                {@code Client}'s remote-flash handling), same sign convention as
     *                                {@link Vector2#rotateRad}
     * @param velocityXPixelsPerSecond the shooting ship's current velocity, in pixels/second —
     *                                 {@code 0} for a shooter whose velocity isn't known (see
     *                                 {@code Client}'s remote-flash handling for when that happens)
     * @param velocityYPixelsPerSecond the shooting ship's current velocity, in pixels/second
     */
    public void trigger(float xPixels, float yPixels, float angleDegrees,
                         float velocityXPixelsPerSecond, float velocityYPixelsPerSecond) {
        ParticleEmitter.ScaledNumericValue angle = emitter.getAngle();
        angle.setLow(baseAngleLowMin + angleDegrees, baseAngleLowMax + angleDegrees);
        angle.setHigh(baseAngleHighMin + angleDegrees, baseAngleHighMax + angleDegrees);
        spawnX = xPixels;
        spawnY = yPixels;
        velocityX = velocityXPixelsPerSecond;
        velocityY = velocityYPixelsPerSecond;
        elapsedSinceTrigger = 0f;
        effect.setPosition(xPixels, yPixels);
        effect.reset();
        playing = true;
    }

    /**
     * Advances this effect by one frame, dragging it (and, since the
     * authored effect is {@code attached: true}, every spark already
     * spawned) forward along the shooter's velocity from {@link #trigger} —
     * a no-op once the one-shot animation has finished playing.
     *
     * @param deltaTime time since the last frame, in seconds
     */
    public void update(float deltaTime) {
        if (!playing) {
            return;
        }
        elapsedSinceTrigger += deltaTime;
        effect.setPosition(spawnX + velocityX * elapsedSinceTrigger, spawnY + velocityY * elapsedSinceTrigger);
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
