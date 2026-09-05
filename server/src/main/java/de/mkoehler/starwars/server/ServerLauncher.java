package de.mkoehler.starwars.server;

import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import de.mkoehler.starwars.net.NetworkConstants;

/**
 * Entry point for the dedicated server process.
 */
public final class ServerLauncher {

    private ServerLauncher() {
    }

    /**
     * Boots the headless libGDX application hosting {@link GameServer}.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        HeadlessApplicationConfiguration config = new HeadlessApplicationConfiguration();
        config.updatesPerSecond = NetworkConstants.SIMULATION_TICK_RATE_HZ;
        new HeadlessApplication(new GameServer(), config);
    }
}
