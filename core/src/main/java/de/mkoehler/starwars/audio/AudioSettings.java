package de.mkoehler.starwars.audio;

import com.badlogic.gdx.math.MathUtils;

/**
 * The player's locally saved audio volume preferences (design.md — audio
 * settings) — a master volume plus one per-category volume, each in
 * {@code [0, 1]} (0% off, 100% full). Plain mutable bean (public no-arg
 * constructor, getters and setters) so Jackson can (de)serialize it with no
 * extra configuration, same convention as this project's other Jackson
 * beans (e.g. {@link de.mkoehler.starwars.net.ConnectionConfig}) — unlike
 * {@link de.mkoehler.starwars.input.KeyBindings}/{@code KeyBindingsConfig},
 * one flat bean serves as both the persisted shape and the live runtime
 * object, since there's no per-key map needing translation here.
 * <p>
 * Every field defaults to {@code 1f} (full volume) — both for a brand-new
 * {@link AudioSettings} and, since Jackson only calls a setter for a key
 * actually present in the JSON file, for any field an older saved file
 * doesn't yet have (a category added in a later version).
 * <p>
 * The actual volume passed to a sound is always {@code masterVolume ×} the
 * relevant category's own volume — see {@link #getEffectiveWeaponsVolume()}/
 * {@link #getEffectiveEnginesVolume()}/{@link #getEffectiveSoundEffectsVolume()}.
 * {@link #getMasterVolume()} alone (with no category multiplied in) is also
 * used directly for non-gameplay audio that isn't any category's concern —
 * currently just the Connect Screen's background music
 * ({@code ConnectScreen}/{@code StarWarsGame#fadeOutAndDisposeMusic}).
 * <p>
 * {@link #save()} is a convenience wrapper around {@link AudioSettingsStore#save},
 * matching {@link de.mkoehler.starwars.input.KeyBindings#save()}'s own
 * "the live object knows how to persist itself" convenience — called by
 * {@code AudioSettingsScreen} after every change.
 */
public class AudioSettings {

    private float masterVolume = 1f;
    private float weaponsVolume = 1f;
    private float enginesVolume = 1f;
    private float soundEffectsVolume = 1f;

    /**
     * No-arg constructor required by Jackson for deserialization.
     */
    public AudioSettings() {
    }

    public float getMasterVolume() {
        return masterVolume;
    }

    public void setMasterVolume(float masterVolume) {
        this.masterVolume = MathUtils.clamp(masterVolume, 0f, 1f);
    }

    public float getWeaponsVolume() {
        return weaponsVolume;
    }

    public void setWeaponsVolume(float weaponsVolume) {
        this.weaponsVolume = MathUtils.clamp(weaponsVolume, 0f, 1f);
    }

    public float getEnginesVolume() {
        return enginesVolume;
    }

    public void setEnginesVolume(float enginesVolume) {
        this.enginesVolume = MathUtils.clamp(enginesVolume, 0f, 1f);
    }

    /**
     * Returns the "Sound Effects" category's own volume — currently a
     * placeholder with no sound wired up to it yet (design.md — audio
     * settings: explosions, asteroid/boundary collisions, etc. all still
     * have no audio at all), kept so the slider and its persisted value
     * already exist for whenever that changes.
     *
     * @return the sound effects category volume, in {@code [0, 1]}
     */
    public float getSoundEffectsVolume() {
        return soundEffectsVolume;
    }

    public void setSoundEffectsVolume(float soundEffectsVolume) {
        this.soundEffectsVolume = MathUtils.clamp(soundEffectsVolume, 0f, 1f);
    }

    /**
     * Returns the effective volume for a weapon/turret/missile-launch sound
     * (design.md — weapon sound): {@link #getMasterVolume()} ×
     * {@link #getWeaponsVolume()}.
     *
     * @return the effective volume, in {@code [0, 1]}
     */
    public float getEffectiveWeaponsVolume() {
        return masterVolume * weaponsVolume;
    }

    /**
     * Returns the effective volume for an engine-loop sound (design.md —
     * engine sound): {@link #getMasterVolume()} × {@link #getEnginesVolume()}.
     *
     * @return the effective volume, in {@code [0, 1]}
     */
    public float getEffectiveEnginesVolume() {
        return masterVolume * enginesVolume;
    }

    /**
     * Returns the effective volume for a general sound effect: {@link #getMasterVolume()} ×
     * {@link #getSoundEffectsVolume()}. See {@link #getSoundEffectsVolume()} for why nothing
     * calls this yet.
     *
     * @return the effective volume, in {@code [0, 1]}
     */
    public float getEffectiveSoundEffectsVolume() {
        return masterVolume * soundEffectsVolume;
    }

    /**
     * Persists these settings locally, overwriting any previous save.
     */
    public void save() {
        AudioSettingsStore.save(this);
    }
}
