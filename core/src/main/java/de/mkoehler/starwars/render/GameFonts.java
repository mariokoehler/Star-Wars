package de.mkoehler.starwars.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;

/**
 * Generates real {@link BitmapFont}s from bundled {@code .ttf} files via
 * {@code gdx-freetype}'s {@link FreeTypeFontGenerator}, rather than relying
 * on a toolkit's pre-baked default font.
 * <p>
 * Every ship-selection/logo/banner text in this project so far has been
 * baked into art by hand (Python/Pillow, see CLAUDE.md) because there was no
 * live way to render "SF Distant Galaxy" — this is that live path, for
 * dynamic text (form widgets, HUD readouts) that can't be pre-baked because
 * its content isn't known ahead of time.
 * <p>
 * Each call generates a fresh {@link BitmapFont} (backed by its own GPU
 * texture) — callers own the returned font and must {@link BitmapFont#dispose()}
 * it themselves, same as any other libGDX texture-backed resource.
 */
public final class GameFonts {

    /** Classpath path of the one game font currently bundled, see design.md 4.4/CLAUDE.md. */
    public static final String SF_DISTANT_GALAXY_TTF_PATH = "fonts/sf_distant_galaxy.ttf";

    private GameFonts() {
    }

    /**
     * Generates a {@link BitmapFont} for "SF Distant Galaxy" at the given
     * pixel size — the font already used for this project's baked logo/menu
     * art (design.md 4.4), now available for live-rendered text too.
     *
     * @param sizePx the font size in pixels
     * @return a freshly generated, caller-owned font
     */
    public static BitmapFont generateSfDistantGalaxy(int sizePx) {
        return generate(Gdx.files.internal(SF_DISTANT_GALAXY_TTF_PATH), sizePx);
    }

    /**
     * Generates a {@link BitmapFont} from an arbitrary {@code .ttf} file at
     * the given pixel size.
     *
     * @param ttfFile the {@code .ttf} file to rasterize
     * @param sizePx  the font size in pixels
     * @return a freshly generated, caller-owned font
     */
    public static BitmapFont generate(FileHandle ttfFile, int sizePx) {
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(ttfFile);
        try {
            FreeTypeFontParameter parameter = new FreeTypeFontParameter();
            parameter.size = sizePx;
            parameter.minFilter = Texture.TextureFilter.Linear;
            parameter.magFilter = Texture.TextureFilter.Linear;
            return generator.generateFont(parameter);
        } finally {
            // Only used to bake the glyph texture above - the returned BitmapFont owns that
            // texture independently from here on, so the generator itself isn't needed after
            // this call and disposing it doesn't affect the font.
            generator.dispose();
        }
    }
}
