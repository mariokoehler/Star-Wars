package de.mkoehler.starwars.sim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.physics.box2d.World;
import de.mkoehler.starwars.sim.components.PhysicsBodyComponent;
import de.mkoehler.starwars.sim.components.ProjectileComponent;

/**
 * Removes projectiles once their lifetime expires (they missed everything).
 * Runs server-side only, once per tick — unlike collision-triggered
 * removals (see {@code GameNetworkServer}'s contact handling), this isn't
 * happening during a Box2D {@code world.step()} callback, so it's safe to
 * destroy bodies directly here.
 */
public class ProjectileLifetimeSystem extends IteratingSystem {

    private final ComponentMapper<PhysicsBodyComponent> bodyMapper = ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<ProjectileComponent> projectileMapper = ComponentMapper.getFor(ProjectileComponent.class);

    private final Engine engine;
    private final World world;

    /**
     * Creates the projectile lifetime system.
     *
     * @param engine the Ashley engine expired projectiles are removed from
     * @param world  the Box2D world expired projectiles' bodies are destroyed in
     */
    public ProjectileLifetimeSystem(Engine engine, World world) {
        super(Family.all(PhysicsBodyComponent.class, ProjectileComponent.class).get());
        this.engine = engine;
        this.world = world;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        ProjectileComponent projectile = projectileMapper.get(entity);
        projectile.tickLifetime(deltaTime);
        if (projectile.getRemainingLifetime() <= 0f) {
            world.destroyBody(bodyMapper.get(entity).getBody());
            engine.removeEntity(entity);
        }
    }
}
