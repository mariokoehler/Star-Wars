# CLAUDE.md — Star Wars project notes

Cross-session memory for this repo. Update this file as we learn things —
gotchas, decisions and their reasons, conventions — so future sessions don't
have to rediscover them. Game design/architecture content belongs in
`design.md`, not here; this file is about *how we work on this codebase*.

## What this project is

Online multiplayer top-down deathmatch shooter, up to 8 players, Newtonian
flight model, dedicated authoritative server. Full design/architecture is in
[`design.md`](./design.md) — read that first for anything about game design
or system architecture. Keep both documents in sync: this file for
process/gotchas, `design.md` for the game/architecture itself.

- GitHub: https://github.com/mariokoehler/Star-Wars (private)

### Status / where we left off (2026-09-05)

Design is comprehensive (see below), and **implementation has now started**:
the first real code is a working network layer (see "Networking layer" below).
No gameplay code yet beyond the Liftoff template's placeholder `Client.java`
— no entities/components, no Box2D flight, no accounts, no UI screens.
`design.md` covers concept, core mechanics (power distribution incl. the
capacitor sub-mechanic and the exact clamping algorithm, combat-lock ESC
rule), architecture (modules, sim stack, networking, netcode approach,
accounts, client local config, Box2D units note, JSON library), rendering/
presentation (§4: camera zoom+inertia, parallax starfield, ship sprite
conventions, UI framework choice), UX flow (§5), a component TODO checklist
(§6), and a remaining open-questions list (§7: client distribution
mechanism, ship roster, map/arena design, tick/snapshot rate, lag
compensation).

**Networking layer (first milestone) — implemented 2026-09-05:** a new
`server` Maven module now exists alongside `core`/`lwjgl3`. `core` gained a
`de.mkoehler.starwars.net` package (message classes, `MessageRegistry`,
`NetworkServer`, `NetworkClient` — all libGDX-free and directly unit-
tested) wrapping the KryoNet fork. Proven end-to-end over real loopback
sockets: connect/disconnect, a handshake round trip, and a ping/pong round
trip over both TCP and UDP (`NetworkServerClientIntegrationTest`). The
dedicated server (`ServerLauncher` → `GameServer`) runs as a headless
libGDX app on a placeholder 30Hz loop and was manually run as a packaged
jar to confirm it boots and stays up, not just unit-tested. See design.md
3.2/3.4/3.5 and §6 for exactly what's done vs. still open (real gameplay
messages, tick/snapshot rate, client-side prediction, accounts).

Read `design.md` in full before continuing further implementation — this
project moves in explicit milestones the user signs off on one at a time,
not open-ended feature sprints.

**Note:** `design.md` §4 was inserted between the old §3 (Architecture)
and §4 (UX flow), which renumbered old §4/§5/§6 to §5/§6/§7 — if a
cross-reference to design.md looks off by one section, that's why.

**Art assets:** the user has usable sprite sets from an earlier,
sourceless version of this same game, currently at `R:\StarWars\sprites`
(local machine, **not** in the repo). See design.md §4.3 for the full
inventory/conventions. Needs copying into `assets/` once rendering work
starts — don't forget this isn't already in the project.

## Build system

Maven, multi-module (migrated from the original gdx-liftoff Gradle setup on
2026-09-04). Modules: `core` (shared sim/net code), `lwjgl3` (desktop
client), `server` (`gdx-backend-headless` dedicated server, added
2026-09-05).

- `mvn clean package` from repo root builds everything; the runnable client
  jar ends up at `lwjgl3/target/StarWars-<version>.jar`, the runnable
  server jar at `server/target/StarWars-Server-<version>.jar`.
- `mvn -pl lwjgl3 -am compile exec:exec` runs the client directly.
- `mvn -pl server -am compile exec:java` runs the dedicated server
  directly (no `<classpath/>`/exec:exec workaround needed here — headless
  has no native windowing to fight with, plain `exec:java` is enough).
- `mvn test` runs the JUnit 5 suite (currently in `core`, covering the
  network layer).
- Java 25, targeted via `maven.compiler.release` in the parent `pom.xml`.

### Maven + libGDX gotchas learned during the migration

- Maven 3.8.4's default lifecycle binding resolves an **ancient**
  `maven-compiler-plugin` (3.1) that ignores `maven.compiler.release`
  entirely and defaults to source/target 5. Fix: pin a modern version
  (3.13.0+) in the parent POM's `<build><pluginManagement>` — it's picked up
  automatically by the default lifecycle binding, no need to redeclare the
  plugin in child modules.
- libGDX's platform-natives artifacts (`gdx-platform`,
  `gdx-box2d-platform`) publish a literal Maven **classifier**
  `natives-desktop` that bundles all desktop OS natives in one jar — this is
  a real classifier string, not a Gradle-variant-only thing. Just declare it
  directly as `<classifier>natives-desktop</classifier>`; no need for
  `os-maven-plugin` or per-OS classifier logic.
- `gdx-backend-lwjgl3`'s own POM already lists **every** LWJGL OS-native
  classifier (`natives-windows`, `natives-linux`, `natives-macos`, arm
  variants, etc.) as plain (non-optional) compile dependencies. Depending on
  it in Maven pulls all of them automatically — this is expected/correct,
  not a misconfiguration, and matches what the old Gradle build shipped too.
- To force the LWJGL version up (avoiding Java 25 warnings, matching what the
  Gradle build did with a `constraints{}` block), you must add a
  `dependencyManagement` entry **per classifier** — Maven's dependency
  management match key includes the classifier, so one entry with no
  classifier does *not* override the classified variants too.
- `${project.parent.basedir}` is **not** a valid Maven property (silently
  fails to interpolate, produces a bogus literal path). Use
  `${project.basedir}/../assets` to reference the sibling `assets/` folder
  as an extra resource directory from the `lwjgl3` module.
- The dedicated server should be built on `gdx-backend-headless` — it runs
  the full libGDX app lifecycle (`Gdx.app`, `Gdx.files`, `Gdx.net`) without a
  window, so `core` can be shared unmodified between client and server. Its
  `Gdx.net` supports HTTP + TCP; it does **not** give us UDP or a
  multiplayer protocol — that's what KryoNet is for (see `design.md` §3.4).
- **`gdx-backend-headless` still needs libGDX's native library.** Running
  the packaged server jar without it throws
  `SharedLibraryLoadRuntimeException: Couldn't load ... gdx64.dll` at
  startup — some core libGDX utilities are backed by native code
  regardless of backend. Fix: add `gdx-platform` classifier
  `natives-desktop` to the `server` module too, same dependency `lwjgl3`
  already has. Only caught by actually running the built jar, not by
  `mvn package` succeeding or unit tests passing — worth remembering to
  smoke-test any new runnable module that way, not just compile/test it.
- **A library only published on JitPack** (like the KryoNet fork) needs
  `<repositories><repository><url>https://jitpack.io</url>...` added to
  the parent POM — it won't resolve from Maven Central alone, and the
  error if you forget is a generic "could not resolve dependency", not
  something that points at JitPack specifically.
- **Kryo network compatibility depends on registration order, not class
  names.** Every class sent over KryoNet must be registered with
  `Kryo.register(...)` in the *exact same order* on both the client and
  server, since Kryo identifies types on the wire by a numeric id derived
  from that order. Keeping this in one shared method
  (`MessageRegistry.register(Kryo)` in `core`, called by both
  `NetworkServer` and `NetworkClient`) rather than duplicating
  registration calls on each end is what actually guarantees this, and is
  covered by a test (`MessageRegistryTest`) that registers into two
  independent `Kryo` instances and asserts identical ids.

## Git / GitHub

- Commits are GPG-signed. If `git commit` fails with a `gpg-agent` connect
  error, start Kleopatra (`C:\Program Files (x86)\Gpg4win\bin\kleopatra.exe`)
  to bring the agent up, then retry the same commit — don't fall back to
  `--no-gpg-sign`. (This is also in the user's global CLAUDE.md, repeated
  here since it came up during this project's initial push.)
- `_gradle-legacy-backup/` at repo root holds the pre-migration Gradle build
  files (gitignored, not pushed). Safe to delete once the Maven setup has
  been trusted for a while — ask before deleting it, it's the user's escape
  hatch back to the old build.

## Code style & testing

- **Javadoc — comprehensive, on every class and every method**, HTML-
  formatted (standard Javadoc tags/markup, not plain comments). Keep it
  strictly about **what the code does/is for** — parameters, return
  values, invariants, preconditions. **Never** put session/debugging
  narrative in it (e.g. "fixed in session X because of bug Y", "changed
  this after discovering Z") — that belongs in the commit message, not
  in code documentation that outlives the context of why it changed.
- **Unit tests — JUnit 5**, used selectively rather than chasing coverage
  numbers. Not every class needs a test (game-loop/rendering code
  especially doesn't lend itself to it), but **core, logic-heavy
  components should have them** — the user has a backend/server
  engineering background and values this even though it's less common in
  game dev. Clear candidates as components get built: the networking
  layer (message (de)serialization, protocol handling), the power
  distribution clamping algorithm (2.2 in design.md — a pure function
  with exact expected behavior, e.g. the 10%-floor/redirect/no-op cases),
  the combat-lock timer logic, account creation/auth validation. Skip
  tests for thin glue code or anything that's really just wiring
  libGDX/Scene2D together.
- **JSON handling — latest Jackson** (`com.fasterxml.jackson.core`/
  `jackson-databind`), for every local/server JSON file described in
  design.md (accounts store, client connection config, client keybinds
  config) — decided so we don't hand-roll or mix JSON libraries across
  the codebase. See design.md 3.9 for where this is used.

## Conventions / preferences

- **Design decisions get written into `design.md` immediately**, same
  session as the decision — not summarized after the fact, not left in
  chat history. This has been the working pattern for several design
  sessions now and should continue.
- **When the user leaves a sub-detail unspecified, propose a concrete
  default directly in the doc and clearly flag it as unconfirmed** (in the
  relevant section, and/or in the Open Questions list) rather than leaving
  a blank or stalling to ask. Validated repeatedly this project — e.g. the
  I/J/K/L power-distribution keybind mapping was proposed unprompted and
  the user explicitly approved it outright. Reserve actually asking
  (AskUserQuestion) for choices that are foundational/hard-to-reverse
  (e.g. the networking library), not every minor default.
- **Proactively flag security-relevant concerns during design even when
  not asked** (e.g. "don't store the raw password server-side, hash it") —
  the user has accepted this kind of unprompted flag well so far. Keep
  doing it, but keep it brief and non-blocking rather than turning it into
  a lecture.
- This project is worked in incremental sessions (evenings); the user
  explicitly relies on `design.md` + this file to pick the thread back up
  next time — always leave both fully in sync before a session ends,
  don't just describe changes in chat.
