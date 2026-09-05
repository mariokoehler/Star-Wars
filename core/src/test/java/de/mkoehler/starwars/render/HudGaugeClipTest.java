package de.mkoehler.starwars.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies {@link HudGaugeClip}'s bottom-anchored "fuel gauge" reveal math,
 * using the actual X-wing HUD numbers (design.md 2.5): a shield overlay
 * visible between pixel rows 130 and 437 of a 512px-tall image, rendered
 * into a 220px on-screen widget.
 */
class HudGaugeClipTest {

    private static final float CLIP_TOP = 130f;
    private static final float CLIP_BOTTOM = 437f;
    private static final float TEXTURE_HEIGHT = 512f;
    private static final float WIDGET_SIZE = 220f;
    private static final float WIDGET_SCREEN_Y = 24f;

    @Test
    void fullFractionRevealsTheEntireClipRange() {
        HudGaugeClip.Result result = HudGaugeClip.compute(1f, CLIP_TOP, CLIP_BOTTOM, WIDGET_SIZE, TEXTURE_HEIGHT, WIDGET_SCREEN_Y);

        assertEquals(130, result.revealTopPixel());
        assertEquals(307, result.revealHeightPixels()); // 437 - 130
    }

    @Test
    void zeroFractionRevealsNothing() {
        HudGaugeClip.Result result = HudGaugeClip.compute(0f, CLIP_TOP, CLIP_BOTTOM, WIDGET_SIZE, TEXTURE_HEIGHT, WIDGET_SCREEN_Y);

        assertEquals(0, result.revealHeightPixels());
    }

    @Test
    void halfFractionRevealsTheBottomHalfOfTheClipRange() {
        HudGaugeClip.Result result = HudGaugeClip.compute(0.5f, CLIP_TOP, CLIP_BOTTOM, WIDGET_SIZE, TEXTURE_HEIGHT, WIDGET_SCREEN_Y);

        assertEquals(284, result.revealTopPixel()); // 437 - (307 * 0.5) = 283.5, rounds up
        assertEquals(153, result.revealHeightPixels()); // 437 - 284
    }

    @Test
    void slicesAtEveryFractionShareTheSameBottomScreenEdge() {
        float bottomAtFull = HudGaugeClip.compute(1f, CLIP_TOP, CLIP_BOTTOM, WIDGET_SIZE, TEXTURE_HEIGHT, WIDGET_SCREEN_Y).sliceScreenBottomY();
        float bottomAtHalf = HudGaugeClip.compute(0.5f, CLIP_TOP, CLIP_BOTTOM, WIDGET_SIZE, TEXTURE_HEIGHT, WIDGET_SCREEN_Y).sliceScreenBottomY();
        float bottomAtLow = HudGaugeClip.compute(0.1f, CLIP_TOP, CLIP_BOTTOM, WIDGET_SIZE, TEXTURE_HEIGHT, WIDGET_SCREEN_Y).sliceScreenBottomY();

        assertEquals(bottomAtFull, bottomAtHalf);
        assertEquals(bottomAtFull, bottomAtLow);
    }

    @Test
    void sliceHeightShrinksProportionallyWithFraction() {
        HudGaugeClip.Result full = HudGaugeClip.compute(1f, CLIP_TOP, CLIP_BOTTOM, WIDGET_SIZE, TEXTURE_HEIGHT, WIDGET_SCREEN_Y);
        HudGaugeClip.Result half = HudGaugeClip.compute(0.5f, CLIP_TOP, CLIP_BOTTOM, WIDGET_SIZE, TEXTURE_HEIGHT, WIDGET_SCREEN_Y);

        assertEquals(full.sliceScreenHeight() / 2f, half.sliceScreenHeight(), 0.5f);
    }

    @Test
    void fractionOutsideZeroToOneIsClamped() {
        HudGaugeClip.Result overFull = HudGaugeClip.compute(1.5f, CLIP_TOP, CLIP_BOTTOM, WIDGET_SIZE, TEXTURE_HEIGHT, WIDGET_SCREEN_Y);
        HudGaugeClip.Result full = HudGaugeClip.compute(1f, CLIP_TOP, CLIP_BOTTOM, WIDGET_SIZE, TEXTURE_HEIGHT, WIDGET_SCREEN_Y);
        assertEquals(full.revealHeightPixels(), overFull.revealHeightPixels());

        HudGaugeClip.Result belowZero = HudGaugeClip.compute(-0.5f, CLIP_TOP, CLIP_BOTTOM, WIDGET_SIZE, TEXTURE_HEIGHT, WIDGET_SCREEN_Y);
        assertEquals(0, belowZero.revealHeightPixels());
    }
}
