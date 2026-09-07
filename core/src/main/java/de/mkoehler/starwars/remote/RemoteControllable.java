package de.mkoehler.starwars.remote;

import java.util.Map;

/**
 * Implemented by a {@link com.badlogic.gdx.Screen} that can be driven and
 * inspected by the embedded dev-only MCP server (design.md 3.13), instead of
 * only through real keyboard/mouse input and screenshots. A screen that
 * implements this registers itself with {@link RemoteControlRegistry} in
 * {@code show()} and unregisters in {@code dispose()}; screen-specific
 * actions (e.g. {@code ConnectScreen.remoteLogin}) live directly on the
 * implementing class, not here — this interface only covers what's common
 * to every remote-controllable screen: identifying itself and reporting its
 * current state.
 */
public interface RemoteControllable {

    /**
     * Returns a short, stable, human-readable name identifying this screen
     * (e.g. {@code "CONNECT"}) — not the Java class name, so it stays
     * readable in tool output and doesn't change if the class is renamed.
     *
     * @return this screen's name
     */
    String screenName();

    /**
     * Returns a snapshot of this screen's current state, as plain
     * JSON-serializable values (strings, numbers, booleans, nested maps/
     * lists) — whatever's useful for an MCP tool caller to inspect. Must
     * only be called from the render thread, same as any other read of live
     * Scene2D/libGDX state.
     * <p>
     * <b>Never include sensitive values</b> (e.g. password field contents)
     * — this is returned verbatim to whatever's driving the MCP client.
     *
     * @return this screen's state, as a JSON-serializable map
     */
    Map<String, Object> describeState();
}
