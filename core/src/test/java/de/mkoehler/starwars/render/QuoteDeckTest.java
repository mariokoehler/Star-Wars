package de.mkoehler.starwars.render;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuoteDeckTest {

    @Test
    void dealsEveryIndexExactlyOnceBeforeRepeating() {
        QuoteDeck deck = new QuoteDeck(5, new Random(1));

        Set<Integer> seenInFirstCycle = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            int next = deck.next();
            assertTrue(next >= 0 && next < 5);
            assertTrue(seenInFirstCycle.add(next), "index " + next + " repeated within the first cycle");
        }
        assertEquals(Set.of(0, 1, 2, 3, 4), seenInFirstCycle);
    }

    @Test
    void reshufflesAFreshFullDeckOnceExhausted() {
        QuoteDeck deck = new QuoteDeck(3, new Random(42));

        for (int i = 0; i < 3; i++) {
            deck.next();
        }

        Set<Integer> secondCycle = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            secondCycle.add(deck.next());
        }
        assertEquals(Set.of(0, 1, 2), secondCycle);
    }

    @Test
    void singleQuoteAlwaysReturnsTheSameIndex() {
        QuoteDeck deck = new QuoteDeck(1, new Random(7));

        for (int i = 0; i < 5; i++) {
            assertEquals(0, deck.next());
        }
    }

    @Test
    void ordersDifferAcrossDifferentRandomSeeds() {
        // Not a hard guarantee for every seed pair, but true for these two -
        // enough to confirm the shuffle actually uses the supplied Random
        // rather than always dealing in index order.
        QuoteDeck deckA = new QuoteDeck(10, new Random(1));
        QuoteDeck deckB = new QuoteDeck(10, new Random(2));

        StringBuilder orderA = new StringBuilder();
        StringBuilder orderB = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            orderA.append(deckA.next());
            orderB.append(deckB.next());
        }

        assertTrue(!orderA.toString().equals(orderB.toString()));
    }
}
