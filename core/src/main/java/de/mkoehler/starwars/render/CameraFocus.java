package de.mkoehler.starwars.render;

import com.badlogic.gdx.math.Vector2;

import java.util.List;

/**
 * The "battlefield camera" focus point (design.md 4.1) — where the camera
 * should be easing toward, given the local player's own position and every
 * enemy ship currently visible to them.
 * <p>
 * With nothing in sight this is simply the player's own position (the
 * plain chase camera the game has always had). With one or more contacts
 * visible it becomes a <i>weighted centroid</i> of the player plus those
 * contacts: two ships give the midpoint of the line between them, three
 * give the centroid of their triangle, and so on — pulling the view toward
 * the action so the player keeps more of the fight on screen instead of
 * staring at empty space behind them.
 * <p>
 * Two refinements keep that from throwing the player off their own screen:
 * <ul>
 *   <li><b>Distance falloff.</b> A contact counts at full weight out to
 *       {@code fullWeightPixels} — inside that band the focus point is
 *       exactly the plain centroid described above — and then fades
 *       linearly to zero weight at {@code falloffPixels}. The fade band
 *       exists so a contact drifting out of relevance (or out of radar
 *       detection entirely) loses its pull gradually instead of the focus
 *       point jumping the moment it crosses a threshold; it is deliberately
 *       the <i>outer</i> part of the range only, so the ordinary
 *       close-quarters case keeps the exact centroid behavior rather than a
 *       distance-distorted version of it.</li>
 *   <li><b>Offset clamp.</b> The final focus point is never further from
 *       the player than {@code maxOffsetPixels}, which the caller derives
 *       from the camera's <i>current</i> visible extent — so the player
 *       stays comfortably framed at any zoom level, manual or speed-driven.
 *       In a close fight this is normally the binding constraint: the
 *       camera leans as far toward the centroid as it is allowed to, in the
 *       centroid's direction.</li>
 * </ul>
 * The player's own weight is a fixed {@code 1}, so the total weight is
 * always at least {@code 1} and the average is always well-defined.
 * <p>
 * Everything here is in <b>render pixels</b>, not meters — that is what
 * {@code camera.position} and the client's per-ship render positions
 * already use.
 */
public final class CameraFocus {

    private CameraFocus() {
    }

    /**
     * Computes the point the camera should ease toward this frame.
     *
     * @param playerX           the local player's own render X position, in pixels
     * @param playerY           the local player's own render Y position, in pixels
     * @param contacts          every currently-visible other ship's render position, in
     *                          pixels; may be empty, in which case the player's own
     *                          position is returned unchanged
     * @param fullWeightPixels  distance out to which a contact counts at full weight;
     *                          beyond it its weight fades linearly toward zero
     * @param falloffPixels     distance at which a contact's influence has faded to
     *                          zero; a contact at or beyond it contributes nothing.
     *                          Must be greater than {@code fullWeightPixels}
     * @param maxOffsetPixels   how far the returned point may sit from the player's
     *                          own position at most
     * @param out               vector the result is written into, and returned — avoids
     *                          an allocation per frame
     * @return {@code out}, holding the focus point in pixels
     */
    public static Vector2 computeFocus(float playerX, float playerY, List<Vector2> contacts,
                                       float fullWeightPixels, float falloffPixels,
                                       float maxOffsetPixels, Vector2 out) {
        // The player's own position at weight 1 - see the class Javadoc for why the
        // accumulator starts there rather than at zero.
        float weightSum = 1f;
        float weightedX = playerX;
        float weightedY = playerY;

        for (Vector2 contact : contacts) {
            float dx = contact.x - playerX;
            float dy = contact.y - playerY;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            float weight = weightFor(distance, fullWeightPixels, falloffPixels);
            if (weight <= 0f) {
                continue;
            }
            weightSum += weight;
            weightedX += contact.x * weight;
            weightedY += contact.y * weight;
        }

        float offsetX = weightedX / weightSum - playerX;
        float offsetY = weightedY / weightSum - playerY;

        float offsetLength = (float) Math.sqrt(offsetX * offsetX + offsetY * offsetY);
        if (offsetLength > maxOffsetPixels && offsetLength > 0f) {
            float scale = maxOffsetPixels / offsetLength;
            offsetX *= scale;
            offsetY *= scale;
        }

        return out.set(playerX + offsetX, playerY + offsetY);
    }

    /**
     * How much a contact at {@code distance} counts toward the centroid:
     * {@code 1} out to {@code fullWeightPixels}, fading linearly to
     * {@code 0} at {@code falloffPixels}, and {@code 0} beyond it.
     *
     * @param distance         the contact's distance from the player, in pixels
     * @param fullWeightPixels the full-weight radius, in pixels
     * @param falloffPixels    the zero-weight radius, in pixels
     * @return the contact's weight, in {@code [0, 1]}
     */
    private static float weightFor(float distance, float fullWeightPixels, float falloffPixels) {
        if (distance <= fullWeightPixels) {
            return 1f;
        }
        if (distance >= falloffPixels) {
            return 0f;
        }
        return (falloffPixels - distance) / (falloffPixels - fullWeightPixels);
    }
}
