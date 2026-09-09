package de.mkoehler.starwars.input;

import com.badlogic.gdx.Input;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The on-disk shape of the local keybinds file (design.md 3.8) — one
 * {@link Input.Keys} keycode per {@link GameAction#name()}. Plain mutable
 * bean (public no-arg constructor, getters and setters) so Jackson can
 * (de)serialize it with no extra configuration, same convention as this
 * project's other Jackson beans (e.g. {@link de.mkoehler.starwars.net.ConnectionConfig}).
 * <p>
 * Keyed by action <i>name</i> rather than a raw {@code Map<GameAction, Integer>}
 * so the file stays readable/hand-editable, and so an action added or
 * removed in a later version doesn't break loading an older file — see
 * {@link KeyBindings#load()}, which only ever reads entries matching a
 * currently-known {@link GameAction}, silently ignoring anything else.
 */
public class KeyBindingsConfig {

    private Map<String, Integer> bindings = new LinkedHashMap<>();

    /**
     * No-arg constructor required by Jackson for deserialization.
     */
    public KeyBindingsConfig() {
    }

    public Map<String, Integer> getBindings() {
        return bindings;
    }

    public void setBindings(Map<String, Integer> bindings) {
        this.bindings = bindings;
    }
}
