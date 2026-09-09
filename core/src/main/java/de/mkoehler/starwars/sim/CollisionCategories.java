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
