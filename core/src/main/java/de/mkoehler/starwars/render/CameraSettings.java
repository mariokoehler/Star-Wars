package de.mkoehler.starwars.render;

import com.badlogic.gdx.math.MathUtils;

/**
 * The player's locally saved camera preferences (design.md 4.1) —
 * currently just their chosen default zoom level, adjusted in-match with
 * the Zoom In/Zoom Out keybinds (design.md 3.8). Plain mutable bean
 * (public no-arg constructor, getters and setters) so Jackson can
 * (de)serialize it with no extra configuration, exactly the same shape and
 * reasoning as {@link de.mkoehler.starwars.audio.AudioSettings}.
 * <p>
 * {@link #getZoom()} is a multiplier on the camera's baseline zoom, in
 * {@code [}{@link #MIN_ZOOM}{@code , }{@link #MAX_ZOOM}{@code ]} — 50% to
 * 200% of the default, per the user's own spec. It composes
 * <i>multiplicatively</i> with the speed-linked zoom
 * ({@code Client.updateCamera}), which is left entirely unchanged: the
 * speed-driven pull-back is always applied on top of whatever the player
 * picked here.
 * <p>
 * Note the direction: a <i>smaller</i> value is zoomed <i>in</i> (a
 * magnified, narrower view), matching libGDX's own
 * {@code OrthographicCamera.zoom} convention — so "Zoom In" steps this
 * value <i>down</i>. See {@link #zoomIn()}/{@link #zoomOut()}.
 * <p>
 * {@link #save()} is a convenience wrapper around
 * {@link CameraSettingsStore#save}, matching
 * {@link de.mkoehler.starwars.audio.AudioSettings#save()}'s own "the live
 * object knows how to persist itself" convenience.
 */
public class CameraSettings {

    /** Most zoomed-<i>in</i> the player may go — 50% of the default zoom level, the user's own spec. */
    public static final float MIN_ZOOM = 0.5f;
    /** Most zoomed-<i>out</i> the player may go — 200% of the default zoom level, the user's own spec. */
    public static final float MAX_ZOOM = 2f;
    /**
     * Multiplier applied per Zoom In/Zoom Out keypress — geometric rather
     * than a fixed additive step so that in and out feel symmetric (the
     * same number of presses covers {@code 1 → 0.5} as covers
     * {@code 1 → 2}), which a linear step over an asymmetric range wouldn't.
     * At 1.1 that's ~7 presses from the default to either end. Untuned
     * starting point.
     */
    private static final float ZOOM_STEP = 1.1f;

    private float zoom = 1f;

    /**
     * No-arg constructor required by Jackson for deserialization.
     */
    public CameraSettings() {
    }

    /**
     * Returns the player's chosen zoom multiplier.
     *
     * @return the multiplier, in {@code [}{@link #MIN_ZOOM}{@code , }{@link #MAX_ZOOM}{@code ]}
     */
    public float getZoom() {
        return zoom;
    }

    /**
     * Sets the player's chosen zoom multiplier, clamped into
     * {@code [}{@link #MIN_ZOOM}{@code , }{@link #MAX_ZOOM}{@code ]} — so a
     * hand-edited or older saved file can never put the camera somewhere
     * the in-game keybinds couldn't.
     *
     * @param zoom the multiplier to use
     */
    public void setZoom(float zoom) {
        this.zoom = MathUtils.clamp(zoom, MIN_ZOOM, MAX_ZOOM);
    }

    /**
     * Steps the zoom one increment further <i>in</i> (a narrower, magnified
     * view), stopping at {@link #MIN_ZOOM}. Does not save on its own — the
     * caller does, same convention as {@link de.mkoehler.starwars.input.KeyBindings}.
     */
    public void zoomIn() {
        setZoom(zoom / ZOOM_STEP);
    }

    /**
     * Steps the zoom one increment further <i>out</i> (a wider view),
     * stopping at {@link #MAX_ZOOM}. Does not save on its own — the caller
     * does, same convention as {@link de.mkoehler.starwars.input.KeyBindings}.
     */
    public void zoomOut() {
        setZoom(zoom * ZOOM_STEP);
    }

    /**
     * Persists these settings locally, overwriting any previous save.
     */
    public void save() {
        CameraSettingsStore.save(this);
    }
}
