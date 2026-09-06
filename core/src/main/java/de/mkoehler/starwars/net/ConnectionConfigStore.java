package de.mkoehler.starwars.net;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.Optional;

/**
 * Loads/saves the local {@link ConnectionConfig} file (design.md 3.7) - a
 * client-local JSON file the Connect Dialog pre-fills itself from on launch
 * and overwrites after every successful connect.
 */
public final class ConnectionConfigStore {

    private static final String FILE_NAME = "connection-config.json";
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private ConnectionConfigStore() {
    }

    /**
     * Loads the locally saved connection config, if any.
     *
     * @return the saved config, or empty if this is the first launch (or the
     * file is unreadable)
     */
    public static Optional<ConnectionConfig> load() {
        FileHandle file = Gdx.files.local(FILE_NAME);
        if (!file.exists()) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readValue(file.readString(), ConnectionConfig.class));
        } catch (Exception e) {
            // A corrupt/unreadable local config shouldn't block the player from connecting -
            // just fall back to an empty dialog, same as a first launch.
            return Optional.empty();
        }
    }

    /**
     * Persists a connection config locally, overwriting any previous one.
     *
     * @param config the config to save
     * @throws java.io.UncheckedIOException if the config can't be serialized to JSON
     */
    public static void save(ConnectionConfig config) {
        String json;
        try {
            json = MAPPER.writeValueAsString(config);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new java.io.UncheckedIOException(e);
        }
        // Gdx.files.local writes relative to the working directory the client was launched
        // from - the same "implementation detail for later" design.md 3.7 flags. Throws its
        // own unchecked GdxRuntimeException on failure; not caught here, same as every other
        // Gdx.files call in this codebase.
        Gdx.files.local(FILE_NAME).writeString(json, false);
    }
}
