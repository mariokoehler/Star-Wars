package de.mkoehler.starwars.render;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;
import com.badlogic.gdx.graphics.g2d.ParticleEmitter;
import com.badlogic.gdx.math.Vector2;

/**
 * A single ship's engine thruster glow (design.md — engine particle
 * effects): a private copy of a shared {@link ParticleEffect} template (see
 * {@link GameAssets#particleEffectPath}), attached to one of a ship's
 * {@code "ENGINE"} attachment points (see
 * {@link de.mkoehler.starwars.sim.metadata.ShipSpriteMetadata}), emitting
 * only while {@link #update} is told the ship is currently thrusting.
 * <p>
 * Copying the template (rather than sharing one instance across every ship
 * of the same type) gives each ship fully independent emission state
 * (active particles, timers) — safe to do freely since a copy's own
 * {@link ParticleEffect#dispose()} is a no-op unless it loaded its own
 * images directly (only the original, {@link com.badlogic.gdx.assets.AssetManager}-owned
 * template does that); the copy shares the same underlying {@code Sprite}/
 * {@code Texture} instances instead.
 * <p>
 * The template is authored assuming the ship's own default (unrotated,
 * angle {@code 0}) facing — this project's convention has a ship's engine
 * sit at its tail, exhaust pointing straight down screen-space when
 * unrotated (see {@code ShipControlSystem}'s forward-vector convention).
 * The classic 2D {@link ParticleEffect} has no single "rotate the whole
 * effect" method of its own, so {@link #update} keeps the emission cone
 * aligned with the ship's actual current facing by re-adding the ship's
 * rotation (in degrees, same sign convention as {@link Vector2#rotateRad})
 * onto the template's originally-authored angle range every frame.
 * <p>
 * Deliberately a hard on/off switch, not a fade — the effect's particles
 * simply stop being updated/drawn the instant thrust is released, and any
 * still-in-flight ones are discarded (via {@link ParticleEffect#reset()})
 * the next time thrust starts again, rather than risk a stale, frozen
 * particle visibly teleporting once the emitter's position later jumps to
 * wherever the ship has since moved. Revisit with a proper fade-out if this
 * reads as too abrupt once actually seen live.
 */
public class ThrusterEffect {

    /** The {@code "ENGINE"} attachment point name (design.md 4.3/2.4-adjacent). */
    public static final String ENGINE_ATTACHMENT_NAME = "ENGINE";

    private final ParticleEffect effect;
    private final ParticleEmitter emitter;
    private final float baseAngleLowMin;
    private final float baseAngleLowMax;
    private final float baseAngleHighMin;
    private final float baseAngleHighMax;
    private boolean active;

    /**
     * Creates a thruster effect from a shared template.
     *
     * @param template the particle effect template to copy — its first (and
     *                 expected only) emitter is the one this effect tracks
     */
    public ThrusterEffect(ParticleEffect template) {
        this.effect = new ParticleEffect(template);
        this.emitter = effect.getEmitters().first();
        ParticleEmitter.ScaledNumericValue angle = emitter.getAngle();
        this.baseAngleLowMin = angle.getLowMin();
        this.baseAngleLowMax = angle.getLowMax();
        this.baseAngleHighMin = angle.getHighMin();
        this.baseAngleHighMax = angle.getHighMax();
    }

    /**
     * Advances this effect by one frame, positioned at
     * ({@code xPixels}, {@code yPixels}) and rotated to
     * {@code shipAngleDegrees} — only while {@code thrusting} is
     * {@code true}; a no-op otherwise (and {@link #draw} then draws
     * nothing).
     *
     * @param xPixels          the engine attachment point's current world position, in pixels
     * @param yPixels          the engine attachment point's current world position, in pixels
     * @param shipAngleDegrees the ship's current facing, in degrees, same
     *                         sign convention as {@link Vector2#rotateRad}
     * @param thrusting        whether the ship's forward-thrust input is currently held
     * @param deltaTime        time since the last frame, in seconds
     */
    public void update(float xPixels, float yPixels, float shipAngleDegrees, boolean thrusting, float deltaTime) {
        if (thrusting && !active) {
            effect.reset();
            active = true;
        } else if (!thrusting && active) {
            active = false;
        }
        if (!active) {
            return;
        }

        ParticleEmitter.ScaledNumericValue angle = emitter.getAngle();
        angle.setLow(baseAngleLowMin + shipAngleDegrees, baseAngleLowMax + shipAngleDegrees);
        angle.setHigh(baseAngleHighMin + shipAngleDegrees, baseAngleHighMax + shipAngleDegrees);

        effect.setPosition(xPixels, yPixels);
        effect.update(deltaTime);
    }

    /**
     * Draws this effect's currently active particles, if any — a no-op
     * while inactive (see {@link #update}).
     *
     * @param batch the batch to draw with, already begun with a matching projection matrix
     */
    public void draw(Batch batch) {
        if (active) {
            effect.draw(batch);
        }
    }
}
