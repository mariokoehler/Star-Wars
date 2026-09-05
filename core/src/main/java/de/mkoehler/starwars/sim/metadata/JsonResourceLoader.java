package de.mkoehler.starwars.sim.metadata;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;

/**
 * Reads a Jackson bean from a classpath resource, e.g. one bundled from
 * {@code assets/} — the shared bit of {@link ShipSpriteMetadataLoader} and
 * {@link de.mkoehler.starwars.sim.ShipTypeConfig}'s loading, both of which
 * are read this same way at runtime by both the headless server and the
 * windowed client, and need identical behavior for a missing resource
 * (empty, not an error — see {@link #loadFromClasspath}).
 */
public final class JsonResourceLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonResourceLoader() {
    }

    /**
     * Loads a bean from a classpath resource.
     *
     * @param resourcePath the classpath-relative resource path
     * @param type         the bean type to deserialize into
     * @param <T>          the bean type
     * @return the loaded bean, or empty if no such resource exists
     * @throws UncheckedIOException if the resource exists but isn't valid JSON
     */
    public static <T> Optional<T> loadFromClasspath(String resourcePath, Class<T> type) {
        try (InputStream stream = JsonResourceLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return Optional.empty();
            }
            return Optional.of(MAPPER.readValue(stream, type));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load " + type.getSimpleName() + " from " + resourcePath, e);
        }
    }
}
