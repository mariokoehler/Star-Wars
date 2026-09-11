package de.mkoehler.starwars.sim;

import com.badlogic.gdx.physics.box2d.Filter;

/**
 * Box2D collision filter bits used across ship and projectile fixtures, so
 * projectiles don't physically collide with each other (they'd otherwise
 * generate pointless contact events every time two shots cross paths).
 * <p>
 * These bits are only actually enforced via {@link #shouldCollide} —
 * {@code GameNetworkServer} installs a custom {@code ContactFilter} (needed
 * for one specific exception, see that class's own comment), and doing so
 * <b>completely replaces</b> Box2D's native default category/mask/group
 * filtering rather than layering on top of it (confirmed by reading
 * {@code World.java}'s actual JNI binding: {@code setContactFilter(non-null)}
 * flips a native {@code useDefaultContactFilter} flag off, so the installed
 * Java callback becomes the <i>only</i> filter consulted). A custom filter
 * that doesn't itself replicate this logic — which is exactly what this
 * project's very first one did, for months, without anyone noticing — makes
 * every category/mask bit on every fixture in the game silently inert.
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

    /**
     * Category for asteroid fixtures (design.md — asteroids) — masked into
     * ship and projectile fixtures only, not into the arena boundary or
     * other asteroids: an asteroid never collides with the boundary (it
     * simply despawns and respawns once it drifts past it,
     * {@code GameNetworkServer#tickAsteroids}) or with another asteroid
     * (the user's own spec).
     */
    public static final short ASTEROID = 0x0008;

    /**
     * Category for a power-up's <b>physical</b> fixture (design.md —
     * power-ups) — masked into the arena boundary, asteroid, and projectile
     * fixtures, but deliberately never into a ship's fixture: a power-up
     * must stay physically inert to a ship touching it (very light bodies
     * getting flung around by contact would make "touch to pick up" feel
     * twitchy), so ship pickup detection instead goes through a separate,
     * sensor fixture on the same body sharing this same category (see
     * {@code PowerUpFactory}) — a sensor never produces collision response
     * regardless of category/mask bits, only contact events.
     */
    public static final short POWERUP = 0x0010;

    /**
     * Category for a mine's fixture (design.md — mines) — masked into ship,
     * projectile, asteroid, and power-up fixtures, but deliberately not the
     * arena boundary: a mine is a permanently stationary
     * {@link com.badlogic.gdx.physics.box2d.BodyDef.BodyType#StaticBody},
     * spawned at least {@link SpawnPointFinder#BOUNDARY_MARGIN_METERS}
     * inside the arena edge and never moving afterward, so it can never
     * actually reach the boundary — masking that pairing in would be dead
     * weight. Its one fixture is a sensor (see {@code MineFactory}), same
     * "detect contact without any real collision response" reasoning as a
     * power-up's own sensor fixture — a mine is destroyed the instant
     * anything touches it, so there's no point giving a static, infinite-
     * mass body real collision response first.
     */
    public static final short MINE = 0x0020;

    private CollisionCategories() {
    }

    /**
     * Returns whether two fixtures' filter data would let them collide, by
     * Box2D's own standard default rule — a group-index override, if both
     * share a nonzero group, otherwise a symmetric category/mask AND check.
     * Pure and Box2D-native-free (a plain {@link Filter} needs no native
     * init to construct), so it's directly unit-testable — see this class's
     * own Javadoc for why replicating this exactly is necessary at all: any
     * custom {@code ContactFilter} bypasses Box2D's native version of this
     * same check entirely.
     *
     * @param filterA one fixture's filter data
     * @param filterB the other fixture's filter data
     * @return {@code true} if a contact between them should be created
     */
    public static boolean shouldCollide(Filter filterA, Filter filterB) {
        if (filterA.groupIndex == filterB.groupIndex && filterA.groupIndex != 0) {
            return filterA.groupIndex > 0;
        }
        return (filterA.maskBits & filterB.categoryBits) != 0
            && (filterA.categoryBits & filterB.maskBits) != 0;
    }
}
