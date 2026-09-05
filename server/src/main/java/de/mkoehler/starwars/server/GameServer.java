package de.mkoehler.starwars.server;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import de.mkoehler.starwars.net.NetworkConstants;

import java.io.IOException;

/**
 * Headless libGDX application hosting the dedicated server. Starts the
 * {@link GameNetworkServer} in {@link #create()} and stops it in
 * {@link #dispose()}; {@link #render()} drives its fixed-rate simulation tick
 * (see design.md 3.5) at whatever rate the headless application is
 * configured for ({@link NetworkConstants#SIMULATION_TICK_RATE_HZ}).
 */
public class GameServer extends ApplicationAdapter {

    private static final String TAG = "GameServer";

    private GameNetworkServer networkServer;

    @Override
    public void create() {
        networkServer = new GameNetworkServer();
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
        networkServer.tick(Gdx.graphics.getDeltaTime());
    }

    @Override
    public void dispose() {
        networkServer.stop();
    }
}
