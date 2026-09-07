# Builds the StarWars dedicated server image (design.md 3.12), deployed via
# QNAP Container Station as a single-service Docker Compose "Application"
# (see deploy/docker-compose.yml).
#
# Build context is the REPO ROOT, not server/ - the Maven reactor build
# needs the root pom.xml and core/ too, and jgitver (design.md 3.10) needs
# the real .git history to compute the image's version:
#
#   docker build -f server/Dockerfile -t starwars-server .

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
# The whole repo, not just core/server/assets: Maven has to parse every
# module the root pom.xml declares (lwjgl3, dev-tools included) to build
# its reactor graph before -pl/-am can even apply - a build context missing
# either of those directories fails at that parse step, not at the build
# step -pl would otherwise restrict to. .dockerignore keeps target/ etc out.
COPY . .
# -am so core is built fresh in this reactor, matching how the client
# release build works (README/CLAUDE.md) - no local .m2 install to rely on.
# Tests run as part of this (no -DskipTests): a broken build should fail
# here, not ship as an image.
RUN mvn -pl server -am package

# Not eclipse-temurin's -alpine variant: libGDX's native libraries (its own
# core native plus Box2D, both pulled in via the natives-desktop classifier)
# are built against glibc, not Alpine's musl.
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=build /build/server/target/StarWars-Server-*.jar /app/server.jar

# TCP for the reliable channel, UDP for the unreliable one (design.md 3.4) -
# NetworkConstants.TCP_PORT / UDP_PORT. Documentation only here; actual
# publishing happens in deploy/docker-compose.yml.
EXPOSE 45625/tcp 45626/udp

# GameNetworkServer resolves the account store as Gdx.files.local("data/
# accounts.json") - relative to the working directory above, so that lands
# at /app/data/accounts.json. deploy/docker-compose.yml bind-mounts a real
# NAS folder onto /app/data so it survives container recreation/updates.

# --enable-native-access=ALL-UNNAMED: same flag the README documents for
# running this jar directly - unlike lwjgl3's, server/pom.xml's shade
# config doesn't bake this into the manifest.
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "/app/server.jar"]
