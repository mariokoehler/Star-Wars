package de.mkoehler.starwars.render;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;

/**
 * One power-up's particle glow (design.md — power-ups): a private copy of
 * the shared {@code textures/particles/powerup.p} template — the same
 * effect for every {@code PowerUpType}, same "one global template" reasoning
 * as {@link ShipLightEffect}. Same shape as {@link ShipLightEffect}: emits
 * continuously for as long as the power-up exists (never toggled), authored
 * {@code attached: true} in the source {@code .p} file so it tracks the
 * power-up's own drifting position without any velocity/rotation handling
 * needed here.
 * <p>
 * {@code Client} draws this effect <em>before</em> the power-up's own
 * texture, centered at the same point — the user's own instruction — so the
 * glow reads as sitting behind the icon rather than on top of it.
 */
public class PowerUpEffect {

    private final ParticleEffect effect;

    /**
     * Creates a power-up glow from a shared template, and starts it emitting
     * immediately — there is no on/off state to manage afterward.
     *
     * @param template the particle effect template to copy
     */
    public PowerUpEffect(ParticleEffect template) {
        this.effect = new ParticleEffect(template);
        this.effect.start();
    }

    /**
     * Advances this effect by one frame, positioned at
     * ({@code xPixels}, {@code yPixels}).
     *
     * @param xPixels   the power-up's current world position, in pixels
     * @param yPixels   the power-up's current world position, in pixels
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
