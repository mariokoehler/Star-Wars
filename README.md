# StarWars

A [libGDX](https://libgdx.com/) project, originally generated with [gdx-liftoff](https://github.com/libgdx/gdx-liftoff) and migrated from Gradle to Maven.

## Platforms

- `core`: Main module with the application logic shared by all platforms.
- `lwjgl3`: Primary desktop platform using LWJGL3; was called 'desktop' in older docs.

## Maven

This project uses [Maven](https://maven.apache.org/) to manage dependencies, with `core` and `lwjgl3` as modules of the root `pom.xml`. Useful commands, run from the repo root:

- `mvn clean package`: builds sources and archives of every module. The runnable, shaded jar for `lwjgl3` ends up at `lwjgl3/target/StarWars-1.0.0.jar`.
- `mvn -pl lwjgl3 -am compile exec:exec`: compiles and starts the application directly.
- `mvn clean`: removes the `target` folders that store compiled classes and built archives.

The `assets/` folder at the repo root is bundled onto the `lwjgl3` module's classpath at build time (see `lwjgl3/pom.xml`), matching how the previous Gradle setup merged it in.

### Note on native packaging

The original Gradle setup used the `construo` plugin (and, if enabled, GraalVM Native Image) to produce standalone native executables per OS. That tooling is Gradle-specific and wasn't ported — GraalVM native image support was also disabled by default (`enableGraalNative=false`) in the generated project. `mvn clean package` still produces a regular cross-platform runnable jar (`lwjgl3/target/StarWars-1.0.0.jar`), which needs a JVM to run. Ask if you'd like native-image/jpackage support added via Maven plugins.
