package de.mkoehler.starwars.render;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;

/**
 * One ship's turret-status light (design.md 2.5/2.9): a private pair of
 * copies of the shared {@link GameAssets#LIGHT_RED_PARTICLE}/
 * {@link GameAssets#LIGHT_GREEN_PARTICLE} templates — the same particle art
 * used for the (now superseded, on turreted ships) positioning lights —
 * attached to one of a ship's {@code "TURRET_INDICATOR"} attachment points
 * (see {@link de.mkoehler.starwars.sim.metadata.ShipSpriteMetadata}).
 * <p>
 * Unlike {@link ShipLightEffect}, which is always on, this effect has a
 * live on/off-equivalent state: whichever of the red/green copies matches
 * the ship's current turret-enabled flag is the one advanced and drawn each
 * frame; the other is left exactly as it was the last time it was active,
 * so switching back to it resumes rather than restarts.
 */
public class TurretIndicatorEffect {

    /** The {@code "TURRET_INDICATOR"} attachment point name (design.md 2.5). */
    public static final String TURRET_INDICATOR_ATTACHMENT_NAME = "TURRET_INDICATOR";

    private final ParticleEffect redEffect;
    private final ParticleEffect greenEffect;

    /**
     * Creates a turret indicator light from the shared red/green templates,
     * and starts both copies emitting immediately so either is ready to be
     * drawn the moment it becomes the active one.
     *
     * @param redTemplate   the disabled-turrets (red) particle effect template to copy
     * @param greenTemplate the enabled-turrets (green) particle effect template to copy
     */
    public TurretIndicatorEffect(ParticleEffect redTemplate, ParticleEffect greenTemplate) {
        this.redEffect = new ParticleEffect(redTemplate);
        this.redEffect.start();
        this.greenEffect = new ParticleEffect(greenTemplate);
        this.greenEffect.start();
    }

    /**
     * Advances the currently-active copy (red if {@code turretsEnabled} is
     * {@code false}, green otherwise) by one frame, positioned at
     * ({@code xPixels}, {@code yPixels}). The inactive copy is left
     * unadvanced.
     *
     * @param xPixels        the attachment point's current world position, in pixels
     * @param yPixels        the attachment point's current world position, in pixels
     * @param turretsEnabled the ship's current turret-enabled state
     * @param deltaTime      time since the last frame, in seconds
     */
    public void update(float xPixels, float yPixels, boolean turretsEnabled, float deltaTime) {
        ParticleEffect active = turretsEnabled ? greenEffect : redEffect;
        active.setPosition(xPixels, yPixels);
        active.update(deltaTime);
    }

    /**
     * Draws the currently-active copy's particles.
     *
     * @param batch          the batch to draw with, already begun with a matching projection matrix
     * @param turretsEnabled the ship's current turret-enabled state
     */
    public void draw(Batch batch, boolean turretsEnabled) {
        (turretsEnabled ? greenEffect : redEffect).draw(batch);
    }
}
