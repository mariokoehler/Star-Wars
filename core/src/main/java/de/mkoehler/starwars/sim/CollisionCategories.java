package de.mkoehler.starwars.sim;

/**
 * Box2D collision filter bits used across ship and projectile fixtures, so
 * projectiles don't physically collide with each other (they'd otherwise
 * generate pointless contact events every time two shots cross paths).
 *
 * @see com.badlogic.gdx.physics.box2d.Filter
 */
public final class CollisionCategories {

    /**
     * Category for ship fixtures.
     */
    public static final short SHIP = 0x0001;

    /**
     * Category for projectile fixtures.
     */
    public static final short PROJECTILE = 0x0002;

    private CollisionCategories() {
    }
}
