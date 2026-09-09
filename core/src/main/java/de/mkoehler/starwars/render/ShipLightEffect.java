package de.mkoehler.starwars.render;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;

/**
 * One ship's positioning light (design.md — positioning lights): a private
 * copy of a shared {@link ParticleEffect} template — the same
 * {@code light_red.p}/{@code light_green.p} effect for every ship type,
 * unlike a ship's own configurable engine effect (see {@link GameAssets}) —
 * attached to one of a ship's {@code "LIGHT_RED"}/{@code "LIGHT_GREEN"}
 * attachment points (see
 * {@link de.mkoehler.starwars.sim.metadata.ShipSpriteMetadata}), matching a
 * real plane/ship's red-left/green-right navigation lights.
 * <p>
 * Unlike {@link ThrusterEffect}, a positioning light is never toggled by
 * player input — it emits continuously for as long as the ship exists —
 * and its authored particles never move relative to their spawn point
 * (zero velocity, a stationary blink), so unlike the thruster's exhaust
 * cone there's nothing to rotate to match the ship's current facing; only
 * the attachment point's own position needs rotating into world space,
 * which the caller already does before calling {@link #update}.
 */
public class ShipLightEffect {

    /** The {@code "LIGHT_RED"} attachment point name (design.md 4.3/2.4-adjacent). */
    public static final String LIGHT_RED_ATTACHMENT_NAME = "LIGHT_RED";
    /** The {@code "LIGHT_GREEN"} attachment point name (design.md 4.3/2.4-adjacent). */
    public static final String LIGHT_GREEN_ATTACHMENT_NAME = "LIGHT_GREEN";

    private final ParticleEffect effect;

    /**
     * Creates a positioning light from a shared template, and starts it
     * emitting immediately — there is no on/off state to manage afterward.
     *
     * @param template the particle effect template to copy
     */
    public ShipLightEffect(ParticleEffect template) {
        this.effect = new ParticleEffect(template);
        this.effect.start();
    }

    /**
     * Advances this effect by one frame, positioned at
     * ({@code xPixels}, {@code yPixels}).
     *
     * @param xPixels   the attachment point's current world position, in pixels
     * @param yPixels   the attachment point's current world position, in pixels
     * @param deltaTime time since the last frame, in seconds
     */
    public void update(float xPixels, float yPixels, float deltaTime) {
        effect.setPosition(xPixels, yPixels);
        effect.update(deltaTime);
    }

    /**
     * Draws this effect's currently active particles.
     *
     * @param batch the batch to draw with, already begun with a matching projection matrix
     */
    public void draw(Batch batch) {
        effect.draw(batch);
    }
}
