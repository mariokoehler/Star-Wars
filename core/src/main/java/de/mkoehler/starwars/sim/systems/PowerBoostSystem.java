package de.mkoehler.starwars.sim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import de.mkoehler.starwars.sim.components.PowerBoostComponent;

/**
 * Counts down every ship's {@link PowerBoostComponent} each tick (design.md —
 * power-ups' BOOST effect). Runs server-side only, once per tick — same
 * shape as {@code ShieldRegenSystem}/{@code CombatTimerSystem}.
 */
public class PowerBoostSystem extends IteratingSystem {

    private final ComponentMapper<PowerBoostComponent> boostMapper = ComponentMapper.getFor(PowerBoostComponent.class);

    /**
     * Creates the power boost system.
     */
    public PowerBoostSystem() {
        super(Family.all(PowerBoostComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        boostMapper.get(entity).tick(deltaTime);
    }
}
