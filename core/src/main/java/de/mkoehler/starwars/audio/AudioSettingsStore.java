package de.mkoehler.starwars.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.Optional;

/**
 * Loads/saves the local {@link AudioSettings} file (design.md — audio
 * settings) — a client-local JSON file, separate from
 * {@code connection-config.json}/{@code keybindings.json} so a player can
 * reset one without touching the others. Same shape/conventions as
 * {@link de.mkoehler.starwars.net.ConnectionConfigStore}/
 * {@link de.mkoehler.starwars.input.KeyBindingsStore}.
 */
public final class AudioSettingsStore {

    private static final String FILE_NAME = "audio-settings.json";
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private AudioSettingsStore() {
    }

    /**
     * Loads the locally saved audio settings, if any.
     *
     * @return the saved settings, or empty if this is the first launch (or
     * the file is unreadable) — callers fall back to {@link AudioSettings}'
     * all-100% defaults
     */
    public static Optional<AudioSettings> load() {
        FileHandle file = Gdx.files.local(FILE_NAME);
        if (!file.exists()) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readValue(file.readString(), AudioSettings.class));
        } catch (Exception e) {
            // A corrupt/unreadable local file shouldn't block the player from playing - just
            // fall back to defaults, same as a first launch.
            return Optional.empty();
        }
    }

    /**
     * Persists audio settings locally, overwriting any previous save.
     *
     * @param settings the settings to save
     * @throws java.io.UncheckedIOException if the settings can't be serialized to JSON
     */
    public static void save(AudioSettings settings) {
        String json;
        try {
            json = MAPPER.writeValueAsString(settings);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new java.io.UncheckedIOException(e);
        }
        Gdx.files.local(FILE_NAME).writeString(json, false);
    }
}
