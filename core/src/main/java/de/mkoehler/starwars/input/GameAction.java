package de.mkoehler.starwars.input;

import com.badlogic.gdx.Input;

/**
 * Every gameplay keybind {@link KeyBindings} lets the player remap
 * (design.md 3.8/5.2) — one entry per action, carrying both a
 * human-readable label (shown on the Keybind Settings screen) and the
 * keycode it's bound to by default on a fresh install.
 * <p>
 * <b>Deliberately excludes</b> ESC (leave match/cancel), the cursor keys,
 * and ENTER — none of those are used for anything on the gameplay screen
 * ({@link de.mkoehler.starwars.Client}) itself, and every keyboard/layout
 * has an ESC key, so there's no internationalization reason to expose it
 * here (design.md 5.2). The left/right arrow keys and ENTER remapped by a
 * player would also be confusing, since they're reused as fixed
 * navigation/confirm keys on other screens (Ship Selection, Connect,
 * Death Screen) that this enum has no relationship to at all.
 * <p>
 * <b>The zoom defaults are the numpad's own {@code +}/{@code -} keys</b>,
 * deliberately: they are the only keys physically labeled "+" and "-" on
 * both a US and a German layout (the main-row "+"/"-" sit on entirely
 * different physical keys per layout, and {@link Input.Keys#PLUS} itself is
 * a keycode the lwjgl3 backend never actually emits — see
 * {@code DefaultLwjgl3Input.getGdxKeyCode}). A player without a numpad
 * rebinds them like any other action.
 * <p>
 * Ordering here is purely presentational — {@link KeybindScreen} (via
 * {@link #values()}) lists actions in this declared order, grouped
 * loosely by theme (movement, combat, systems, UI) for readability.
 */
public enum GameAction {

    THRUST_FORWARD("Thrust Forward", Input.Keys.W),
    TURN_LEFT("Turn Left", Input.Keys.A),
    TURN_RIGHT("Turn Right", Input.Keys.D),

    FIRE_WEAPON("Fire Weapon", Input.Keys.SPACE),
    TOGGLE_TURRET("Toggle Turret", Input.Keys.T),
    FIRE_MISSILE("Fire Missile", Input.Keys.M),

    RADAR_PULSE("Radar Pulse", Input.Keys.R),
    POWER_SHIELDS("Power: Shields", Input.Keys.J),
    POWER_WEAPONS("Power: Weapons", Input.Keys.I),
    POWER_ENGINES("Power: Engines", Input.Keys.L),
    POWER_RESET("Power: Reset Distribution", Input.Keys.K),

    SHOW_SCOREBOARD("Show Scoreboard", Input.Keys.TAB),
    SHOW_DISPLAY_NAMES("Show Player Names", Input.Keys.N),

    ZOOM_IN("Zoom In", Input.Keys.NUMPAD_ADD),
    ZOOM_OUT("Zoom Out", Input.Keys.NUMPAD_SUBTRACT);

    private final String displayName;
    private final int defaultKeycode;

    GameAction(String displayName, int defaultKeycode) {
        this.displayName = displayName;
        this.defaultKeycode = defaultKeycode;
    }

    /**
     * Returns the label shown for this action on the Keybind Settings screen.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the {@link Input.Keys} keycode this action is bound to on a
     * fresh install, or after "Reset to Defaults" is pressed.
     *
     * @return the default keycode
     */
    public int getDefaultKeycode() {
        return defaultKeycode;
    }
}
