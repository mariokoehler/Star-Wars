package de.mkoehler.starwars.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies {@link DialogLayout}'s top-down-to-screen conversion and
 * scaled-centering math, using the actual Ship Selection screen numbers
 * (design.md 5.1): an 818x618 dialog, and its 384x384 portrait area.
 */
class DialogLayoutTest {

    @Test
    void topLeftCornerOfDialogMapsToScreenOrigin() {
        // An element the full size of its container, anchored at the container's own top-left
        // (0,0), should land exactly at the container's own screen position.
        assertEquals(100f, DialogLayout.toScreenX(100f, 0f));
        assertEquals(50f, DialogLayout.toScreenY(50f, 618f, 0f, 618f));
    }

    @Test
    void arrowAtSpecYLandsBelowTheDialogTop() {
        // The arrow buttons' top-left is at Y=124 (from the dialog's top), height 38 -
        // verifies against a hand-computed expectation rather than re-deriving the formula.
        float dialogScreenY = 200f;
        float dialogHeight = 618f;
        float screenY = DialogLayout.toScreenY(dialogScreenY, dialogHeight, 124f, 38f);
        assertEquals(dialogScreenY + 618f - 124f - 38f, screenY);
        assertEquals(656f, screenY);
    }

    @Test
    void elementAtDialogBottomEdgeMapsToContainerScreenY() {
        // topDownY + elementHeight == containerHeight means the element's bottom touches the
        // container's own bottom edge, i.e. screenY should equal the container's screenY exactly.
        assertEquals(200f, DialogLayout.toScreenY(200f, 618f, 618f - 38f, 38f));
    }

    @Test
    void squarePortraitScalesDownUniformlyToFitTheBox() {
        // The actual case: a 512x512 portrait fit into the 384x384 portrait area.
        DialogLayout.Fit fit = DialogLayout.fitCentered(384f, 384f, 512f, 512f);

        assertEquals(384f, fit.width());
        assertEquals(384f, fit.height());
        assertEquals(0f, fit.offsetX());
        assertEquals(0f, fit.offsetY());
    }

    @Test
    void nonSquareContentIsScaledByItsLimitingDimensionAndCenteredOnTheOther() {
        // Wider-than-tall content fit into a square box: width is the limiting dimension, so
        // the content should be centered vertically (a nonzero offsetY) but flush horizontally.
        DialogLayout.Fit fit = DialogLayout.fitCentered(200f, 200f, 400f, 100f);

        assertEquals(200f, fit.width());
        assertEquals(50f, fit.height());
        assertEquals(0f, fit.offsetX());
        assertEquals(75f, fit.offsetY());
    }

    @Test
    void contentSmallerThanTheBoxIsNeverScaledUp() {
        DialogLayout.Fit fit = DialogLayout.fitCentered(384f, 384f, 100f, 100f);

        assertEquals(100f, fit.width());
        assertEquals(100f, fit.height());
        assertEquals(142f, fit.offsetX());
    }
}
