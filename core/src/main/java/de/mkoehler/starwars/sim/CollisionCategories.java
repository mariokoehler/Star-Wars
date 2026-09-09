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

    /**
     * Category for the arena boundary fixture ({@link ArenaBounds}) — only
     * masked into ship fixtures (design.md — arena bounds), so a
     * projectile/missile simply keeps flying past the edge and expires on
     * its own lifetime timer instead of bouncing or being destroyed there.
     */
    public static final short ARENA_BOUNDARY = 0x0004;

    private CollisionCategories() {
    }
}
