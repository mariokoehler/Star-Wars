package de.mkoehler.starwars.sim.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * Associates an entity with the texture region drawn to represent it, and the
 * size, in meters, that region should be drawn at.
 */
public class SpriteComponent implements Component {

    private final TextureRegion region;
    private final float widthMeters;
    private final float heightMeters;

    /**
     * Creates a sprite component.
     *
     * @param region       the texture region to draw
     * @param widthMeters  the width to draw the region at, in Box2D meters
     * @param heightMeters the height to draw the region at, in Box2D meters
     */
    public SpriteComponent(TextureRegion region, float widthMeters, float heightMeters) {
        this.region = region;
        this.widthMeters = widthMeters;
        this.heightMeters = heightMeters;
    }

    /**
     * Returns the texture region drawn for this entity.
     *
     * @return the entity's texture region
     */
    public TextureRegion getRegion() {
        return region;
    }

    /**
     * Returns the width this entity is drawn at.
     *
     * @return the draw width, in meters
     */
    public float getWidthMeters() {
        return widthMeters;
    }

    /**
     * Returns the height this entity is drawn at.
     *
     * @return the draw height, in meters
     */
    public float getHeightMeters() {
        return heightMeters;
    }
}
