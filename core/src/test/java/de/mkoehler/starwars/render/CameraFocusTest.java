package de.mkoehler.starwars.render;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link CameraFocus}' weighted-centroid focus point: its
 * degenerate "nothing in sight" case, the plain midpoint/centroid results
 * the feature is specified in terms of, the outer fade band, and the
 * offset clamp.
 */
class CameraFocusTest {

    private static final float TOLERANCE = 1e-3f;

    /** Large enough that the clamp never engages in a test that isn't about the clamp. */
    private static final float NO_CLAMP = 100_000f;
    /** Full-weight radius used by the tests that aren't about the fade band itself. */
    private static final float FULL_WEIGHT = 1_000f;
    /** Zero-weight radius used by the tests that aren't about the fade band itself. */
    private static final float FALLOFF = 2_000f;

    private final Vector2 out = new Vector2();

    @Test
    void withNothingInSightFocusesOnThePlayer() {
        Vector2 focus = CameraFocus.computeFocus(
            300f, -120f, Collections.emptyList(), FULL_WEIGHT, FALLOFF, NO_CLAMP, out);

        assertEquals(300f, focus.x, TOLERANCE);
        assertEquals(-120f, focus.y, TOLERANCE);
    }

    @Test
    void writesIntoAndReturnsTheSuppliedVector() {
        Vector2 focus = CameraFocus.computeFocus(
            1f, 2f, Collections.emptyList(), FULL_WEIGHT, FALLOFF, NO_CLAMP, out);

        assertSame(out, focus);
    }

    @Test
    void oneFullWeightContactGivesTheExactMidpoint() {
        // The feature's own "two players" description: the halfway point of the line between them.
        List<Vector2> contacts = Collections.singletonList(new Vector2(100f, 200f));

        Vector2 focus = CameraFocus.computeFocus(0f, 0f, contacts, FULL_WEIGHT, FALLOFF, NO_CLAMP, out);

        assertEquals(50f, focus.x, TOLERANCE);
        assertEquals(100f, focus.y, TOLERANCE);
    }

    @Test
    void twoFullWeightContactsGiveTheTriangleCentroid() {
        // The feature's own "three players" description: the centroid of the triangle they span.
        List<Vector2> contacts = Arrays.asList(new Vector2(300f, 0f), new Vector2(0f, 300f));

        Vector2 focus = CameraFocus.computeFocus(0f, 0f, contacts, FULL_WEIGHT, FALLOFF, NO_CLAMP, out);

        assertEquals(100f, focus.x, TOLERANCE);
        assertEquals(100f, focus.y, TOLERANCE);
    }

    @Test
    void aContactRightAtTheFullWeightRadiusStillCountsInFull() {
        List<Vector2> contacts = Collections.singletonList(new Vector2(100f, 0f));

        Vector2 focus = CameraFocus.computeFocus(0f, 0f, contacts, 100f, 200f, NO_CLAMP, out);

        assertEquals(50f, focus.x, TOLERANCE);
    }

    @Test
    void aContactBeyondTheFalloffHasNoInfluenceAtAll() {
        List<Vector2> contacts = Collections.singletonList(new Vector2(500f, 500f));

        Vector2 focus = CameraFocus.computeFocus(0f, 0f, contacts, 50f, 100f, NO_CLAMP, out);

        assertEquals(0f, focus.x, TOLERANCE);
        assertEquals(0f, focus.y, TOLERANCE);
    }

    @Test
    void aContactExactlyAtTheFalloffHasNoInfluenceEither() {
        // The reason the fade band exists: a contact reaching the edge of detection contributes
        // nothing *and* was contributing almost nothing a moment earlier, so it can vanish from
        // the contact list without the focus point jumping.
        List<Vector2> contacts = Collections.singletonList(new Vector2(100f, 0f));

        Vector2 focus = CameraFocus.computeFocus(0f, 0f, contacts, 50f, 100f, NO_CLAMP, out);

        assertEquals(0f, focus.x, TOLERANCE);
        assertEquals(0f, focus.y, TOLERANCE);
    }

    @Test
    void aContactHalfwayThroughTheFadeBandPullsWithHalfWeight() {
        // Distance 75 through a 50..100 fade band - weight 0.5, so the centroid is
        // (0 * 1 + 75 * 0.5) / 1.5 = 25.
        List<Vector2> contacts = Collections.singletonList(new Vector2(75f, 0f));

        Vector2 focus = CameraFocus.computeFocus(0f, 0f, contacts, 50f, 100f, NO_CLAMP, out);

        assertEquals(25f, focus.x, TOLERANCE);
        assertEquals(0f, focus.y, TOLERANCE);
    }

    @Test
    void theFocusMovesTowardTheContactNotAwayFromIt() {
        // Guards the sign of the offset - a flipped one would still produce a plausible-looking
        // magnitude while pointing the camera at empty space behind the player.
        List<Vector2> contacts = Collections.singletonList(new Vector2(-50f, -50f));

        Vector2 focus = CameraFocus.computeFocus(0f, 0f, contacts, FULL_WEIGHT, FALLOFF, NO_CLAMP, out);

        assertTrue(focus.x < 0f, "expected the focus to move toward the contact, got x=" + focus.x);
        assertTrue(focus.y < 0f, "expected the focus to move toward the contact, got y=" + focus.y);
    }

    @Test
    void theOffsetIsClampedToTheMaximumWithoutChangingDirection() {
        // An unclamped centroid would sit 200px along +X (the midpoint of 0 and 400) - the clamp
        // cuts that to 30 while keeping the direction.
        List<Vector2> contacts = Collections.singletonList(new Vector2(400f, 0f));

        Vector2 focus = CameraFocus.computeFocus(0f, 0f, contacts, FULL_WEIGHT, FALLOFF, 30f, out);

        assertEquals(30f, focus.x, TOLERANCE);
        assertEquals(0f, focus.y, TOLERANCE);
    }

    @Test
    void theClampMeasuresFromThePlayerNotFromTheOrigin() {
        // Same geometry as above, translated far from the world origin - the clamped focus must
        // stay exactly `maxOffset` from the player, not from (0, 0).
        List<Vector2> contacts = Collections.singletonList(new Vector2(10_400f, 10_000f));

        Vector2 focus = CameraFocus.computeFocus(
            10_000f, 10_000f, contacts, FULL_WEIGHT, FALLOFF, 30f, out);

        assertEquals(10_030f, focus.x, TOLERANCE);
        assertEquals(10_000f, focus.y, TOLERANCE);
    }

    @Test
    void anOffsetAlreadyInsideTheClampIsNotPushedOutToIt() {
        // Centroid offset here is 10px (the midpoint of 0 and 20), well inside a 30px clamp - the
        // clamp must leave it alone rather than normalizing every offset to its own length.
        List<Vector2> contacts = Collections.singletonList(new Vector2(20f, 0f));

        Vector2 focus = CameraFocus.computeFocus(0f, 0f, contacts, FULL_WEIGHT, FALLOFF, 30f, out);

        assertEquals(10f, focus.x, TOLERANCE);
        assertEquals(0f, focus.y, TOLERANCE);
    }

    @Test
    void contactsOnOppositeSidesCancelOut() {
        // Two equally-weighted contacts mirrored about the player - the centroid lands back on
        // the player, which is exactly the "keep the whole fight framed" behavior wanted.
        List<Vector2> contacts = Arrays.asList(new Vector2(100f, 0f), new Vector2(-100f, 0f));

        Vector2 focus = CameraFocus.computeFocus(0f, 0f, contacts, FULL_WEIGHT, FALLOFF, NO_CLAMP, out);

        assertEquals(0f, focus.x, TOLERANCE);
        assertEquals(0f, focus.y, TOLERANCE);
    }

    @Test
    void aContactExactlyOnThePlayerLeavesTheFocusThere() {
        // Zero-length offset - guards the clamp's own division by the offset length.
        List<Vector2> contacts = Collections.singletonList(new Vector2(250f, 250f));

        Vector2 focus = CameraFocus.computeFocus(250f, 250f, contacts, FULL_WEIGHT, FALLOFF, 30f, out);

        assertEquals(250f, focus.x, TOLERANCE);
        assertEquals(250f, focus.y, TOLERANCE);
    }
}
