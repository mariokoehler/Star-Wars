package de.mkoehler.starwars.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.Optional;

/**
 * Loads/saves the local {@link CameraSettings} file (design.md 4.1) — a
 * client-local JSON file, separate from {@code connection-config.json}/
 * {@code keybindings.json}/{@code audio-settings.json} so a player can
 * reset one without touching the others. Same shape/conventions as
 * {@link de.mkoehler.starwars.audio.AudioSettingsStore}/
 * {@link de.mkoehler.starwars.net.ConnectionConfigStore}/
 * {@link de.mkoehler.starwars.input.KeyBindingsStore}.
 */
public final class CameraSettingsStore {

    private static final String FILE_NAME = "camera-settings.json";
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private CameraSettingsStore() {
    }

    /**
     * Loads the locally saved camera settings, if any.
     *
     * @return the saved settings, or empty if this is the first launch (or
     * the file is unreadable) — callers fall back to {@link CameraSettings}'
     * default 100% zoom
     */
    public static Optional<CameraSettings> load() {
        FileHandle file = Gdx.files.local(FILE_NAME);
        if (!file.exists()) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readValue(file.readString(), CameraSettings.class));
        } catch (Exception e) {
            // A corrupt/unreadable local file shouldn't block the player from playing - just
            // fall back to defaults, same as a first launch.
            return Optional.empty();
        }
    }

    /**
     * Persists camera settings locally, overwriting any previous save.
     *
     * @param settings the settings to save
     * @throws java.io.UncheckedIOException if the settings can't be serialized to JSON
     */
    public static void save(CameraSettings settings) {
        String json;
        try {
            json = MAPPER.writeValueAsString(settings);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new java.io.UncheckedIOException(e);
        }
        Gdx.files.local(FILE_NAME).writeString(json, false);
    }
}
