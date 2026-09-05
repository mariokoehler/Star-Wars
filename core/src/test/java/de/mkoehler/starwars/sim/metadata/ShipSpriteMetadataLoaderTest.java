package de.mkoehler.starwars.sim.metadata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link ShipSpriteMetadataLoader} can round-trip a
 * {@link ShipSpriteMetadata} (hitbox polygon and multiple named attachment
 * points, including more than one point under the same name) through both
 * of its read paths — a plain file (used by the sprite metadata editor) and
 * a classpath resource (used by the game at runtime) — and that a missing
 * classpath resource is reported as empty, not an error, since most ships
 * won't have metadata authored yet.
 */
class ShipSpriteMetadataLoaderTest {

    @Test
    void fileRoundTripPreservesHitboxAndAttachmentPoints(@TempDir Path tempDir) throws IOException {
        ShipSpriteMetadata original = new ShipSpriteMetadata();
        original.setHitboxPolygon(List.of(
            new PixelPoint(-32f, 40f),
            new PixelPoint(32f, 40f),
            new PixelPoint(0f, -48f)));
        original.getAttachmentPoints().put("PROJECTILE", List.of(
            new PixelPoint(-20f, 10f),
            new PixelPoint(20f, 10f)));
        original.getAttachmentPoints().put("ENGINE", List.of(new PixelPoint(0f, -50f)));

        File file = tempDir.resolve("xwing.meta.json").toFile();
        ShipSpriteMetadataLoader.saveToFile(original, file);
        ShipSpriteMetadata loaded = ShipSpriteMetadataLoader.loadFromFile(file);

        assertEquals(3, loaded.getHitboxPolygon().size());
        assertEquals(-32f, loaded.getHitboxPolygon().get(0).getX());
        assertEquals(40f, loaded.getHitboxPolygon().get(0).getY());

        assertEquals(2, loaded.getAttachmentPoints().get("PROJECTILE").size());
        assertEquals(-20f, loaded.getAttachmentPoints().get("PROJECTILE").get(0).getX());
        assertEquals(20f, loaded.getAttachmentPoints().get("PROJECTILE").get(1).getX());
        assertEquals(1, loaded.getAttachmentPoints().get("ENGINE").size());
    }

    @Test
    void missingClasspathResourceIsEmptyNotAnError() {
        Optional<ShipSpriteMetadata> result = ShipSpriteMetadataLoader.loadFromClasspath("shipdata/does-not-exist.meta.json");
        assertTrue(result.isEmpty());
    }
}
