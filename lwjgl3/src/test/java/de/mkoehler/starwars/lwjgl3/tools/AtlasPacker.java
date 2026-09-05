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
        settings.maxWidth = 1024;
        settings.maxHeight = 1024;
        settings.filterMin = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear;
        settings.filterMag = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear;

        TexturePacker.process(settings, input.getPath(), output.getPath(), packFileName);
    }
}
