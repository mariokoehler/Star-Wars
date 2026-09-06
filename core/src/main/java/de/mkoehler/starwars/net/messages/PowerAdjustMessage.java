package de.mkoehler.starwars.net.messages;

import de.mkoehler.starwars.sim.PowerSystem;

/**
 * Sent once per discrete power-distribution keybind action (design.md
 * 2.2/5.3: a tap adjusts by one increment, a held key maximizes, K resets)
 * — over the reliable, ordered channel, unlike movement/firing input (which
 * is re-sent every tick, so a dropped UDP packet there is harmless): each of
 * these is a discrete one-shot event, and losing or reordering one would
 * silently change the resulting power split. See
 * {@code de.mkoehler.starwars.sim.components.PowerDistributionComponent}'s
 * Javadoc for why this reliable/ordered delivery is what keeps the sending
 * client's own locally-mirrored state and the server's authoritative state
 * from ever diverging.
 */
public class PowerAdjustMessage {

    /**
     * Which power-distribution action this message carries.
     */
    public enum Kind {
        /** One increment toward {@link #getTarget()}, see {@code PowerDistribution#adjust}. */
        ADJUST,
        /** Jump {@link #getTarget()} straight to its maximum, see {@code PowerDistribution#maximize}. */
        MAXIMIZE,
        /** Back to the even baseline; {@link #getTarget()} is unused/null. */
        RESET
    }

    private Kind kind;
    private PowerSystem target;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public PowerAdjustMessage() {
    }

    /**
     * Creates a power-adjust message.
     *
     * @param kind   which action this message carries
     * @param target the system to act on; unused for {@link Kind#RESET}, may be {@code null} then
     */
    public PowerAdjustMessage(Kind kind, PowerSystem target) {
        this.kind = kind;
        this.target = target;
    }

    /**
     * Returns which power-distribution action this message carries.
     *
     * @return the action kind
     */
    public Kind getKind() {
        return kind;
    }

    /**
     * Returns the system this action targets.
     *
     * @return the target system, or {@code null} for a {@link Kind#RESET}
     */
    public PowerSystem getTarget() {
        return target;
    }
}
