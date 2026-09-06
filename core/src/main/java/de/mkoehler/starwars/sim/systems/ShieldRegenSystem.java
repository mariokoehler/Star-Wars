package de.mkoehler.starwars.sim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import de.mkoehler.starwars.sim.PowerSystem;
import de.mkoehler.starwars.sim.components.PowerDistributionComponent;
import de.mkoehler.starwars.sim.components.ShieldComponent;

/**
 * Recharges every ship's shield at its per-second rate, scaled by that
 * ship's current {@link PowerSystem#SHIELDS} power allocation (design.md
 * 2.2/2.5). Runs server-side only, once per tick, after that tick's hits are
 * resolved — a ship's shield starts regenerating again immediately, with no
 * regen-delay-after-hit mechanic yet (deliberately not built until it's
 * asked for).
 */
public class ShieldRegenSystem extends IteratingSystem {

    private final ComponentMapper<ShieldComponent> shieldMapper = ComponentMapper.getFor(ShieldComponent.class);
    private final ComponentMapper<PowerDistributionComponent> powerMapper = ComponentMapper.getFor(PowerDistributionComponent.class);

    /**
     * Creates the shield regen system.
     */
    public ShieldRegenSystem() {
        super(Family.all(ShieldComponent.class, PowerDistributionComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        float multiplier = powerMapper.get(entity).getDistribution().multiplierFor(PowerSystem.SHIELDS);
        shieldMapper.get(entity).regenerate(deltaTime, multiplier);
    }
}
