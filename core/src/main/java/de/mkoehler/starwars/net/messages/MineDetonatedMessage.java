package de.mkoehler.starwars.net.messages;

/**
 * Broadcast by the server whenever a mine detonates (design.md — mines),
 * carrying the position of the explosion so every client can play the
 * same big explosion particle effect + sound already used for a ship's
 * own destruction ({@code GameAssets#EXPLOSION_PARTICLE}/
 * {@code GameAssets#EXPLOSION_SOUND}) — the user's own explicit ask, to
 * reuse existing VFX rather than author anything new for this.
 * <p>
 * Carries no mine id — same "just a VFX trigger" shape as
 * {@link ProjectileHitMessage}; a client doesn't need to know which mine
 * detonated, it infers the mine is gone the normal way (its id no longer
 * appearing in the next {@link WorldSnapshotMessage}).
 * <p>
 * Sent to every connected client unconditionally, over the unreliable UDP
 * channel — same reasoning as {@link ProjectileHitMessage}: dropping an
 * occasional one is harmless for a purely cosmetic effect.
 */
public class MineDetonatedMessage {

    private float x;
    private float y;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public MineDetonatedMessage() {
    }

    /**
     * Creates a mine-detonated message.
     *
     * @param x the explosion position, in meters
     * @param y the explosion position, in meters
     */
    public MineDetonatedMessage(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Returns the explosion position.
     *
     * @return the position, in meters
     */
    public float getX() {
        return x;
    }

    /**
     * Returns the explosion position.
     *
     * @return the position, in meters
     */
    public float getY() {
        return y;
    }
}
