package de.mkoehler.starwars.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

import java.util.EnumMap;
import java.util.Map;

/**
 * The player's own live keybinds (design.md 3.8) — one {@link Input.Keys}
 * keycode per {@link GameAction}, mutable, and always kept persisted: every
 * {@link #rebind}/{@link #resetToDefaults} call in {@link de.mkoehler.starwars.KeybindScreen}
 * saves immediately afterward (design.md 5.2 — "changes save immediately to
 * the local keybinds file", no separate Save button).
 * <p>
 * Owned for the whole app run by {@link de.mkoehler.starwars.StarWarsGame}
 * (see {@link de.mkoehler.starwars.StarWarsGame#getKeyBindings()}) — every
 * screen that reads or edits a binding shares this exact instance, same
 * "state that outlives any one screen" convention already used there for
 * the shared {@code AssetManager}/{@code QuoteDeck}.
 * <p>
 * {@link de.mkoehler.starwars.Client} reads bindings via {@link #isPressed}/
 * {@link #isJustPressed} in place of every hardcoded {@code Gdx.input.isKeyPressed(Input.Keys.*)}
 * call it used to make for a gameplay action — see that class's own
 * history for exactly which ones. <b>ESC (leave match) stays hardcoded
 * there</b>, deliberately never routed through here — see
 * {@link GameAction}'s Javadoc for why.
 */
public class KeyBindings {

    private final EnumMap<GameAction, Integer> keycodes = new EnumMap<>(GameAction.class);

    private KeyBindings() {
    }

    /**
     * Creates a fresh set of bindings at every {@link GameAction}'s own
     * default keycode.
     *
     * @return a new, all-default {@link KeyBindings}
     */
    public static KeyBindings defaults() {
        KeyBindings bindings = new KeyBindings();
        bindings.resetToDefaults();
        return bindings;
    }

    /**
     * Creates a {@link KeyBindings}, starting from {@link #defaults()} and
     * overlaying whatever {@link KeyBindingsStore#load()} finds locally
     * saved — an action with no saved entry (a first launch, or one added
     * in a version after the save was made) simply keeps its default.
     *
     * @return the effective bindings to use for this run
     */
    public static KeyBindings load() {
        KeyBindings bindings = defaults();
        KeyBindingsStore.load().ifPresent(config -> {
            for (GameAction action : GameAction.values()) {
                Integer keycode = config.getBindings().get(action.name());
                if (keycode != null) {
                    bindings.keycodes.put(action, keycode);
                }
            }
        });
        return bindings;
    }

    /**
     * Resets every action back to its {@link GameAction#getDefaultKeycode()}.
     * Does <b>not</b> save on its own — {@link de.mkoehler.starwars.KeybindScreen}'s
     * "Reset to Defaults" button calls {@link #save()} itself right after,
     * same as every other mutation here.
     */
    public void resetToDefaults() {
        for (GameAction action : GameAction.values()) {
            keycodes.put(action, action.getDefaultKeycode());
        }
    }

    /**
     * Returns the keycode currently bound to {@code action}.
     *
     * @param action the action to look up
     * @return its current {@link Input.Keys} keycode
     */
    public int get(GameAction action) {
        return keycodes.get(action);
    }

    /**
     * Rebinds {@code action} to {@code newKeycode}. If {@code newKeycode} is
     * already bound to a <i>different</i> action, that other action is
     * given {@code action}'s previous keycode instead (a swap) — so no key
     * is ever left bound to two actions at once, which would otherwise mean
     * a single keypress silently triggering both (e.g. rebinding "Fire
     * Missile" onto "T" would make every future press of "T" both toggle
     * the turret <i>and</i> fire a missile, with no indication why).
     *
     * @param action     the action being rebound
     * @param newKeycode the keycode to bind it to
     */
    public void rebind(GameAction action, int newKeycode) {
        int previousKeycode = keycodes.get(action);
        if (previousKeycode == newKeycode) {
            return;
        }
        for (Map.Entry<GameAction, Integer> entry : keycodes.entrySet()) {
            if (entry.getKey() != action && entry.getValue() == newKeycode) {
                entry.setValue(previousKeycode);
                break;
            }
        }
        keycodes.put(action, newKeycode);
    }

    /**
     * Persists the current bindings locally, overwriting any previous save.
     */
    public void save() {
        KeyBindingsConfig config = new KeyBindingsConfig();
        Map<String, Integer> map = config.getBindings();
        for (Map.Entry<GameAction, Integer> entry : keycodes.entrySet()) {
            map.put(entry.getKey().name(), entry.getValue());
        }
        KeyBindingsStore.save(config);
    }

    /**
     * Equivalent to {@code Gdx.input.isKeyPressed(get(action))} — whether
     * {@code action}'s bound key is currently held down.
     *
     * @param action the action to check
     * @return {@code true} if its bound key is currently held
     */
    public boolean isPressed(GameAction action) {
        return Gdx.input.isKeyPressed(get(action));
    }

    /**
     * Equivalent to {@code Gdx.input.isKeyJustPressed(get(action))} —
     * whether {@code action}'s bound key was pressed down this frame.
     *
     * @param action the action to check
     * @return {@code true} if its bound key was just pressed this frame
     */
    public boolean isJustPressed(GameAction action) {
        return Gdx.input.isKeyJustPressed(get(action));
    }
}
