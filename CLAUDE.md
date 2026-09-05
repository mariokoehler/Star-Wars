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

**Flight prototype (first milestone) — implemented 2026-09-05:** a
single-player Newtonian flight prototype now lives in `Client.java`
(`core`) — one player-controlled X-wing, WASD thrust/rotate (design.md
5.3 defaults, hardcoded, not yet driven by remappable keybinds), Box2D
body + Ashley ECS (`de.mkoehler.starwars.sim`: components
`PhysicsBodyComponent`/`SpriteComponent`/`PlayerControlledComponent`,
systems `PhysicsSystem`/`PlayerInputSystem`/`RenderSystem`, plus
`ShipFactory`), a plain camera-ease-follow, and a real texture atlas
pipeline (see "Texture atlas pipeline" below). No networking of ship
state yet; no other ships, weapons, or HUD.

**First user play-test (2026-09-05) — feedback applied:** turn torque
felt sluggish → bumped 15→22.5 N·m (+50%, design.md 5/§6). Speed was
hard to judge with no visible background → added the parallax starfield
(design.md 4.2, `de.mkoehler.starwars.render.ParallaxBackground`). This
is the expected loop for this milestone: implement → the user plays it →
tune/extend based on actual feel, not further design-on-paper.

**Second play-test round (2026-09-05) — feedback applied:** turn rate
and speed perception both fine now, no further tuning needed until
per-ship stats become a real topic (design.md §7 "ship roster"). Found
and fixed a real bug: vertical parallax scroll was inverted (see
design.md 4.2's "Bug found and fixed" note — a raw-OpenGL-V-vs-world-Y
sign issue, only visible by actually flying, not something a unit test
would have caught). Window/camera bumped to 1920×1080 (design.md 4.1).
Real nebula art now in as the backmost parallax layer (CC0, see
"Texture atlas pipeline" below and `README.md`'s "Third-party assets");
the star-dot layer on top of it is still `PlaceholderStarfield` —
**still awaiting real transparent star art** from the user for that one
layer specifically (the nebula layer no longer needs it, see design.md
4.2's updated transparency rule: only non-backmost layers need
transparency).

**Third play-test round (2026-09-05) — real bug fixed:** the user
reported ship jitter specifically at terminal velocity, background
staying smooth, and correctly intuited it was an interaction between two
update rates (though not quite the camera specifically). Root cause:
classic fixed-timestep-without-interpolation jitter — `PhysicsSystem`
steps Box2D on a fixed 1/60s accumulator, so some render frames get 0
fixed steps and others get 1+, and drawing the raw post-step body
position each frame doesn't advance smoothly relative to render time.
Invisible at low speed (tiny per-step delta), obvious at high speed
(large per-step delta) — exactly the reported symptom. Fixed by
interpolating: `PhysicsBodyComponent` now snapshots a pre-step
position/angle every frame, `PhysicsSystem` exposes an alpha (how far
the accumulator is toward the next step), and `RenderSystem`/the camera-
follow target both draw/track the interpolated state instead of the raw
one. See design.md 3.3's "Fixed-timestep rendering requires
interpolation" note for the full writeup — this is the same
"interpolate between two known states via a blend factor" technique
design.md 3.5 already calls for with networked snapshots, just applied
locally first.

**Implementation note:** fixing this required `Client.java` to stop
calling `engine.update(deltaTime)` as a single call, and instead call
`playerInputSystem`/`physicsSystem`/`renderSystem`'s `update()` directly
in sequence, with camera-follow and background drawing interleaved
between the physics and render steps — Ashley's automatic engine update
doesn't have a hook for "run arbitrary non-system code between two
systems." Flagged in `Client.java`'s class Javadoc as something to
revisit (e.g. camera-follow and background as their own Ashley systems)
once there's more than this one entity/pipeline to coordinate.

**Accepted as-is:** interpolation reduced the jitter significantly but
didn't eliminate it completely at top speed — the user tried it, judged
the remainder good enough to live with for now (half-joked it could
even pass as an intentional near-max-speed camera-shake effect), and
explicitly said not to keep chasing it further. Don't treat the small
residual as an open bug to keep fixing without being asked.

**Fourth play-test round (2026-09-05) — real bug fixed:** resizing the
window to a different aspect ratio stretched/distorted both the ship and
background. Cause: the `OrthographicCamera` was created with a fixed
1920×1080 viewport and nothing updated it on resize — `Client` didn't
override `resize(...)` at all, so the camera's projection kept assuming
1920×1080 while the actual framebuffer became a different shape,
distorting everything drawn through it. Fixed with libGDX's
`ScreenViewport` (wrapping the same camera, updated from a new
`resize(width, height)` override) rather than hand-rolling the math —
see design.md 4.1's "Resize behavior" note for why `ScreenViewport`
specifically (not `FitViewport`/`StretchViewport`) is the right fit given
our pixel-space world convention. `viewport.update(w, h, false)` — the
`false` matters, it stops the camera from recentering to the world
origin on every resize, which would otherwise fight the camera-follow
logic.

**Networked ship movement (second networking milestone) — implemented
2026-09-05.** The user's actual request was "network the flight model,
and I need to be able to test with two client instances on one
machine" — answer to that second part: works with no special handling,
the server already accepts multiple simultaneous connections and each
client uses an OS-assigned ephemeral port, so there's no collision.
Recommended local test flow: `mvn clean package` once, then
`java -jar lwjgl3/target/StarWars-1.0.0.jar` from two separate terminals
(not two concurrent `mvn exec:exec` processes).

**Architecture shift:** the server is now the sole simulator of ship
physics (design.md 3.5). `GameNetworkServer` (new, `server` module) owns
the authoritative Box2D `World`/Ashley `Engine`, spawns a ship per
connection (using KryoNet's own `Connection.getID()` as the player id —
no separate id counter needed), applies received input, steps physics,
and broadcasts a `WorldSnapshotMessage` every tick. `Client.java` was
rewritten to no longer use Box2D/Ashley at all — it sends
`PlayerInputMessage`s and renders every ship (its own included) purely
from received snapshots, easing toward each one's latest position/angle
the same way the camera already eased toward the ship. **No client-side
prediction yet** — deliberately deferred, so there's a visible input lag
right now; that's the next milestone. New message types:
`PlayerJoinedMessage`, `PlayerInputMessage`, `ShipState`,
`WorldSnapshotMessage`, `PlayerLeftMessage` (all in `core.net.messages`,
registered in `MessageRegistry`, each with a round-trip test in
`MessageRegistryTest`).

**Real bug caught by actually running two clients + a server, not by
tests passing:** the server crashed on startup
(`SharedLibraryLoadRuntimeException: gdx-box2d64.dll`) because it now
uses Box2D directly but only had `gdx-platform` natives, not
`gdx-box2d-platform` — same class of gotcha as the earlier
`gdx-platform`-natives miss, different library. Fixed by adding
`gdx-box2d-platform` classifier `natives-desktop` to `server/pom.xml`.
Verified the fix by actually running one server + two client processes
concurrently on this machine and watching both connect/exchange
traffic/disconnect cleanly with zero exceptions — not just `mvn package`
succeeding.

**Renamed/removed as part of this shift:** `PlayerInputSystem` →
`ShipControlSystem` (now reads a `NetworkInputComponent` set from
received input, not `Gdx.input` directly — also makes it headless-safe,
which is exactly why it moved server-side). `RenderSystem` and
`SpriteComponent` were deleted outright (client no longer uses Ashley
for rendering ships at all, so they had no remaining caller) rather than
left in place unused. New: `PlayerIdComponent`, `NetworkInputComponent`,
`ShipStats` (a first small step toward the data-driven "Ship roster"
TODO — one constant, `ShipStats.XWING`, shared by server body-building
and client sprite-sizing so the numbers can't drift apart).

**Cross-thread correctness pattern — important, applies to both ends:**
KryoNet's connection/message callbacks run on its own network thread,
never the render/tick thread. Both `GameNetworkServer` and `Client` now
follow the same rule: a network callback only ever enqueues a `Runnable`
onto a `ConcurrentLinkedQueue`; the actual state mutation (spawning a
ship, updating input, adding/removing a rendered ship) happens later,
drained at the very start of the next tick/render call, on the one
thread that actually owns that state (Box2D/Ashley server-side, the
`ships` map client-side). Follow this same pattern for anything else
added to either network endpoint later — don't mutate shared state
directly from inside an `onReceived`/`onConnected`/`onDisconnected`
override.

**Play-test confirmation (2026-09-05):** the user tried the real
two-client setup and confirmed the expected input lag is "pretty
noticeable" — as anticipated, since there's no client-side prediction
yet. **Explicitly confirmed as the next milestone.** Also confirmed the
placeholder color-tint for telling ships apart is good enough for now;
see design.md 3.5's "Visual distinction" note for the future direction
(a floating display-name label instead, once accounts/display names
exist) — not scheduled yet, don't start on it without being asked.

Read `design.md` in full before continuing further implementation — this
project moves in explicit milestones the user signs off on one at a time,
not open-ended feature sprints.

**Note:** `design.md` §4 was inserted between the old §3 (Architecture)
and §4 (UX flow), which renumbered old §4/§5/§6 to §5/§6/§7 — if a
cross-reference to design.md looks off by one section, that's why.

**Art assets:** the user has a full sprite library from an earlier,
sourceless version of this same game, at `R:\StarWars\sprites` (local
machine). See design.md §4.3 for the full inventory/conventions.
**Decided: don't bulk-import it.** Copy individual source files into
`assets-raw/` (repo-tracked) one ship/frame at a time, only as each is
actually used — the X-wing neutral frame is the only one copied in so
far. See "Texture atlas pipeline" below for how raw art becomes a
runtime atlas.

**Background art (2026-09-05):** the backmost parallax layer is real art
now — `assets-raw/backgrounds/blue-nebula/` /
`assets/textures/backgrounds/blue_nebula.png`, CC0-licensed (license
text alongside it, referenced in `README.md`'s "Third-party assets").
Tileable backgrounds don't need atlas-packing (a shared atlas page would
bleed at tile edges under `TextureWrap.Repeat`) — they're loaded as a
plain, dedicated `Texture` straight from `assets/`, same pattern any
future tileable background should follow.

**Still awaiting from the user:** a real, **transparent**, tileable
star-dot image for the layer drawn on top of the nebula (the nebula
itself doesn't need transparency, being the backmost layer — see
design.md 4.2's transparency rule). When it arrives: same drop-in
process, `assets-raw/backgrounds/<name>/` + a plain `Texture` swapped in
for `PlaceholderStarfield.generate(...)` in `Client.java` — one line,
`ParallaxBackground.Layer` itself doesn't change.

### Texture atlas pipeline (added 2026-09-05)

Raw source PNGs live in `assets-raw/<category>/<name>/...` (repo-tracked,
doubles as backup for art that only otherwise exists on `R:\`). Generated
atlases (`.atlas` + page `.png`) live in `assets/textures/`, which is
what the game actually loads at runtime — never load loose PNGs from
`assets-raw/` directly. See design.md 4.3 for the full rationale.

Packing uses libGDX's `TexturePacker` (`com.badlogicgames.gdx:gdx-tools`,
CLI/programmatic, no GUI needed), via `AtlasPacker`
(`lwjgl3/src/test/java/.../lwjgl3/tools/AtlasPacker.java`) — deliberately
under `src/test`, not `src/main`, and `gdx-tools` is a **test-scoped**
dependency, specifically so the packer tool and its dependency never end
up on the shaded runtime jar's classpath. It's a `main()` method, not a
`@Test`, so `mvn test` won't run it — it's invoked by hand only when
`assets-raw/` changes:

```
mvn -q -pl core -am install -DskipTests
mvn -pl lwjgl3 dependency:build-classpath -Dmdep.outputFile=target/test-cp.txt -DincludeScope=test
cd lwjgl3 && java -cp "target/classes;target/test-classes;$(cat target/test-cp.txt)" de.mkoehler.starwars.lwjgl3.tools.AtlasPacker
```

(See the surefire/exec-maven-plugin gotcha below for why this doesn't
just run via `exec:java`.) Add a new `pack(...)` call in `AtlasPacker.main()`
for each new raw asset folder.

## Build system

Maven, multi-module (migrated from the original gdx-liftoff Gradle setup on
2026-09-04). Modules: `core` (shared sim/net code), `lwjgl3` (desktop
client), `server` (`gdx-backend-headless` dedicated server, added
2026-09-05).

- `mvn clean package` from repo root builds everything; the runnable client
  jar ends up at `lwjgl3/target/StarWars-<version>.jar`, the runnable
  server jar at `server/target/StarWars-Server-<version>.jar`.
- To run the client or server directly (not the packaged jar), `core`
  must be installed locally first, **then** run the target module *alone*
  — no `-am` on the exec step itself (see the gotcha below for why):
  ```
  mvn install -pl core -am -DskipTests
  mvn -pl lwjgl3 compile exec:exec
  mvn -pl server compile exec:java
  ```
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
- **Each native-backed libGDX module needs its own `*-platform`
  `natives-desktop` dependency, independently** — `gdx-platform` (core
  natives, above) does not cover `gdx-box2d` too. When the server started
  using Box2D directly (`GameNetworkServer`), it crashed with the same
  class of error but for `gdx-box2d64.dll` specifically, requiring
  `gdx-box2d-platform` classifier `natives-desktop` added separately.
  General rule going forward: any time the `server` module starts using
  a new native-backed libGDX piece, check whether it needs its own
  natives dependency too — don't assume `gdx-platform` alone covers
  everything, and verify by actually running the packaged jar, not just
  building it.
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
- **A plugin's top-level `<configuration>` (outside `<executions>`)
  applies to *every* goal of that plugin invoked directly from the CLI**,
  not just the one it was written for. `lwjgl3/pom.xml`'s
  exec-maven-plugin config (the `<classpath/>` special-element trick) is
  written for `exec:exec`; invoking `exec:java` on the same module picks
  up that same config and fails (`Cannot store value into array...`)
  because `exec:java`'s `arguments` parameter doesn't understand the
  `<classpath/>` marker. Don't try to reuse that block for `exec:java` —
  the atlas packer above resolves its classpath a different way instead.
- **Invoking a bare plugin goal (e.g. `exec:java`, `exec:exec`) with
  `-pl <module> -am` runs it across the whole reactor, not just
  `<module>`** — it ran (and failed) against the parent `pom`-packaging
  project first here, before ever reaching the intended module, both for
  the client (`exec:exec`) and the server (`exec:java`) run commands (the
  README/this file both had this bug — a user actually hit it running the
  client). Fix: `mvn install -pl core -am -DskipTests` once to put `core`
  in the local repo, then invoke the exec goal with plain `-pl <module>`
  (no `-am`) so the reactor is just that one project.
  **Tried and does NOT work:** binding the exec-plugin config to a named
  `<execution id="...">` and invoking `exec:goal@id` — that CLI syntax
  does not restrict which reactor projects the goal runs against, it
  still walks the whole reactor and just fails the same way on any
  project that doesn't declare that execution id. Dropping `-am` on the
  exec step itself is the actual fix, not `@id`.

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
  **Applied 2026-09-05:** the flight prototype (`de.mkoehler.starwars.sim`)
  intentionally has no unit tests — `PhysicsSystem`/`PlayerInputSystem`/
  `RenderSystem` are thin Box2D/Ashley/`Gdx.input` wiring, not standalone
  logic, matching this rule rather than a coverage gap. Revisit if real
  testable logic (e.g. a data-driven ship-stats lookup) lands in that
  package later.
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
- **The user has Photoshop and is willing to manually edit image assets**
  (resize, reformat, re-tile, etc.) on request — if a texture/sprite
  needs some transformation that's easier done by hand than scripted
  (e.g. resizing an asset, making a background seamlessly tileable,
  adding an alpha channel), just ask rather than trying to script an
  equivalent transformation.
