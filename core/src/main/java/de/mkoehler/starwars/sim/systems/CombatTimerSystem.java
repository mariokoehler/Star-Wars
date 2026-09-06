package de.mkoehler.starwars.sim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import de.mkoehler.starwars.sim.components.CombatTimerComponent;

/**
 * Advances every ship's {@link CombatTimerComponent} each tick (design.md
 * 2.3's combat-lock rule for leaving a match via ESC). Runs server-side
 * only, once per tick, alongside {@code ShieldRegenSystem}.
 */
public class CombatTimerSystem extends IteratingSystem {

    private final ComponentMapper<CombatTimerComponent> combatTimerMapper = ComponentMapper.getFor(CombatTimerComponent.class);

    /**
     * Creates the combat timer system.
     */
    public CombatTimerSystem() {
        super(Family.all(CombatTimerComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        combatTimerMapper.get(entity).tick(deltaTime);
    }
}
