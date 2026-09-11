package de.mkoehler.starwars.net.messages;

/**
 * One mine's position at the moment a {@link WorldSnapshotMessage} was
 * built (design.md — mines). Not sent on its own, only as an element of
 * that message's mine list. Broadcast unfiltered to every connected
 * player, same scope-boundary reasoning as {@link AsteroidState}/
 * {@link PowerUpState} — not gated by radar.
 * <p>
 * No angle/velocity fields, unlike an asteroid or power-up — a mine is
 * permanently stationary and non-rotating once spawned (design.md —
 * mines: a static Box2D body, {@code MineFactory}), and its rotating
 * animation is a purely client-side visual (a baked 64-frame sprite
 * sequence played on a loop), not driven by any server-authoritative
 * angle.
 * <p>
 * A mine has no destroyed/despawned notification of its own in this
 * message — that's {@link MineDetonatedMessage}'s job (it also carries
 * the explosion's position, for VFX); a client otherwise infers a mine is
 * gone simply by its id no longer appearing in a subsequent snapshot,
 * same as an asteroid or power-up.
 */
public class MineState {

    private int mineId;
    private float x;
    private float y;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public MineState() {
    }

    /**
     * Creates a mine state entry.
     *
     * @param mineId this mine's id, unique among currently-active mines
     * @param x      the mine's position, in meters
     * @param y      the mine's position, in meters
     */
    public MineState(int mineId, float x, float y) {
        this.mineId = mineId;
        this.x = x;
        this.y = y;
    }

    /**
     * Returns this mine's id.
     *
     * @return the mine id
     */
    public int getMineId() {
        return mineId;
    }

    /**
     * Returns the mine's X position.
     *
     * @return the position, in meters
     */
    public float getX() {
        return x;
    }

    /**
     * Returns the mine's Y position.
     *
     * @return the position, in meters
     */
    public float getY() {
        return y;
    }
}
