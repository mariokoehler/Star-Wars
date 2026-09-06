package de.mkoehler.starwars.sim.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import de.mkoehler.starwars.sim.metadata.TurretConfig;

import java.util.List;

/**
 * A ship's turret mounts — added by {@code ShipFactory} only for ship types
 * whose metadata has both {@code "TURRET"} attachment points and a
 * {@link TurretConfig} (currently the Falcon and Star Destroyer). All
 * mounts on one ship share the same {@link #getConfig()} tuning, en/disable
 * together via {@link #isEnabled()}/{@link #setEnabled(boolean)} (design.md
 * — one "T" keypress toggles every turret on the ship at once), but each
 * {@link TurretMount} tracks and aims at its own target independently — see
 * {@code TurretSystem} for the actual scan/track/fire behavior.
 */
public class TurretComponent implements Component {

    private final List<TurretMount> mounts;
    private final TurretConfig config;
    private boolean enabled;

    /**
     * Creates a turret component.
     *
     * @param mounts this ship's turret mounts, one per {@code "TURRET"} attachment point
     * @param config the tuning values shared by every mount
     */
    public TurretComponent(List<TurretMount> mounts, TurretConfig config) {
        this.mounts = mounts;
        this.config = config;
    }

    /**
     * Returns this ship's turret mounts.
     *
     * @return the mounts, one per {@code "TURRET"} attachment point
     */
    public List<TurretMount> getMounts() {
        return mounts;
    }

    /**
     * Returns the tuning values shared by every mount on this ship.
     *
     * @return the turret config
     */
    public TurretConfig getConfig() {
        return config;
    }

    /**
     * Returns whether this ship's turrets are currently switched on.
     *
     * @return {@code true} if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether this ship's turrets are switched on.
     *
     * @param enabled {@code true} to enable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Flips {@link #isEnabled()} — the "T" keybind's actual effect.
     */
    public void toggle() {
        enabled = !enabled;
    }

    /**
     * One turret's mutable runtime state: its fixed local mount point, and
     * everything {@code TurretSystem} updates every tick (current aim,
     * acquired target, and its own fire-rate cooldown).
     */
    public static final class TurretMount {

        private final Vector2 localOffsetMeters;
        private float aimAngleRadians;
        private Entity target;
        private float cooldownRemaining;

        /**
         * Creates a turret mount.
         *
         * @param localOffsetMeters this mount's position relative to the ship's
         *                          center, in the ship's own local (unrotated) frame, in meters —
         *                          converted once from the attachment point's pixel coordinates using
         *                          the ship type's own {@code pixelsPerMeter}
         */
        public TurretMount(Vector2 localOffsetMeters) {
            this.localOffsetMeters = localOffsetMeters;
        }

        /**
         * Returns this mount's position relative to the ship's center, in
         * the ship's own local (unrotated) frame.
         *
         * @return the local offset, in meters
         */
        public Vector2 getLocalOffsetMeters() {
            return localOffsetMeters;
        }

        /**
         * Returns this turret's current aim direction.
         *
         * @return the aim angle, in world-space radians (same convention as {@code Body.getAngle()})
         */
        public float getAimAngleRadians() {
            return aimAngleRadians;
        }

        /**
         * Sets this turret's current aim direction.
         *
         * @param aimAngleRadians the new aim angle, in world-space radians
         */
        public void setAimAngleRadians(float aimAngleRadians) {
            this.aimAngleRadians = aimAngleRadians;
        }

        /**
         * Returns this turret's currently acquired target, if any.
         *
         * @return the target entity, or {@code null} if none is currently acquired
         */
        public Entity getTarget() {
            return target;
        }

        /**
         * Sets this turret's currently acquired target.
         *
         * @param target the target entity, or {@code null} to clear it
         */
        public void setTarget(Entity target) {
            this.target = target;
        }

        /**
         * Returns how much longer until this turret can fire again.
         *
         * @return the remaining cooldown, in seconds; {@code <= 0} means ready
         */
        public float getCooldownRemaining() {
            return cooldownRemaining;
        }

        /**
         * Reduces the remaining cooldown by the given amount, not below zero.
         *
         * @param deltaTime time elapsed, in seconds
         */
        public void tickCooldown(float deltaTime) {
            cooldownRemaining = Math.max(0f, cooldownRemaining - deltaTime);
        }

        /**
         * Resets the cooldown to the turret's full duration, right after firing.
         *
         * @param cooldownSeconds the turret's configured cooldown duration
         */
        public void resetCooldown(float cooldownSeconds) {
            cooldownRemaining = cooldownSeconds;
        }
    }
}
