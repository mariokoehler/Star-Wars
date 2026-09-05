package de.mkoehler.starwars.server;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import de.mkoehler.starwars.net.NetworkConstants;
import de.mkoehler.starwars.net.NetworkServer;

import java.io.IOException;

/**
 * Headless libGDX application hosting the dedicated server. Starts the
 * {@link NetworkServer} in {@link #create()} and stops it in {@link #dispose()};
 * {@link #render()} drives the fixed-rate loop the authoritative simulation
 * will eventually run on (see design.md 3.5).
 */
public class GameServer extends ApplicationAdapter {

    private static final String TAG = "GameServer";

    private NetworkServer networkServer;
    private long frameCount;

    @Override
    public void create() {
        networkServer = new NetworkServer();
        try {
            networkServer.start(NetworkConstants.TCP_PORT, NetworkConstants.UDP_PORT);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start network server on TCP "
                + NetworkConstants.TCP_PORT + " / UDP " + NetworkConstants.UDP_PORT, e);
        }
        Gdx.app.log(TAG, "Listening on TCP " + NetworkConstants.TCP_PORT
            + " / UDP " + NetworkConstants.UDP_PORT);
    }

    @Override
    public void render() {
        frameCount++;
        if (frameCount % NetworkConstants.SIMULATION_TICK_RATE_HZ == 0) {
            Gdx.app.log(TAG, "Tick " + frameCount + " (" + (frameCount / NetworkConstants.SIMULATION_TICK_RATE_HZ) + "s uptime)");
        }
    }

    @Override
    public void dispose() {
        networkServer.stop();
    }
}
