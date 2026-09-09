package de.mkoehler.starwars.input;

import com.badlogic.gdx.Input;

/**
 * The single point {@link de.mkoehler.starwars.KeybindScreen} asks for a
 * key's display label — defaults to {@link Input.Keys#toString(int)}
 * (design.md 3.8's originally-accepted v1 limitation: a hardcoded
 * US-layout name, since libGDX/GLFW reports keycodes by physical key
 * position, not the character the OS layout actually produces), but
 * {@code lwjgl3}'s {@code Lwjgl3Launcher} overrides it at startup with a
 * resolver that asks GLFW for the localized character instead (design.md
 * 3.8's addendum — {@code core} can't do this itself, since it would need
 * a direct GLFW dependency, which the headless {@code server} module has
 * no business carrying).
 * <p>
 * {@code core} is not otherwise aware this override exists — if
 * {@link #setResolver} is never called (e.g. a hypothetical future
 * backend that hasn't wired one up yet), this simply keeps behaving
 * exactly as it always did.
 */
public final class KeyLabels {

    private static KeyLabelResolver resolver = Input.Keys::toString;

    private KeyLabels() {
    }

    /**
     * Installs a platform-specific resolver, replacing the default
     * {@link Input.Keys#toString(int)} behavior. Intended to be called
     * once, early in startup (before any key label is actually needed —
     * see {@link de.mkoehler.starwars.KeybindScreen}).
     *
     * @param resolver the resolver to use from now on
     */
    public static void setResolver(KeyLabelResolver resolver) {
        KeyLabels.resolver = resolver;
    }

    /**
     * Returns the label to display for {@code keycode}, via whichever
     * resolver is currently installed.
     *
     * @param keycode the {@link Input.Keys} keycode
     * @return a short, human-readable label
     */
    public static String getLabel(int keycode) {
        return resolver.getLabel(keycode);
    }
}
