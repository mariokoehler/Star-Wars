package de.mkoehler.starwars.render;

import com.badlogic.gdx.math.MathUtils;

/**
 * Pure arithmetic for {@code Client}'s engine-loop sound volume (design.md —
 * engine sound) — pulled out specifically so it's unit-testable without a GL
 * context, same "logic-heavy pure function, separate from rendering/GL
 * wiring" convention as {@link RadarScopeMath}/{@link HudGaugeClip}. Public
 * (unlike those two), since it's driven directly from {@code Client} itself
 * rather than a same-package rendering widget class — engine sound has no
 * separate HUD-widget-style wrapper, by design (design.md's "no separate
 * manager class" note).
 */
public final class EngineAudioMath {

    private EngineAudioMath() {
    }

    /**
     * Moves {@code currentFraction} toward {@code targetFraction} at a
     * constant rate that would cover the full {@code [0, 1]} range in
     * exactly {@code fadeDurationSeconds} — the thrust-button fade-in/
     * fade-out this project's engine loops use, driven every frame with
     * whatever {@code targetFraction} the thrust key currently implies
     * (1 held, 0 released).
     *
     * @param currentFraction   the volume fraction from the previous frame, in {@code [0, 1]}
     * @param targetFraction    the fraction to approach, in {@code [0, 1]}
     * @param deltaTimeSeconds  time since the last frame, in seconds
     * @param fadeDurationSeconds how long a full 0-to-1 (or 1-to-0) transition takes, in seconds
     * @return the new volume fraction, clamped so it never overshoots {@code targetFraction}
     */
    public static float approachFraction(float currentFraction, float targetFraction,
                                          float deltaTimeSeconds, float fadeDurationSeconds) {
        if (fadeDurationSeconds <= 0f) {
            return targetFraction;
        }
        float maxStep = deltaTimeSeconds / fadeDurationSeconds;
        if (currentFraction < targetFraction) {
            return Math.min(targetFraction, currentFraction + maxStep);
        } else if (currentFraction > targetFraction) {
            return Math.max(targetFraction, currentFraction - maxStep);
        }
        return currentFraction;
    }

    /**
     * A remote ship's engine volume falloff by distance: 1.0 at 0m, linearly
     * down to 0.0 at {@code maxAudibleRangeMeters} and beyond.
     *
     * @param distanceMeters        the listener's distance from the ship, in meters
     * @param maxAudibleRangeMeters the distance at which the engine becomes fully inaudible
     * @return the distance-based volume fraction, in {@code [0, 1]}
     */
    public static float distanceVolumeFraction(float distanceMeters, float maxAudibleRangeMeters) {
        if (maxAudibleRangeMeters <= 0f) {
            return 0f;
        }
        return MathUtils.clamp(1f - (distanceMeters / maxAudibleRangeMeters), 0f, 1f);
    }
}
