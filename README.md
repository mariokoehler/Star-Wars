# Star Wars

An online multiplayer top-down space deathmatch shooter for up to 8
players — Newtonian flight (thrust/rotate, no arcade auto-braking),
a dedicated authoritative server, and player accounts that persist
across sessions. Built on [libGDX](https://libgdx.com/).

**Full game design and technical architecture live in [`design.md`](./design.md).**
That document is the source of truth for how and why everything works;
this README only covers building, running, and finding your way
around the repo.

## What's in the game

- **Flight & combat** — Newtonian movement, a three-way power
  distribution system (Shields/Weapons/Engines) with diminishing
  returns on turn rate for lighter ships, blasters, autonomous
  turrets on capital ships, and lock-on homing missiles.
- **Seven playable ships** across two factions (Rebel: A-Wing →
  X-wing → Falcon; Imperial: TIE Fighter → TIE Interceptor → Star
  Destroyer; Snowspeeder as the free, faction-neutral starter), unlocked
  with XP earned from kills.
- **Radar/minimap** — layered detection (omnidirectional, forward
  cone, an active pulse), server-enforced so you only ever see what
  you'd actually detect.
- **A live arena** — hazard asteroids and hard boundary walls, not just
  open space.
- **Accounts, a scoreboard, and remappable keybinds.**

## Playing

If someone already has a server running, grab the latest client from
[Releases](https://github.com/mariokoehler/Star-Wars/releases) —
`StarWars-Client.zip` is self-contained (bundles its own Java runtime,
nothing to install) — unzip it and run `StarWars.exe`.

## Building from source

**Requirements:** JDK 25, Maven.

```
mvn clean package
```

produces the runnable client and server jars. Then, easiest way to run
either locally on Windows:

```
start_server.cmd    # one terminal
start_client.cmd     # one per player, in separate terminals
```

Both scripts reinstall `core` before running — necessary because this
project's version is computed from git tags (`jgitver`), so a stale
local install of `core` can silently fall out of date the moment a new
commit lands. Wait for the server to print
`[GameServer] Listening on TCP 45625 / UDP 45626` before starting a
client.

Not on Windows, or want the plain Maven commands:

```
mvn install -pl core -am -DskipTests
mvn -pl server compile exec:java      # one terminal
mvn -pl lwjgl3 compile exec:exec      # one per player
```

To run a packaged jar directly instead:

```
java --enable-native-access=ALL-UNNAMED -jar server/target/StarWars-Server-<version>.jar
java --enable-native-access=ALL-UNNAMED -jar lwjgl3/target/StarWars-<version>.jar
```

## Project layout

Maven multi-module; `<version>` throughout is computed from git tags,
never hand-edited (see design.md 3.10).

| Module | What it is |
|---|---|
| `core` | Shared code: simulation (Ashley/Box2D), networking, accounts, everything both client and server need |
| `lwjgl3` | The desktop client (LWJGL3) |
| `server` | The dedicated, headless server — the sole simulation authority |
| `dev-tools` | A small Swing app for authoring ship hitbox polygons/attachment points |

## Releasing

Pushing a `vX.Y.Z` tag triggers two independent GitHub Actions
workflows: `release-client.yml` builds a self-contained Windows
`StarWars-Client.zip` (via `jpackage`) and publishes it as a GitHub
Release asset; `release-server.yml` builds and pushes a Docker image to
`ghcr.io/mariokoehler/starwars-server`. Both embed the same
tag-derived version, which the client/server handshake checks
exactly — see design.md 3.10–3.12 for the full mechanism, and
`deploy/docker-compose.yml` for how the server actually gets deployed.

## Documentation

- **[`design.md`](./design.md)** — game design and system architecture:
  read this first for anything about how or why a feature works.
- **[`CLAUDE.md`](./CLAUDE.md)** — process notes, build gotchas, and
  session-by-session history for anyone (human or AI) picking up work
  on this codebase.

## Third-party assets

- **`assets/textures/backgrounds/blue_nebula.png`** (source copy at
  `assets-raw/backgrounds/blue-nebula/`) — "Blue_Nebula_08" from
  Screaming Brain Studios' *Seamless Space Backgrounds* pack, **CC0 1.0
  Universal / Public Domain**. No attribution required; full license
  text kept alongside it at `assets-raw/backgrounds/blue-nebula/License.txt`.
- **`assets/fonts/sf_distant_galaxy.ttf`** ("SF Distant Galaxy") —
  ShyFonts freeware: free to use and to redistribute as long as
  distribution stays free and over the internet (see design.md §3.9
  for the full terms and reasoning), which this project's release
  pipeline already satisfies.
