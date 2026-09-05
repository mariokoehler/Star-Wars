package de.mkoehler.starwars.sim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import de.mkoehler.starwars.sim.PhysicsConstants;
import de.mkoehler.starwars.sim.components.PhysicsBodyComponent;
import de.mkoehler.starwars.sim.components.SpriteComponent;

/**
 * Draws every entity with both a {@link PhysicsBodyComponent} and a
 * {@link SpriteComponent}, positioned and rotated to match its Box2D body.
 * <p>
 * Draws at the body's position/angle interpolated via
 * {@link PhysicsSystem#getAlpha()}, not its raw current state — see
 * {@link PhysicsBodyComponent} for why.
 */
public class RenderSystem extends IteratingSystem {

    private final SpriteBatch batch;
    private final OrthographicCamera camera;
    private final PhysicsSystem physicsSystem;

    private final ComponentMapper<PhysicsBodyComponent> bodyMapper = ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<SpriteComponent> spriteMapper = ComponentMapper.getFor(SpriteComponent.class);

    /**
     * Creates the render system.
     *
     * @param batch         the batch to draw with; not owned by this system,
     *                      the caller is responsible for disposing it
     * @param camera        the camera whose combined matrix is applied before
     *                      drawing
     * @param physicsSystem the physics system stepping the same entities'
     *                      bodies, used for its interpolation alpha
     */
    public RenderSystem(SpriteBatch batch, OrthographicCamera camera, PhysicsSystem physicsSystem) {
        super(Family.all(PhysicsBodyComponent.class, SpriteComponent.class).get());
        this.batch = batch;
        this.camera = camera;
        this.physicsSystem = physicsSystem;
    }

    @Override
    public void update(float deltaTime) {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        super.update(deltaTime);
        batch.end();
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        PhysicsBodyComponent physics = bodyMapper.get(entity);
        SpriteComponent sprite = spriteMapper.get(entity);
        float alpha = physicsSystem.getAlpha();

        Vector2 position = physics.getInterpolatedPosition(alpha);
        float angle = physics.getInterpolatedAngle(alpha);

        float widthPixels = sprite.getWidthMeters() * PhysicsConstants.PIXELS_PER_METER;
        float heightPixels = sprite.getHeightMeters() * PhysicsConstants.PIXELS_PER_METER;
        float xPixels = position.x * PhysicsConstants.PIXELS_PER_METER;
        float yPixels = position.y * PhysicsConstants.PIXELS_PER_METER;

        // The source art faces up/north when unrotated (design.md 4.3), and PlayerInputSystem
        // treats angle 0 as "facing north" too (see its FORWARD vector) - so the body's angle
        // maps directly onto the region's rotation with no offset needed.
        batch.draw(sprite.getRegion(),
            xPixels - widthPixels / 2f, yPixels - heightPixels / 2f,
            widthPixels / 2f, heightPixels / 2f,
            widthPixels, heightPixels,
            1f, 1f,
            angle * MathUtils.radiansToDegrees);
    }
}
