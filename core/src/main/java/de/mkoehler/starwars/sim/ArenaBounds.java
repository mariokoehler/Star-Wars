package de.mkoehler.starwars.sim;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.ChainShape;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.World;

/**
 * The combat arena's physical boundary (design.md — arena bounds): a fixed
 * {@value #SIZE_METERS}×{@value #SIZE_METERS}m square, centered on the
 * origin, that ships bounce off rather than fly past — chosen over a
 * wrap-around boundary since a discernible edge is easier to orient
 * yourself against than an invisible teleporting one, and large enough
 * (relative to every ship type's own radar range, 60-200m) that finding an
 * opponent takes real searching rather than always being trivial.
 * <p>
 * {@link #createBoundary(World)} builds one static body with a single
 * {@link ChainShape} fixture looping through the square's 4 corners — the
 * standard, idiomatic Box2D way to build a level boundary (a closed chain
 * costs Box2D essentially nothing regardless of its physical size; there's
 * no reason to reach for several long, thin box fixtures instead). Called
 * identically by both {@code GameNetworkServer}'s authoritative
 * {@link World} and {@code Client}'s local-prediction {@code localWorld} —
 * same "shared code, can't diverge" reasoning as {@link ShipFactory} —
 * since a bounce the client predicts differently from what the server
 * actually does would otherwise fight reconciliation every time a ship
 * touches a wall.
 * <p>
 * Only masked into ship fixtures ({@link CollisionCategories#ARENA_BOUNDARY}) —
 * a projectile/missile simply keeps flying past the edge and expires on its
 * own lifetime timer, rather than needing new hit-detection/despawn logic
 * for a case that essentially never matters (nobody's out there to hit).
 * <p>
 * {@link #wallImpactDamage(float)} (design.md — arena bounds' addendum) is
 * the "faceplanting into the wall actually hurts" half of this feature —
 * {@code GameNetworkServer} calls it from its own {@code ContactListener}
 * with the ship's speed at the moment contact begins, same shape as its
 * existing projectile-hit detection (a pure damage-amount calculation here,
 * the actual {@link ShipDamage#apply} / kill-check / body-teardown sequence
 * stays server-side, deferred outside the contact callback the same way a
 * projectile hit already is).
 */
public final class ArenaBounds {

    /** The arena's full width/height, in meters — untuned starting point, not yet playtested. */
    public static final float SIZE_METERS = 500f;

    /** Half of {@link #SIZE_METERS} — the arena spans {@code [-HALF_SIZE_METERS, HALF_SIZE_METERS]} on each axis. */
    public static final float HALF_SIZE_METERS = SIZE_METERS / 2f;

    /**
     * How bouncy the boundary wall is. Set on the wall's own fixture, not
     * the ship's (which stays at its existing low {@code 0.2f}, tuned for
     * soft ship-vs-ship bumps) — Box2D mixes two fixtures' restitution via
     * {@code max(...)} by default, so this value alone determines how hard
     * a wall bounce actually feels, independent of what any given ship's
     * own restitution happens to be. Untuned starting point.
     */
    private static final float WALL_RESTITUTION = 0.6f;

    /**
     * Near-zero, deliberately — keeps a wall bounce a clean reflection of
     * the ship's velocity rather than inducing spin from an off-angle hit.
     * A more chaotic "glancing impact" feel could reintroduce friction here
     * later, but isn't what a first pass calls for.
     */
    private static final float WALL_FRICTION = 0f;

    /**
     * Impact speed below which a wall bounce is "free" — no damage — so an
     * ordinary boundary bounce during normal maneuvering never costs
     * anything; only a genuinely careless/hard hit does. Untuned starting
     * point, same as every other combat number in this project.
     */
    private static final float MIN_DAMAGE_IMPACT_SPEED_METERS_PER_SECOND = 20f;

    /**
     * Damage per m/s of impact speed above {@link #MIN_DAMAGE_IMPACT_SPEED_METERS_PER_SECOND}
     * — untuned starting point, chosen so a genuinely fast, deliberate
     * faceplant can plausibly kill a ship outright (a hull/shield pool of
     * roughly 100+100 by default), while the threshold above already keeps
     * routine bounces harmless.
     */
    private static final float DAMAGE_PER_METER_PER_SECOND = 1.5f;

    private ArenaBounds() {
    }

    /**
     * Computes the hull/shield damage (see {@link ShipDamage#apply}) a ship
     * takes from hitting the boundary wall at {@code impactSpeedMetersPerSecond}
     * — a pure linear-above-a-threshold formula, {@code 0} at or below
     * {@link #MIN_DAMAGE_IMPACT_SPEED_METERS_PER_SECOND}.
     *
     * @param impactSpeedMetersPerSecond the ship's speed at the moment of impact
     * @return the damage to apply, never negative
     */
    public static float wallImpactDamage(float impactSpeedMetersPerSecond) {
        return Math.max(0f, impactSpeedMetersPerSecond - MIN_DAMAGE_IMPACT_SPEED_METERS_PER_SECOND)
            * DAMAGE_PER_METER_PER_SECOND;
    }

    /**
     * Creates the arena's boundary body in {@code world} — a closed
     * {@link ChainShape} looping through the square's 4 corners.
     *
     * @param world the Box2D world to create the boundary in
     * @return the created boundary body — {@code GameNetworkServer} keeps
     * this reference to identify a ship-vs-boundary contact by body
     * identity in its own {@code ContactListener}
     */
    public static Body createBoundary(World world) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.StaticBody;
        Body body = world.createBody(bodyDef);

        ChainShape shape = new ChainShape();
        float h = HALF_SIZE_METERS;
        shape.createLoop(new float[] {
            -h, -h,
            h, -h,
            h, h,
            -h, h
        });

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.friction = WALL_FRICTION;
        fixtureDef.restitution = WALL_RESTITUTION;
        fixtureDef.filter.categoryBits = CollisionCategories.ARENA_BOUNDARY;
        // POWERUP added alongside SHIP (design.md - power-ups): a power-up bounces off the
        // boundary too, same as a ship, just never damaged by it (GameNetworkServer's own
        // registerPotentialWallHit guards on PlayerIdComponent before treating a contact as a
        // damageable wall hit).
        fixtureDef.filter.maskBits = (short) (CollisionCategories.SHIP | CollisionCategories.POWERUP);
        body.createFixture(fixtureDef);

        shape.dispose();
        return body;
    }
}
