package de.mkoehler.starwars.net.messages;

/**
 * Broadcast by the server whenever a ship physically collides with
 * something worth a "thud" for (design.md — impact sounds): the arena
 * boundary, an asteroid, or another ship. Carries the position of the
 * colliding ship so every client can play one of the four shared impact
 * clips there, picked randomly and independently on each client — purely
 * cosmetic, so there's no need for every client to hear the exact same
 * sample.
 * <p>
 * Fires on any such contact, not just ones that deal damage (unlike
 * {@code ArenaBounds#wallImpactDamage}'s speed threshold) — this is a
 * physical-contact sound, not a damage indicator. Same "just a VFX/SFX
 * trigger" shape as {@link ProjectileHitMessage}/{@link MineDetonatedMessage}.
 * <p>
 * Sent to every connected client unconditionally, over the unreliable UDP
 * channel — dropping an occasional one is harmless for a purely cosmetic
 * effect, same reasoning as every other one-shot sound trigger here.
 */
public class ShipImpactMessage {

    private float x;
    private float y;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public ShipImpactMessage() {
    }

    /**
     * Creates a ship-impact message.
     *
     * @param x the colliding ship's position, in meters
     * @param y the colliding ship's position, in meters
     */
    public ShipImpactMessage(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Returns the colliding ship's position.
     *
     * @return the position, in meters
     */
    public float getX() {
        return x;
    }

    /**
     * Returns the colliding ship's position.
     *
     * @return the position, in meters
     */
    public float getY() {
        return y;
    }
}
