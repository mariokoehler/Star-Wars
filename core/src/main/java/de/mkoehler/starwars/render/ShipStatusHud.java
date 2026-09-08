package de.mkoehler.starwars.render;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import de.mkoehler.starwars.sim.ShipStats;
import de.mkoehler.starwars.sim.ShipType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Renders a ship's hull/shield status HUD widget (design.md 2.5): a
 * holographic background panel, a shield-strength ring, and a ship-hull
 * silhouette with a green (full) to red (empty) gradient — the latter two
 * clipped into a bottom-anchored "fill" gauge driven by the ship's current
 * shield/hull fraction.
 * <p>
 * All three source images share one 512x512 canvas so they line up with no
 * offset math needed when drawn at the same position/size — but that also
 * means each one's actually-visible content only occupies part of that
 * canvas (the rest is transparent padding). {@link ShipStats}'
 * {@code getHud*ClipTopPixel()}/{@code getHud*ClipBottomPixel()} mark that
 * visible range (in image-pixel space, top-down, matching
 * {@link TextureRegion}'s own pixel convention) for the shield and hull
 * overlays respectively, and differ per ship type since each ship's art
 * occupies a different vertical extent of that shared canvas.
 * <p>
 * The background panel and shield ring are generic HUD chrome, shared by
 * every ship type; the hull silhouette is ship-specific art, resolved lazily
 * per {@link ShipType} from the shared {@link AssetManager} — falling back
 * to the X-wing's own hull art (and, since every ship type currently copies
 * the X-wing's hud clip numbers too, its correct clip range) for any ship
 * type {@link GameAssets#queueAll} didn't find (and therefore didn't queue)
 * art for.
 * <p>
 * Every texture here is read from {@code assets} (owned by
 * {@code StarWarsGame}, populated once by {@code SplashScreen} before any
 * screen using this widget is ever shown) rather than loaded/disposed by
 * this class itself — so, unlike its predecessor, this class owns nothing
 * and needs no {@link com.badlogic.gdx.utils.Disposable#dispose()}.
 */
public class ShipStatusHud {

    private static final String FALLBACK_HULL_TEXTURE_PATH = GameAssets.shipHullTexturePath(ShipType.XWING);

    private final AssetManager assets;
    private final Texture background;
    private final Texture shield;
    private final Map<ShipType, Texture> hullTexturesByType = new EnumMap<>(ShipType.class);

    /**
     * Creates the widget, reading its shared HUD chrome from {@code assets}
     * immediately — both must already be loaded (see the class Javadoc).
     *
     * @param assets the shared asset manager to resolve textures from
     */
    public ShipStatusHud(AssetManager assets) {
        this.assets = assets;
        background = assets.get(GameAssets.HUD_STATUS_BACKGROUND, Texture.class);
        shield = assets.get(GameAssets.HUD_STATUS_SHIELD, Texture.class);
    }

    /**
     * Draws the widget as a {@code size}x{@code size} square with its
     * bottom-left corner at ({@code x}, {@code y}), in whatever coordinate
     * system the batch's currently-set projection matrix uses (a
     * screen-space HUD camera, not the world camera ships/projectiles are
     * drawn with).
     *
     * @param batch          the batch to draw with; must already be between
     *                       {@code begin()}/{@code end()}
     * @param stats          the ship type's stats, for its HUD clip layout
     * @param x              the widget's screen X position
     * @param y              the widget's screen Y position
     * @param size           the widget's width and height
     * @param hullFraction   current hull health / max, in {@code [0, 1]}
     * @param shieldFraction current shield strength / max, in {@code [0, 1]}
     */
    public void render(SpriteBatch batch, ShipStats stats, float x, float y, float size,
                        float hullFraction, float shieldFraction) {
        Texture hullTexture = hullTexturesByType.computeIfAbsent(stats.getType(), type -> {
            String preferredPath = GameAssets.shipHullTexturePath(type);
            String path = assets.isLoaded(preferredPath, Texture.class) ? preferredPath : FALLBACK_HULL_TEXTURE_PATH;
            return assets.get(path, Texture.class);
        });

        batch.draw(background, x, y, size, size);
        drawClippedFromBottom(batch, shield, x, y, size, shieldFraction,
            stats.getHudShieldClipTopPixel(), stats.getHudShieldClipBottomPixel());
        drawClippedFromBottom(batch, hullTexture, x, y, size, hullFraction,
            stats.getHudHullClipTopPixel(), stats.getHudHullClipBottomPixel());
    }

    /**
     * Draws only the bottom {@code fraction} of an overlay's visible-pixel
     * range [{@code clipTopPixel}, {@code clipBottomPixel}], anchored to the
     * same bottom edge regardless of fraction — a "fuel gauge" that fills
     * from the bottom up as fraction increases, empties from the top down as
     * it decreases.
     * <p>
     * The top and bottom reveal pixels are both rounded from the *same*
     * base (the bottom pixel), not independently, so the revealed slice's
     * bottom edge never drifts by a stray pixel as the fraction changes
     * frame to frame.
     */
    private void drawClippedFromBottom(SpriteBatch batch, Texture texture, float x, float y, float size,
                                        float fraction, float clipTopPixel, float clipBottomPixel) {
        HudGaugeClip.Result clip = HudGaugeClip.compute(fraction, clipTopPixel, clipBottomPixel,
            size, texture.getHeight(), y);
        if (clip.revealHeightPixels() <= 0) {
            return;
        }

        TextureRegion region = new TextureRegion(texture, 0, clip.revealTopPixel(), texture.getWidth(), clip.revealHeightPixels());
        batch.draw(region, x, clip.sliceScreenBottomY(), size, clip.sliceScreenHeight());
    }
}
