package de.mkoehler.starwars.net;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * This build's version, computed by jgitver from git tags/history at build
 * time (design.md 3.10) and baked into every jar via a resource-filtered
 * properties file — never a hand-maintained constant.
 * <p>
 * Both {@link NetworkClient} and {@link NetworkServer} read this to compare
 * versions during the handshake, so a client built from a different commit
 * than the server it's talking to is rejected up front rather than risking
 * a {@link MessageRegistry} mismatch further into the session.
 */
public final class AppVersion {

    private static final String RESOURCE_NAME = "starwars-version.properties";
    private static final String VERSION = loadVersion();

    private AppVersion() {
    }

    /**
     * Returns this build's version string, e.g. {@code "1.2.3"} on a tagged
     * release or {@code "1.2.4-SNAPSHOT"} on a commit made since the last tag
     * (design.md 3.10).
     *
     * @return the build version
     */
    public static String getVersion() {
        return VERSION;
    }

    private static String loadVersion() {
        try (InputStream stream = AppVersion.class.getClassLoader().getResourceAsStream(RESOURCE_NAME)) {
            if (stream == null) {
                throw new IllegalStateException(
                    "Missing " + RESOURCE_NAME + " on the classpath - was this built with Maven (not just compiled), "
                        + "so resource filtering ran?");
            }
            Properties properties = new Properties();
            properties.load(stream);
            String version = properties.getProperty("version");
            if (version == null || version.isBlank()) {
                throw new IllegalStateException("'version' property missing or blank in " + RESOURCE_NAME);
            }
            return version;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + RESOURCE_NAME, e);
        }
    }
}
