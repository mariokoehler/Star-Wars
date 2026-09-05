package de.mkoehler.starwars;

import com.badlogic.gdx.Game;

/**
 * The actual {@code ApplicationListener} for the desktop client, now that
 * there's more than one {@link com.badlogic.gdx.Screen} (design.md 5.1) —
 * {@link Client} (gameplay) previously filled this role directly back when
 * it was the only screen that existed.
 * <p>
 * {@link Game} takes care of delegating {@code render}/{@code resize}/
 * {@code pause}/{@code resume}/{@code dispose} to whichever {@link
 * com.badlogic.gdx.Screen} is current; switching screens (and disposing the
 * one being left) is each screen's own responsibility, not this class's —
 * see {@link ShipSelectionScreen}'s Start handling for where that happens.
 */
public class StarWarsGame extends Game {

    @Override
    public void create() {
        setScreen(new ShipSelectionScreen(this));
    }
}
