package de.mkoehler.starwars.render;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;

/**
 * One ship's damage smoke plume (design.md — damage smoke), attached to
 * one of its {@code "DAMAGE_SMOKE"} attachment points, activated once the
 * ship's hull damage crosses that point's own threshold — see
 * {@link de.mkoehler.starwars.Client}'s {@code DamageSmokePoint}/
 * {@code buildDamageSmokePoints} for exactly which point uses which
 * threshold.
 * <p>
 * Same hard on/off + discard-and-restart-on-reactivation shape as
 * {@link ThrusterEffect}, but with nothing to rotate: the authored effect's
 * puffs already drift outward in a random direction (a full 0°-360° spread)
 * rather than a directional cone tied to the ship's facing, so there's no
 * angle value worth rewriting per frame — only the attachment point's own
 * position needs updating each frame, same as {@link ShipLightEffect}.
 * Unlike a positioning light, this effect is deliberately authored
 * <em>unattached</em> ({@code attached: false} in the {@code .p} file) so
 * puffs drift and linger behind the ship as it moves rather than sticking
 * to the hull — correct for smoke, wrong for a light, the exact distinction
 * that motivated fixing the light effects to {@code attached: true}
 * instead.
 */
public class DamageSmokeEffect {

    /** The {@code "DAMAGE_SMOKE"} attachment point name (design.md 4.3/2.4-adjacent). */
    public static final String DAMAGE_SMOKE_ATTACHMENT_NAME = "DAMAGE_SMOKE";

    private final ParticleEffect effect;
    private boolean active;

    /**
     * Creates a damage smoke effect from a shared template.
     *
     * @param template the particle effect template to copy
     */
    public DamageSmokeEffect(ParticleEffect template) {
        this.effect = new ParticleEffect(template);
    }

    /**
     * Advances this effect by one frame, positioned at
     * ({@code xPixels}, {@code yPixels}) — only while {@code shouldBeActive}
     * is {@code true}; a no-op (and {@link #draw} then draws nothing)
     * otherwise. Discards any still-alive particles via
     * {@link ParticleEffect#reset()} on the transition from inactive to
     * active, same reasoning as {@link ThrusterEffect#update} — this
     * project's hull damage never decreases mid-life (no hull regen, only
     * shields regen), so in practice this only ever fires once per ship
     * life per attachment point, but stays robust if that ever changes.
     *
     * @param xPixels        the attachment point's current world position, in pixels
     * @param yPixels        the attachment point's current world position, in pixels
     * @param shouldBeActive whether this ship's current hull damage exceeds this point's threshold
     * @param deltaTime      time since the last frame, in seconds
     */
    public void update(float xPixels, float yPixels, boolean shouldBeActive, float deltaTime) {
        if (shouldBeActive && !active) {
            effect.reset();
            active = true;
        } else if (!shouldBeActive && active) {
            active = false;
        }
        if (!active) {
            return;
        }

        effect.setPosition(xPixels, yPixels);
        effect.update(deltaTime);
    }

    /**
     * Draws this effect's currently active particles, if any (see {@link #update}).
     *
     * @param batch the batch to draw with, already begun with a matching projection matrix
     */
    public void draw(Batch batch) {
        if (active) {
            effect.draw(batch);
        }
    }
}
