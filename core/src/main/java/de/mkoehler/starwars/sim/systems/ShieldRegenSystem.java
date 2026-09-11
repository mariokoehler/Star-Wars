package de.mkoehler.starwars.sim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import de.mkoehler.starwars.sim.PowerSystem;
import de.mkoehler.starwars.sim.components.PowerBoostComponent;
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
    private final ComponentMapper<PowerBoostComponent> boostMapper = ComponentMapper.getFor(PowerBoostComponent.class);

    /**
     * Creates the shield regen system.
     */
    public ShieldRegenSystem() {
        super(Family.all(ShieldComponent.class, PowerDistributionComponent.class, PowerBoostComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        // Design.md - power-ups' BOOST effect, same "multiply on top of the distribution's own
        // multiplier" treatment as ShipControlSystem's engines multiplier.
        float multiplier = powerMapper.get(entity).getDistribution().multiplierFor(PowerSystem.SHIELDS)
            * boostMapper.get(entity).getMultiplier();
        shieldMapper.get(entity).regenerate(deltaTime, multiplier);
    }
}
