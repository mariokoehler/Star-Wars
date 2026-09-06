package de.mkoehler.starwars.render;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Picks a random Death Screen quote index (design.md 5.1) without
 * repeating one until every other quote has been shown - a classic
 * "shuffle bag": each {@link #next()} deals one index off a shuffled
 * deck, reshuffling a fresh deck of every index only once the current
 * one is empty. Session-local only (in-memory, never persisted) -
 * owned by {@code StarWarsGame} so it survives across repeated deaths
 * (each its own {@code DeathScreen} instance) for as long as the app
 * keeps running, per the user's request.
 */
public class QuoteDeck {

    private final int quoteCount;
    private final Random random;
    private final Deque<Integer> remaining = new ArrayDeque<>();

    /**
     * Creates a deck over indices {@code [0, quoteCount)}.
     *
     * @param quoteCount the total number of distinct quotes to deal from
     * @param random     the source of randomness (injectable for
     *                   deterministic tests)
     */
    public QuoteDeck(int quoteCount, Random random) {
        this.quoteCount = quoteCount;
        this.random = random;
    }

    /**
     * Deals the next quote index. Never repeats an index already dealt
     * this cycle - once every index has been dealt once, reshuffles a
     * fresh full deck before dealing again.
     *
     * @return a quote index in {@code [0, quoteCount)}
     */
    public int next() {
        if (remaining.isEmpty()) {
            refill();
        }
        return remaining.pop();
    }

    private void refill() {
        List<Integer> indices = new ArrayList<>(quoteCount);
        for (int i = 0; i < quoteCount; i++) {
            indices.add(i);
        }
        Collections.shuffle(indices, random);
        remaining.addAll(indices);
    }
}
