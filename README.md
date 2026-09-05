# StarWars

A [libGDX](https://libgdx.com/) project, originally generated with [gdx-liftoff](https://github.com/libgdx/gdx-liftoff) and migrated from Gradle to Maven.

## How to run

\# One-time build (from the repo root)

mvn clean package

\# Terminal 1 — the dedicated server

java --enable-native-access=ALL-UNNAMED -jar server/target/StarWars-Server-1.0.0.jar

Wait until it prints [GameServer] Listening on TCP 45625 / UDP 45626, then:

\# Terminal 2 — client 1

java --enable-native-access=ALL-UNNAMED -jar lwjgl3/target/StarWars-1.0.0.jar

\# Terminal 3 — client 2

java --enable-native-access=ALL-UNNAMED -jar lwjgl3/target/StarWars-1.0.0.jar

## Platforms

- `core`: Shared code — simulation (Ashley/Box2D), the network layer, and the application logic shared by all platforms.
- `lwjgl3`: Primary desktop platform using LWJGL3; was called 'desktop' in older docs.
- `server`: Dedicated server, built on `gdx-backend-headless`.

## Maven

This project uses [Maven](https://maven.apache.org/) to manage dependencies, with `core`, `lwjgl3` and `server` as modules of the root `pom.xml`. Useful commands, run from the repo root:

- `mvn clean package`: builds sources, runs tests, and packages every module. The runnable, shaded jars end up at `lwjgl3/target/StarWars-1.0.0.jar` (client) and `server/target/StarWars-Server-1.0.0.jar` (dedicated server).
- To run the client or server directly with `exec:exec`/`exec:java` instead of the packaged jar, `core` must already be installed locally first (once, or whenever `core` changes) — a bare exec goal invoked with `-am` runs across the *entire* reactor, including the parent `pom`, which has no config for that plugin and fails immediately:
  ```
  mvn install -pl core -am -DskipTests
  mvn -pl lwjgl3 compile exec:exec
  mvn -pl server compile exec:java
  ```
- `mvn clean`: removes the `target` folders that store compiled classes and built archives.

The `assets/` folder at the repo root is bundled onto the `lwjgl3` module's classpath at build time (see `lwjgl3/pom.xml`), matching how the previous Gradle setup merged it in.

## Third-party assets

- **`assets/textures/backgrounds/blue_nebula.png`** (and its source copy at
  `assets-raw/backgrounds/blue-nebula/`) — "Blue_Nebula_08" from
  Screaming Brain Studios' *Seamless Space Backgrounds* pack, released
  under **CC0 1.0 Universal / Public Domain**. No attribution required;
  full license text kept alongside it at
  `assets-raw/backgrounds/blue-nebula/License.txt`.

### Note on native packaging

The original Gradle setup used the `construo` plugin (and, if enabled, GraalVM Native Image) to produce standalone native executables per OS. That tooling is Gradle-specific and wasn't ported — GraalVM native image support was also disabled by default (`enableGraalNative=false`) in the generated project. `mvn clean package` still produces a regular cross-platform runnable jar (`lwjgl3/target/StarWars-1.0.0.jar`), which needs a JVM to run. Ask if you'd like native-image/jpackage support added via Maven plugins.
