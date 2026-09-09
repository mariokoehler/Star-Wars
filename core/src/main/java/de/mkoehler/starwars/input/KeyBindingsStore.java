package de.mkoehler.starwars.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.Optional;

/**
 * Loads/saves the local {@link KeyBindingsConfig} file (design.md 3.8) — a
 * client-local JSON file, separate from {@code connection-config.json}
 * (design.md 3.7) so a player can reset their controls without touching
 * saved login info. Same shape/conventions as
 * {@link de.mkoehler.starwars.net.ConnectionConfigStore}.
 */
public final class KeyBindingsStore {

    private static final String FILE_NAME = "keybindings.json";
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private KeyBindingsStore() {
    }

    /**
     * Loads the locally saved keybinds config, if any.
     *
     * @return the saved config, or empty if this is the first launch (or the
     * file is unreadable) — {@link KeyBindings#load()} falls back to
     * defaults for whichever actions this doesn't cover
     */
    public static Optional<KeyBindingsConfig> load() {
        FileHandle file = Gdx.files.local(FILE_NAME);
        if (!file.exists()) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readValue(file.readString(), KeyBindingsConfig.class));
        } catch (Exception e) {
            // A corrupt/unreadable local file shouldn't block the player from playing - just
            // fall back to defaults, same as a first launch.
            return Optional.empty();
        }
    }

    /**
     * Persists a keybinds config locally, overwriting any previous one.
     *
     * @param config the config to save
     * @throws java.io.UncheckedIOException if the config can't be serialized to JSON
     */
    public static void save(KeyBindingsConfig config) {
        String json;
        try {
            json = MAPPER.writeValueAsString(config);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new java.io.UncheckedIOException(e);
        }
        Gdx.files.local(FILE_NAME).writeString(json, false);
    }
}
