package de.mkoehler.starwars.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies {@link EngineAudioMath}'s fade-approach and distance-falloff
 * arithmetic (design.md — engine sound).
 */
class EngineAudioMathTest {

    private static final float TOLERANCE = 1e-4f;

    @Test
    void reachesFullVolumeAfterExactlyTheFadeDuration() {
        float fraction = EngineAudioMath.approachFraction(0f, 1f, 0.25f, 0.25f);
        assertEquals(1f, fraction, TOLERANCE);
    }

    @Test
    void reachesSilenceAfterExactlyTheFadeDuration() {
        float fraction = EngineAudioMath.approachFraction(1f, 0f, 0.25f, 0.25f);
        assertEquals(0f, fraction, TOLERANCE);
    }

    @Test
    void movesLinearlyPartwayThroughTheFade() {
        float fraction = EngineAudioMath.approachFraction(0f, 1f, 0.1f, 0.25f);
        assertEquals(0.4f, fraction, TOLERANCE);
    }

    @Test
    void neverOvershootsTheTargetOnALargeDeltaTime() {
        float fraction = EngineAudioMath.approachFraction(0f, 1f, 5f, 0.25f);
        assertEquals(1f, fraction, TOLERANCE);
    }

    @Test
    void staysPutOnceAlreadyAtTheTarget() {
        float fraction = EngineAudioMath.approachFraction(1f, 1f, 0.1f, 0.25f);
        assertEquals(1f, fraction, TOLERANCE);
    }

    @Test
    void isFullVolumeAtZeroDistance() {
        assertEquals(1f, EngineAudioMath.distanceVolumeFraction(0f, 40f), TOLERANCE);
    }

    @Test
    void isSilentAtTheMaxAudibleRange() {
        assertEquals(0f, EngineAudioMath.distanceVolumeFraction(40f, 40f), TOLERANCE);
    }

    @Test
    void isSilentBeyondTheMaxAudibleRange() {
        assertEquals(0f, EngineAudioMath.distanceVolumeFraction(100f, 40f), TOLERANCE);
    }

    @Test
    void fallsOffLinearlyWithDistance() {
        assertEquals(0.5f, EngineAudioMath.distanceVolumeFraction(20f, 40f), TOLERANCE);
    }
}
