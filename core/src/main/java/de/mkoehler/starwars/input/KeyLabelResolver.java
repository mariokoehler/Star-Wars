package de.mkoehler.starwars.input;

/**
 * Resolves a {@link com.badlogic.gdx.Input.Keys} keycode to the label
 * {@link de.mkoehler.starwars.KeybindScreen} should show for it — a seam
 * so a platform backend that can do better than
 * {@link com.badlogic.gdx.Input.Keys#toString(int)}'s hardcoded US-layout
 * name (see {@link KeyLabels}'s Javadoc) can plug in without {@code core}
 * needing to depend on that platform's own libraries.
 */
public interface KeyLabelResolver {

    /**
     * Returns the label to display for {@code keycode}.
     *
     * @param keycode the {@link com.badlogic.gdx.Input.Keys} keycode
     * @return a short, human-readable label
     */
    String getLabel(int keycode);
}
