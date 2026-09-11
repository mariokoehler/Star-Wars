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
            | CollisionCategories.ARENA_BOUNDARY | CollisionCategories.ASTEROID | CollisionCategories.POWERUP
            | CollisionCategories.MINE));

    // Mirrors ProjectileFactory/MissileFactory's fixtureDef.filter exactly.
    private static final Filter PROJECTILE = filter(CollisionCategories.PROJECTILE,
        (short) (CollisionCategories.SHIP | CollisionCategories.ASTEROID | CollisionCategories.POWERUP
            | CollisionCategories.MINE));

    // Mirrors ArenaBounds.createBoundary's fixtureDef.filter exactly.
    private static final Filter ARENA_BOUNDARY = filter(CollisionCategories.ARENA_BOUNDARY,
        (short) (CollisionCategories.SHIP | CollisionCategories.POWERUP));

    // Mirrors AsteroidFactory.createAsteroid's fixtureDef.filter exactly.
    private static final Filter ASTEROID = filter(CollisionCategories.ASTEROID,
        (short) (CollisionCategories.SHIP | CollisionCategories.PROJECTILE | CollisionCategories.POWERUP
            | CollisionCategories.MINE));

    // Mirrors PowerUpFactory.createPowerUp's two fixtures exactly - the physical one (real mass,
    // never masks in SHIP) and the sensor one (isSensor=true, masks in SHIP only).
    private static final Filter POWERUP_PHYSICAL = filter(CollisionCategories.POWERUP,
        (short) (CollisionCategories.ARENA_BOUNDARY | CollisionCategories.ASTEROID | CollisionCategories.PROJECTILE
            | CollisionCategories.MINE));
    private static final Filter POWERUP_SENSOR = filter(CollisionCategories.POWERUP, CollisionCategories.SHIP);

    // Mirrors MineFactory.createMine's one (sensor) fixture exactly.
    private static final Filter MINE = filter(CollisionCategories.MINE,
        (short) (CollisionCategories.SHIP | CollisionCategories.PROJECTILE | CollisionCategories.ASTEROID
            | CollisionCategories.POWERUP));

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
    void powerUpPhysicalFixtureCollidesWithTheArenaBoundaryAsteroidsAndProjectiles() {
        assertTrue(CollisionCategories.shouldCollide(POWERUP_PHYSICAL, ARENA_BOUNDARY));
        assertTrue(CollisionCategories.shouldCollide(ARENA_BOUNDARY, POWERUP_PHYSICAL));
        assertTrue(CollisionCategories.shouldCollide(POWERUP_PHYSICAL, ASTEROID));
        assertTrue(CollisionCategories.shouldCollide(ASTEROID, POWERUP_PHYSICAL));
        assertTrue(CollisionCategories.shouldCollide(POWERUP_PHYSICAL, PROJECTILE));
        assertTrue(CollisionCategories.shouldCollide(PROJECTILE, POWERUP_PHYSICAL));
    }

    @Test
    void powerUpPhysicalFixtureNeverCollidesWithAShip() {
        // The whole point of the two-fixture design (design.md - power-ups): a ship touching a
        // power-up must never get a real Box2D collision response, only the sensor below.
        assertFalse(CollisionCategories.shouldCollide(POWERUP_PHYSICAL, SHIP));
        assertFalse(CollisionCategories.shouldCollide(SHIP, POWERUP_PHYSICAL));
    }

    @Test
    void powerUpSensorFixtureOnlyCollidesWithShips() {
        assertTrue(CollisionCategories.shouldCollide(POWERUP_SENSOR, SHIP));
        assertTrue(CollisionCategories.shouldCollide(SHIP, POWERUP_SENSOR));
        assertFalse(CollisionCategories.shouldCollide(POWERUP_SENSOR, ARENA_BOUNDARY));
        assertFalse(CollisionCategories.shouldCollide(POWERUP_SENSOR, ASTEROID));
        assertFalse(CollisionCategories.shouldCollide(POWERUP_SENSOR, PROJECTILE));
    }

    @Test
    void mineCollidesWithShipsProjectilesAsteroidsAndPowerUpsPhysicalFixture() {
        assertTrue(CollisionCategories.shouldCollide(MINE, SHIP));
        assertTrue(CollisionCategories.shouldCollide(SHIP, MINE));
        assertTrue(CollisionCategories.shouldCollide(MINE, PROJECTILE));
        assertTrue(CollisionCategories.shouldCollide(PROJECTILE, MINE));
        assertTrue(CollisionCategories.shouldCollide(MINE, ASTEROID));
        assertTrue(CollisionCategories.shouldCollide(ASTEROID, MINE));
        assertTrue(CollisionCategories.shouldCollide(MINE, POWERUP_PHYSICAL));
        assertTrue(CollisionCategories.shouldCollide(POWERUP_PHYSICAL, MINE));
    }

    @Test
    void mineNeverCollidesWithTheArenaBoundaryOrAPowerUpsSensorFixture() {
        // A mine is spawned inside the boundary margin and never moves (design.md - mines), so
        // this pairing can never actually be consulted in practice - kept as a test anyway, same
        // as asteroidsDoNotCollideWithTheArenaBoundary, to keep CollisionCategories' own filter
        // constants honest.
        assertFalse(CollisionCategories.shouldCollide(MINE, ARENA_BOUNDARY));
        assertFalse(CollisionCategories.shouldCollide(ARENA_BOUNDARY, MINE));
        assertFalse(CollisionCategories.shouldCollide(MINE, POWERUP_SENSOR));
        assertFalse(CollisionCategories.shouldCollide(POWERUP_SENSOR, MINE));
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
