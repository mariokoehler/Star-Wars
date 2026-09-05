package de.mkoehler.starwars.sim.metadata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;

/**
 * Reads and writes {@link ShipSpriteMetadata} as JSON.
 * <p>
 * Deliberately not built on libGDX's {@code Gdx.files} — this needs to work
 * identically for the game (headless server and windowed client alike, via
 * {@link #loadFromClasspath}) and for the {@code dev-tools} sprite metadata
 * editor (which edits plain files on disk, via {@link #loadFromFile}/
 * {@link #saveToFile}, and has no libGDX application context at all).
 */
public final class ShipSpriteMetadataLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private ShipSpriteMetadataLoader() {
    }

    /**
     * Loads ship sprite metadata from a classpath resource, e.g.
     * {@code "shipdata/xwing.meta.json"} bundled from {@code assets/}. This
     * is how the game itself reads metadata at runtime.
     *
     * @param resourcePath the classpath-relative resource path
     * @return the loaded metadata, or empty if no such resource exists — a
     * ship with no metadata yet is expected (design.md 2.4/4.3), callers
     * should fall back to a default hitbox/attachment points, not fail
     * @throws UncheckedIOException if the resource exists but isn't valid JSON
     */
    public static Optional<ShipSpriteMetadata> loadFromClasspath(String resourcePath) {
        try (InputStream stream = ShipSpriteMetadataLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return Optional.empty();
            }
            return Optional.of(MAPPER.readValue(stream, ShipSpriteMetadata.class));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load ship sprite metadata from " + resourcePath, e);
        }
    }

    /**
     * Loads ship sprite metadata from a file on disk. Used by the sprite
     * metadata editor, which works directly with files being edited rather
     * than packaged classpath resources.
     *
     * @param file the file to read
     * @return the loaded metadata
     * @throws IOException if the file can't be read or isn't valid JSON
     */
    public static ShipSpriteMetadata loadFromFile(File file) throws IOException {
        return MAPPER.readValue(file, ShipSpriteMetadata.class);
    }

    /**
     * Writes ship sprite metadata to a file on disk, pretty-printed (so it's
     * readable and diffs cleanly in git). Used by the sprite metadata editor.
     *
     * @param metadata the metadata to write
     * @param file     the file to write to; overwritten if it already exists
     * @throws IOException if the file can't be written
     */
    public static void saveToFile(ShipSpriteMetadata metadata, File file) throws IOException {
        MAPPER.writeValue(file, metadata);
    }
}
