package de.mkoehler.starwars.lwjgl3.tools;

import com.badlogic.gdx.tools.texturepacker.TexturePacker;

import java.io.File;

/**
 * Developer-only utility that (re)builds this project's texture atlases from
 * the raw source images in {@code assets-raw/}, writing the packed atlas and
 * page images into {@code assets/textures/}.
 * <p>
 * This is not a JUnit test — it has a {@code main()} method and is meant to
 * be run by hand whenever raw art under {@code assets-raw/} changes, via:
 * <pre>{@code
 * mvn -pl lwjgl3 -am test-compile exec:java \
 *     -Dexec.classpathScope=test \
 *     -Dexec.mainClass=de.mkoehler.starwars.lwjgl3.tools.AtlasPacker
 * }</pre>
 * It lives under {@code src/test} (rather than {@code src/main}) specifically
 * so that {@code gdx-tools} stays off the shaded runtime jar's classpath.
 */
public final class AtlasPacker {

    private AtlasPacker() {
    }

    /**
     * Packs every configured atlas. Currently just the ships atlas; add more
     * {@link #pack(String, String, String)} calls here as more raw asset
     * folders are added under {@code assets-raw/}.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        pack("ships", "ships");
        pack("projectiles", "projectiles");
        pack("menu", "menu");
        // Death Screen art (design.md 5.1) is deliberately NOT packed here - it's 23 quote
        // variants only ever shown one at a time, never batched together in the same draw call,
        // so atlas-packing them would only force all 23 into GPU memory (~4 full 2048x2048 pages)
        // to use one - the exact "atlases are for sharing a texture bind across many simultaneous
        // sprites" case this doesn't fit. See DeathScreen for the plain-Texture, load-on-demand
        // approach instead - copied loose into assets/textures/after_death/, same convention as
        // this project's tileable backgrounds (blue_nebula.png, menu_starfield.png).
    }

    /**
     * Packs one atlas from {@code assets-raw/<sourceFolder>} into
     * {@code assets/textures/<packFileName>.atlas} (plus its page image(s)).
     *
     * @param sourceFolder folder name under {@code assets-raw/} to read source images from
     * @param packFileName base file name (without extension) for the generated atlas
     */
    private static void pack(String sourceFolder, String packFileName) {
        // exec-maven-plugin runs with the lwjgl3 module directory as the working
        // directory, so the repo root (and assets-raw/assets siblings) is one level up.
        File input = new File("../assets-raw/" + sourceFolder);
        File output = new File("../assets/textures");

        TexturePacker.Settings settings = new TexturePacker.Settings();
        // 2048 rather than 1024 - once ship portraits (512x512 each, 6 ships) were added
        // alongside the existing hull frames, 1024x1024 pages could no longer fit everything on
        // one page, needing 6 separate pages (and 6 texture binds per frame instead of 1-2) for
        // what's still a modest amount of total content. Comfortably supported by any GPU this
        // project targets.
        settings.maxWidth = 2048;
        settings.maxHeight = 2048;
        settings.filterMin = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear;
        settings.filterMag = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear;
        // Without this, TexturePacker treats each subfolder of the input directory as an
        // entirely separate pack - fine for region naming (still prefixed by subfolder, e.g.
        // "falcon/falcon256"), but it means every ship type got its own dedicated page even
        // though most are far smaller than maxWidth/maxHeight, wasting texture memory and
        // adding an extra texture bind per ship type at render time. Found once a second ship
        // type's worth of sprites got packed and produced ships2.png/ships3.png/... one per
        // subfolder instead of sharing pages.
        settings.combineSubdirectories = true;

        TexturePacker.process(settings, input.getPath(), output.getPath(), packFileName);
    }
}
