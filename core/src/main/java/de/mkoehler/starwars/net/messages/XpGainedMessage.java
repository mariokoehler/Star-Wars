package de.mkoehler.starwars.net.messages;

/**
 * Sent by the server to a single player's own connection whenever their
 * account is credited XP, for any reason (design.md — floating XP text),
 * purely so that player's own client can show a generic floating "+N XP"
 * text above their ship. The server's {@code AccountStore} is still the
 * sole source of truth for the actual persisted total — this message never
 * carries a running total, only the amount just credited.
 * <p>
 * Deliberately generic on the amount/reason: currently only sent for kill
 * XP (design.md 2.10), via {@code GameNetworkServer.awardXp}, but any future
 * XP source should funnel through that same method and get this client
 * feedback for free, with no further wire changes needed.
 * <p>
 * Unicast (via {@code Connection.sendTCP}) to the earning player's own
 * connection only, not broadcast — the reliable/ordered channel, since
 * unlike a purely cosmetic SFX trigger (design.md's other one-shot
 * messages), dropping this one would silently skip real feedback for a
 * real account change.
 */
public class XpGainedMessage {

    private int playerId;
    private int amount;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public XpGainedMessage() {
    }

    /**
     * Creates an XP-gained message.
     *
     * @param playerId the earning player's id
     * @param amount   the XP amount just credited
     */
    public XpGainedMessage(int playerId, int amount) {
        this.playerId = playerId;
        this.amount = amount;
    }

    /**
     * Returns the earning player's id.
     *
     * @return the player id
     */
    public int getPlayerId() {
        return playerId;
    }

    /**
     * Returns the XP amount just credited.
     *
     * @return the XP amount
     */
    public int getAmount() {
        return amount;
    }
}
