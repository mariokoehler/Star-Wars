package de.mkoehler.starwars.sim.metadata;

/**
 * A single point in a ship sprite's local pixel space: the origin is the
 * sprite's own center, and Y increases upward — matching this project's
 * world/screen convention (design.md 4.3), not raw image/Swing pixel space
 * (origin top-left, Y down). Any tool producing these is responsible for
 * that conversion; consumers can use the values directly.
 * <p>
 * Plain mutable bean (public no-arg constructor, getters and setters) so
 * Jackson can (de)serialize it with no extra configuration.
 */
public class PixelPoint {

    private float x;
    private float y;

    /**
     * No-arg constructor required by Jackson for deserialization.
     */
    public PixelPoint() {
    }

    /**
     * Creates a point.
     *
     * @param x the X coordinate, in pixels from the sprite's center
     * @param y the Y coordinate, in pixels from the sprite's center, increasing upward
     */
    public PixelPoint(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Returns the X coordinate.
     *
     * @return the X coordinate, in pixels from the sprite's center
     */
    public float getX() {
        return x;
    }

    /**
     * Sets the X coordinate.
     *
     * @param x the X coordinate, in pixels from the sprite's center
     */
    public void setX(float x) {
        this.x = x;
    }

    /**
     * Returns the Y coordinate.
     *
     * @return the Y coordinate, in pixels from the sprite's center, increasing upward
     */
    public float getY() {
        return y;
    }

    /**
     * Sets the Y coordinate.
     *
     * @param y the Y coordinate, in pixels from the sprite's center, increasing upward
     */
    public void setY(float y) {
        this.y = y;
    }
}
