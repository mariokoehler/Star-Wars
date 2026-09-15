package de.mkoehler.starwars.render;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import de.mkoehler.starwars.sim.PowerDistribution;
import de.mkoehler.starwars.sim.PowerSystem;

import java.util.Map;

/**
 * Renders the power-distribution HUD widget (design.md 2.2): a holographic
 * background panel with three vertical glow bars (Shields/Weapons/Engines)
 * side by side, each clipped into a bottom-anchored "fill" gauge — a bar at
 * the even baseline (1/3) reads about a third full, and since no system's
 * raw priority share can ever exceed roughly 78% (design.md 2.2's floor/
 * clamping algorithm), no bar ever reads completely full purely from
 * priority alone; that's expected, not a bug.
 * <p>
 * Since design.md 2.2's priority-based rework, each bar shows one of two
 * things depending on whether that system currently has demand: while idle
 * (effective fraction {@code 0}), it shows its <em>priority</em> share dimmed
 * gray — "reserved here, but not doing anything right now"; while demanding,
 * it shows its <em>effective</em> share at normal brightness, with a faint
 * green tint whenever that's higher than its priority share — "actively
 * boosted by another system's idle power." A demanding bar can therefore
 * read taller than its own priority share, unlike before the rework.
 * <p>
 * All four source images share one 512x512 canvas, each bar already
 * authored at its own horizontal position on that canvas — so, like
 * {@link ShipStatusHud}, drawing all of them at the same position/size lines
 * them up with no offset math needed. Unlike {@link ShipStatusHud}, the
 * visible-pixel range is the same for every bar and every ship type (there's
 * only one power-distribution panel design), so it's a fixed constant here
 * rather than per-ship config.
 * <p>
 * Every texture here is read from the shared {@link AssetManager} (see
 * {@link ShipStatusHud}'s class Javadoc for the same reasoning) rather than
 * loaded/disposed by this class itself, so this class owns nothing and
 * needs no {@code dispose()}.
 */
public class PowerDistributionHud {

    /** Top of every bar's visible-pixel range (from the top of the image) — the 100% mark. */
    private static final float CLIP_TOP_PIXEL = 170f;
    /** Bottom of every bar's visible-pixel range — the 0% mark. */
    private static final float CLIP_BOTTOM_PIXEL = 423f;

    private final Texture background;
    private final Texture shieldsBar;
    private final Texture weaponsBar;
    private final Texture enginesBar;

    /**
     * Creates the widget, reading its HUD chrome from {@code assets}
     * immediately — all four must already be loaded (see the class
     * Javadoc).
     *
     * @param assets the shared asset manager to resolve textures from
     */
    public PowerDistributionHud(AssetManager assets) {
        background = assets.get(GameAssets.HUD_DISTRIBUTION_BACKGROUND, Texture.class);
        shieldsBar = assets.get(GameAssets.HUD_DISTRIBUTION_SHIELD, Texture.class);
        weaponsBar = assets.get(GameAssets.HUD_DISTRIBUTION_WEAPONS, Texture.class);
        enginesBar = assets.get(GameAssets.HUD_DISTRIBUTION_ENGINE, Texture.class);
    }

    /**
     * Draws the widget as a {@code size}x{@code size} square with its
     * bottom-left corner at ({@code x}, {@code y}), in whatever coordinate
     * system the batch's currently-set projection matrix uses (a
     * screen-space HUD camera, not the world camera).
     *
     * @param batch        the batch to draw with; must already be between {@code begin()}/{@code end()}
     * @param x                  the widget's screen X position
     * @param y                  the widget's screen Y position
     * @param size               the widget's width and height
     * @param distribution       the current power priority split to display
     * @param effectiveFractions each system's current effective fraction (design.md 2.2's
     *                           priority-based rework), {@code 0} for a system with no demand —
     *                           see {@link PowerDistribution#effectiveFractions}
     */
    public void render(SpriteBatch batch, float x, float y, float size, PowerDistribution distribution,
                        Map<PowerSystem, Float> effectiveFractions) {
        batch.draw(background, x, y, size, size);
        drawSystemBar(batch, shieldsBar, x, y, size, distribution.getFraction(PowerSystem.SHIELDS),
            effectiveFractions.get(PowerSystem.SHIELDS));
        drawSystemBar(batch, weaponsBar, x, y, size, distribution.getFraction(PowerSystem.WEAPONS),
            effectiveFractions.get(PowerSystem.WEAPONS));
        drawSystemBar(batch, enginesBar, x, y, size, distribution.getFraction(PowerSystem.ENGINES),
            effectiveFractions.get(PowerSystem.ENGINES));
    }

    /**
     * Draws one system's bar per the class Javadoc's "idle shows dimmed
     * priority, demanding shows effective (tinted green if boosted)" rule.
     */
    private void drawSystemBar(SpriteBatch batch, Texture texture, float x, float y, float size,
                                float priorityFraction, float effectiveFraction) {
        boolean demanding = effectiveFraction > 0f;
        float heightFraction = demanding ? effectiveFraction : priorityFraction;

        Color previous = batch.getColor().cpy();
        if (!demanding) {
            batch.setColor(0.55f, 0.55f, 0.55f, 0.6f);
        } else if (effectiveFraction > priorityFraction + 0.01f) {
            batch.setColor(0.75f, 1f, 0.85f, 1f);
        }
        drawBar(batch, texture, x, y, size, heightFraction);
        batch.setColor(previous);
    }

    private void drawBar(SpriteBatch batch, Texture texture, float x, float y, float size, float fraction) {
        HudGaugeClip.Result clip = HudGaugeClip.compute(fraction, CLIP_TOP_PIXEL, CLIP_BOTTOM_PIXEL,
            size, texture.getHeight(), y);
        if (clip.revealHeightPixels() <= 0) {
            return;
        }

        TextureRegion region = new TextureRegion(texture, 0, clip.revealTopPixel(), texture.getWidth(), clip.revealHeightPixels());
        batch.draw(region, x, clip.sliceScreenBottomY(), size, clip.sliceScreenHeight());
    }
}
