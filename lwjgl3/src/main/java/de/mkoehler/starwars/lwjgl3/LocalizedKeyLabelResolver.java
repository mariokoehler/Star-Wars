package de.mkoehler.starwars.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.DefaultLwjgl3Input;
import de.mkoehler.starwars.input.KeyLabelResolver;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves a key to the character the player's <b>actual OS keyboard
 * layout</b> would produce for it, instead of {@link Input.Keys#toString(int)}'s
 * hardcoded US-layout name — design.md 3.8's addendum, driven directly by
 * a user report: on this project's own German QWERTZ dev machine, the
 * physical key labeled "Z" (swapped with "Y" relative to a US layout)
 * correctly <i>bound</i> as "Y" (libGDX/GLFW report keycodes by physical
 * position, which is exactly what makes the "press a key" capture in
 * {@link de.mkoehler.starwars.KeybindScreen} layout-safe in the first
 * place — see that class's Javadoc) but also <i>displayed</i> as "Y",
 * which reads as wrong to a player who just pressed "Z". This resolver
 * fixes the display only; what's captured and persisted is untouched
 * (still the physical/US keycode, for the same cross-layout-safety
 * reason as always).
 * <p>
 * <b>Why this lives here, in {@code lwjgl3}, and not in {@code core}
 * alongside {@link de.mkoehler.starwars.input.KeyLabels}:</b> the only
 * way to ask the OS for a layout-aware key name is GLFW's
 * {@code glfwGetKeyName}, a direct LWJGL3/GLFW dependency {@code core}
 * has no business carrying (it's shared with the headless {@code server}
 * module, which never touches a keyboard at all). {@code Lwjgl3Launcher}
 * installs this resolver via {@link de.mkoehler.starwars.input.KeyLabels#setResolver}
 * at startup; {@code core} stays unaware of GLFW entirely.
 * <p>
 * {@code glfwGetKeyName} takes a <i>GLFW</i> keycode, not a GDX one, and
 * libGDX only ever exposes the opposite direction publicly
 * ({@link DefaultLwjgl3Input#getGdxKeyCode(int)}, GLFW → GDX). {@link #reverseMap()}
 * inverts it once, lazily (not before {@link Gdx#input} exists, which
 * isn't true yet at this resolver's own construction time — see
 * {@code Lwjgl3Launcher}), by calling that same method for every GLFW
 * keycode and recording where each GDX keycode came from, rather than
 * hand-duplicating libGDX's own ~100-case mapping table (and risking it
 * silently drifting out of sync on a future libGDX upgrade).
 */
public class LocalizedKeyLabelResolver implements KeyLabelResolver {

    private Map<Integer, Integer> gdxToGlfw;

    @Override
    public String getLabel(int keycode) {
        Integer glfwKeycode = reverseMap().get(keycode);
        if (glfwKeycode != null) {
            // GLFW returns null for a key with no printable representation on the current
            // layout (function keys, arrows, modifiers, etc.) - Input.Keys.toString's own
            // hardcoded name is exactly the right fallback for those, same as before this
            // resolver existed.
            String localized = GLFW.glfwGetKeyName(glfwKeycode, 0);
            if (localized != null && !localized.isEmpty()) {
                return localized;
            }
        }
        return Input.Keys.toString(keycode);
    }

    /**
     * Builds (once) and returns the GDX-keycode → GLFW-keycode map,
     * inverting {@link DefaultLwjgl3Input#getGdxKeyCode(int)}.
     *
     * @return the reverse mapping
     */
    private Map<Integer, Integer> reverseMap() {
        if (gdxToGlfw != null) {
            return gdxToGlfw;
        }
        Map<Integer, Integer> map = new HashMap<>();
        // Gdx.input is a DefaultLwjgl3Input on this backend by construction (Lwjgl3Window sets
        // it in its constructor) - guarded rather than assumed, so a call before the window
        // exists degrades to "always fall back to Input.Keys.toString" instead of crashing.
        if (Gdx.input instanceof DefaultLwjgl3Input lwjgl3Input) {
            for (int glfwKey = 0; glfwKey <= GLFW.GLFW_KEY_LAST; glfwKey++) {
                int gdxKey = lwjgl3Input.getGdxKeyCode(glfwKey);
                if (gdxKey != Input.Keys.UNKNOWN) {
                    map.putIfAbsent(gdxKey, glfwKey);
                }
            }
        }
        gdxToGlfw = map;
        return map;
    }
}
