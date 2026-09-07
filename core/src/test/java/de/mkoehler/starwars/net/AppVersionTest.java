package de.mkoehler.starwars.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies {@link AppVersion} reads a real, non-blank version out of the
 * resource-filtered {@code starwars-version.properties} (design.md 3.10) -
 * not the exact value, since that's whatever jgitver computes for the
 * current git state and changes with every commit.
 */
class AppVersionTest {

    @Test
    void versionIsANonBlankStringReadFromTheFilteredResource() {
        String version = AppVersion.getVersion();
        assertNotNull(version);
        assertFalse(version.isBlank());
    }

    @Test
    void versionIsStableAcrossRepeatedCalls() {
        assertEquals(AppVersion.getVersion(), AppVersion.getVersion());
    }
}
