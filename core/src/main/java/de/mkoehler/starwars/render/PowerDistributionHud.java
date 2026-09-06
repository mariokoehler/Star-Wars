package de.mkoehler.starwars.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import de.mkoehler.starwars.sim.PowerDistribution;
import de.mkoehler.starwars.sim.PowerSystem;

/**
 * Renders the power-distribution HUD widget (design.md 2.2): a holographic
 * background panel with three vertical glow bars (Shields/Weapons/Engines)
 * side by side, each clipped into a bottom-anchored "fill" gauge driven
 * directly by that system's current share of total power output — a bar at
 * the even baseline (1/3) reads about a third full, and since no system can
 * ever exceed roughly 78% (design.md 2.2's floor/clamping algorithm), no bar
 * ever reads completely full; that's expected, not a bug.
 * <p>
 * All four source images share one 512x512 canvas, each bar already
 * authored at its own horizontal position on that canvas — so, like
 * {@link ShipStatusHud}, drawing all of them at the same position/size lines
 * them up with no offset math needed. Unlike {@link ShipStatusHud}, the
 * visible-pixel range is the same for every bar and every ship type (there's
 * only one power-distribution panel design), so it's a fixed constant here
 * rather than per-ship config.
 */
public class PowerDistributionHud implements Disposable {

    /** Top of every bar's visible-pixel range (from the top of the image) — the 100% mark. */
    private static final float CLIP_TOP_PIXEL = 170f;
    /** Bottom of every bar's visible-pixel range — the 0% mark. */
    private static final float CLIP_BOTTOM_PIXEL = 423f;

    private final Texture background = new Texture(Gdx.files.internal("textures/hud/hud_distribution_background.png"));
    private final Texture shieldsBar = new Texture(Gdx.files.internal("textures/hud/hud_distribution_shield.png"));
    private final Texture weaponsBar = new Texture(Gdx.files.internal("textures/hud/hud_distribution_weapons.png"));
    private final Texture enginesBar = new Texture(Gdx.files.internal("textures/hud/hud_distribution_engine.png"));

    /**
     * Draws the widget as a {@code size}x{@code size} square with its
     * bottom-left corner at ({@code x}, {@code y}), in whatever coordinate
     * system the batch's currently-set projection matrix uses (a
     * screen-space HUD camera, not the world camera).
     *
     * @param batch        the batch to draw with; must already be between {@code begin()}/{@code end()}
     * @param x            the widget's screen X position
     * @param y            the widget's screen Y position
     * @param size         the widget's width and height
     * @param distribution the current power split to display
     */
    public void render(SpriteBatch batch, float x, float y, float size, PowerDistribution distribution) {
        batch.draw(background, x, y, size, size);
        drawBar(batch, shieldsBar, x, y, size, distribution.getFraction(PowerSystem.SHIELDS));
        drawBar(batch, weaponsBar, x, y, size, distribution.getFraction(PowerSystem.WEAPONS));
        drawBar(batch, enginesBar, x, y, size, distribution.getFraction(PowerSystem.ENGINES));
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

    @Override
    public void dispose() {
        background.dispose();
        shieldsBar.dispose();
        weaponsBar.dispose();
        enginesBar.dispose();
    }
}
