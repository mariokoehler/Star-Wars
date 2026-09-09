package de.mkoehler.starwars.sim;

import com.badlogic.gdx.physics.box2d.Filter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link CollisionCategories#shouldCollide} against every fixture
 * pairing this project's Box2D bodies actually use — see that method's own
 * Javadoc for why this needs its own explicit replication/test at all (a
 * custom {@code ContactFilter}, once installed, is the <i>only</i> filter
 * Box2D consults; these category/mask bits mean nothing unless something
 * replicates the default check itself).
 */
class CollisionCategoriesTest {

    // Mirrors ShipFactory.createBody's fixtureDef.filter exactly.
    private static final Filter SHIP = filter(CollisionCategories.SHIP,
        (short) (CollisionCategories.SHIP | CollisionCategories.PROJECTILE
            | CollisionCategories.ARENA_BOUNDARY | CollisionCategories.ASTEROID));

    // Mirrors ProjectileFactory/MissileFactory's fixtureDef.filter exactly.
    private static final Filter PROJECTILE = filter(CollisionCategories.PROJECTILE,
        (short) (CollisionCategories.SHIP | CollisionCategories.ASTEROID));

    // Mirrors ArenaBounds.createBoundary's fixtureDef.filter exactly.
    private static final Filter ARENA_BOUNDARY = filter(CollisionCategories.ARENA_BOUNDARY, CollisionCategories.SHIP);

    // Mirrors AsteroidFactory.createAsteroid's fixtureDef.filter exactly.
    private static final Filter ASTEROID = filter(CollisionCategories.ASTEROID,
        (short) (CollisionCategories.SHIP | CollisionCategories.PROJECTILE));

    @Test
    void shipsCollideWithEachOther() {
        assertTrue(CollisionCategories.shouldCollide(SHIP, SHIP));
    }

    @Test
    void shipsCollideWithTheArenaBoundary() {
        assertTrue(CollisionCategories.shouldCollide(SHIP, ARENA_BOUNDARY));
        assertTrue(CollisionCategories.shouldCollide(ARENA_BOUNDARY, SHIP));
    }

    @Test
    void shipsCollideWithProjectiles() {
        assertTrue(CollisionCategories.shouldCollide(SHIP, PROJECTILE));
        assertTrue(CollisionCategories.shouldCollide(PROJECTILE, SHIP));
    }

    @Test
    void shipsCollideWithAsteroids() {
        assertTrue(CollisionCategories.shouldCollide(SHIP, ASTEROID));
        assertTrue(CollisionCategories.shouldCollide(ASTEROID, SHIP));
    }

    @Test
    void projectilesCollideWithAsteroids() {
        assertTrue(CollisionCategories.shouldCollide(PROJECTILE, ASTEROID));
        assertTrue(CollisionCategories.shouldCollide(ASTEROID, PROJECTILE));
    }

    @Test
    void projectilesDoNotCollideWithEachOther() {
        assertFalse(CollisionCategories.shouldCollide(PROJECTILE, PROJECTILE));
    }

    @Test
    void projectilesDoNotCollideWithTheArenaBoundary() {
        assertFalse(CollisionCategories.shouldCollide(PROJECTILE, ARENA_BOUNDARY));
        assertFalse(CollisionCategories.shouldCollide(ARENA_BOUNDARY, PROJECTILE));
    }

    @Test
    void asteroidsDoNotCollideWithEachOther() {
        assertFalse(CollisionCategories.shouldCollide(ASTEROID, ASTEROID));
    }

    @Test
    void asteroidsDoNotCollideWithTheArenaBoundary() {
        assertFalse(CollisionCategories.shouldCollide(ASTEROID, ARENA_BOUNDARY));
        assertFalse(CollisionCategories.shouldCollide(ARENA_BOUNDARY, ASTEROID));
    }

    @Test
    void aNonzeroMatchingGroupIndexOverridesTheMaskBitsEntirely() {
        Filter always = filter((short) 0, (short) 0);
        always.groupIndex = 5;
        Filter alsoAlways = filter((short) 0, (short) 0);
        alsoAlways.groupIndex = 5;
        assertTrue(CollisionCategories.shouldCollide(always, alsoAlways));

        Filter never = filter((short) 1, (short) -1);
        never.groupIndex = -3;
        Filter alsoNever = filter((short) 1, (short) -1);
        alsoNever.groupIndex = -3;
        assertFalse(CollisionCategories.shouldCollide(never, alsoNever));
    }

    private static Filter filter(short categoryBits, short maskBits) {
        Filter filter = new Filter();
        filter.categoryBits = categoryBits;
        filter.maskBits = maskBits;
        return filter;
    }
}
