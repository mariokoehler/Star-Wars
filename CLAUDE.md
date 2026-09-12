# CLAUDE.md — Star Wars project notes

Cross-session memory for this repo: gotchas, decisions and their reasons,
conventions — so future sessions don't have to rediscover them. Game
design/architecture (what the game *is*, how each system currently works)
belongs in `design.md`, not here; this file is about *how we work on this
codebase* — build/tooling gotchas, testing conventions, working style. Both
files get updated the same session a decision/gotcha is found, not after.

## What this project is

Online multiplayer top-down deathmatch shooter, up to 8 players, Newtonian
flight model, dedicated authoritative server. Full design/architecture is in
[`design.md`](./design.md) — read that first for game design or system
architecture. GitHub: https://github.com/mariokoehler/Star-Wars (public
since 2026-09-09).

## Current status

A playable, networked multiplayer game under active iterative development
(evening sessions — the user plays each build and reports back real bugs/
feel issues the same or next session). See `design.md` for the full current
feature set and exact mechanics; short version: full client/server
networking with client-side prediction/reconciliation, 7 flyable ships with
authored hitboxes, weapons + capacitor, shields/hull, power distribution,
turrets (Falcon/Star Destroyer), radar (base/cone/pulse, real server-side
fog-of-war), homing missiles, accounts with persisted XP/kills/deaths, ship
unlocks gated by XP + a branch-based Ship Tree, a scoreboard, a Death
Screen, an embedded dev-only MCP server for remote-controlling the Connect
screen, a full particle-effects pass (engine/lights/damage smoke/muzzle
flash/explosions), a remappable Keybind screen, an Audio Settings screen, a dynamic
"battlefield" camera (leans toward the centroid of the player plus every
visible enemy) with a player-chosen, persisted zoom level on top of the
existing speed-linked zoom, a
bounded 500×500m arena with wall-impact damage and real spawn points,
asteroids, power-ups, mines (spawned only via a BOMB power-up pickup),
impact/pickup sounds, and a hangar-ambience music loop. Tagged releases use
jgitver-computed versions from git tags (currently `v0.0.8`).

**Known standing issue, unresolved:** an intermittent, large (0–25s),
environment-level delay in raw Windows `SocketChannel`/`DatagramChannel`
connect/close calls — confirmed via a from-scratch JUnit test with zero game
code involved, on more than one machine, VPN ruled out. Worked around (not
fixed) by never blocking the render thread on it (`NetworkClient.stopAsync()`
on every screen-transition teardown) — root cause (security software socket
interception is the leading theory) never confirmed. If a stall/jitter
report ever traces back to "physics accumulator way behind" or
`PhysicsSystem.getAlpha()` exceeding 1, this is a plausible shared cause,
not confirmed as one.

## Build system

Maven, multi-module. Modules: `core` (shared sim/net code), `lwjgl3`
(desktop client), `server` (`gdx-backend-headless` dedicated server),
`dev-tools` (offline Swing authoring tools, not shipped — sprite metadata
editor).

- `mvn clean package` from repo root builds everything; client jar at
  `lwjgl3/target/StarWars-<version>.jar`, server jar at
  `server/target/StarWars-Server-<version>.jar`.
- To run a module directly (not the packaged jar): install `core` first,
  **then** run the target module *alone* — no `-am` on the exec step itself
  (a bare `exec:java`/`exec:exec` with `-am` walks the whole reactor,
  including the parent `pom`-packaging project, and fails there first):
  ```
  mvn install -pl core -am -DskipTests
  mvn -pl lwjgl3 compile exec:exec
  mvn -pl server compile exec:java
  mvn -pl dev-tools compile exec:java
  ```
- `mvn test` runs the JUnit 5 suite. Java 25 (`maven.compiler.release`).
- **jgitver computes `${project.version}` from git tags** — every pom.xml
  carries a placeholder `<version>0</version>`. Re-run
  `mvn install -pl core -am -DskipTests` after *every* new commit, not just
  once per session — a commit changes the version the next build expects,
  and the old local `core` install silently stops matching.

### Maven + libGDX gotchas

- Maven 3.8.4's default `maven-compiler-plugin` (3.1) ignores
  `maven.compiler.release` and defaults to source/target 5 — pin 3.13.0+ in
  the parent POM's `<pluginManagement>`.
- libGDX native artifacts (`gdx-platform`, `gdx-box2d-platform`,
  `gdx-freetype-platform`) use a real Maven classifier, `natives-desktop` —
  declare it directly, no `os-maven-plugin` needed.
- **Each native-backed libGDX piece needs its *own* `*-platform`
  `natives-desktop` dependency, independently** — `gdx-platform` doesn't
  cover Box2D, Box2D doesn't cover FreeType, etc. Missing one throws
  `SharedLibraryLoadRuntimeException` at actual runtime, not at build/test
  time — only caught by actually running the packaged jar. Check this any
  time a module starts using a new native-backed libGDX piece (e.g. the
  headless `server` needed `gdx-platform` for basics *and*
  `gdx-box2d-platform` once it started using Box2D directly).
- Force a `dependencyManagement` LWJGL version bump per-classifier — Maven's
  match key includes the classifier, one unclassified entry doesn't
  override the classified variants too.
- `${project.parent.basedir}` isn't a valid Maven property — use
  `${project.basedir}/../assets` for the sibling `assets/` resource dir.
- A JitPack-only dependency (e.g. the KryoNet fork) needs the JitPack
  repository added to the parent POM explicitly.
- **Kryo wire compatibility depends on registration order, not class
  names** — every class sent over KryoNet must be `Kryo.register()`ed in
  the exact same order on both ends. Kept in one shared
  `MessageRegistry.register(Kryo)`, tested by registering into two
  independent `Kryo` instances and asserting identical ids.
- A plugin's top-level `<configuration>` (outside `<executions>`) applies to
  *every* goal of that plugin invoked from the CLI — `lwjgl3`'s
  `exec-maven-plugin` config is written for `exec:exec`; `exec:java` on the
  same module picks it up and fails. Don't reuse one goal's config block for
  another.
- **Invoking a bare plugin goal with `-pl <module> -am` runs it across the
  whole reactor**, not just `<module>` — install `core` first, then invoke
  the exec goal with plain `-pl <module>` (no `-am`). Binding the plugin
  config to a named `<execution id>` and invoking `exec:goal@id` does
  **not** fix this — that CLI syntax still walks the whole reactor.
- Maven silently drops one side of a **duplicate same-`groupId:artifactId`
  `<plugin>` declaration** within one `<plugins>` list (a build warning,
  not an error) — merge same-GA plugins into one block with multiple
  `<execution>`s instead of declaring the plugin twice.
- A multi-step packaging pipeline with real ordering dependencies (e.g.
  `jpackage`, which refuses to run if its destination folder already
  exists) needs each step bound to its own distinct standard-lifecycle
  phase — don't rely on same-phase plugin-declaration order.
- Commit signing: if `git commit` fails with a `gpg-agent` connect error,
  start Kleopatra (`C:\Program Files (x86)\Gpg4win\bin\kleopatra.exe`) and
  retry the same commit — never fall back to `--no-gpg-sign`.

## Networking / Box2D patterns

- **Cross-thread rule, both ends:** KryoNet invokes connection/message
  callbacks on its own network thread, never the render/tick thread. A
  network callback only ever enqueues a `Runnable` onto a
  `ConcurrentLinkedQueue`; the actual state mutation happens later, drained
  at the start of the next tick/render call, on the thread that owns that
  state. Follow this for anything new added to either endpoint.
- **Box2D clears a body's applied forces/torques after every single
  `world.step()` call.** The server ticks slower than it physics-steps
  (30Hz tick / 60Hz step), so a continuous force/torque applied once per
  *tick* only survives the first of that tick's steps. `PhysicsSystem` has
  an `update(deltaTime, Runnable beforeEachStep)` overload that reapplies
  input immediately before every individual step — any code applying a
  continuous (not one-shot/impulse) force must go through this overload.
- **Installing any custom Box2D `ContactFilter` on a `World` completely
  replaces its native default category/mask/group filtering, not layers on
  top of it** (confirmed by reading `World.java`'s JNI binding). Once one
  is installed, every `CollisionCategories` bit means nothing unless the
  filter itself replicates the default check — `CollisionCategories
  .shouldCollide(Filter, Filter)` is that replication, unit-tested, and the
  server's own `ContactFilter` lambda must call it first. This bit the
  project for months (every category exclusion silently did nothing) before
  asteroids — the first body large/slow/long-lived enough — made it
  undeniable.
- **Don't spawn one Box2D body exactly overlapping another's collider.**
  Two exactly-coincident shapes have an undefined separation direction, and
  Box2D's overlap resolution pushes one along some unrelated fixed axis
  instead. Prevent the collision itself via a `ContactFilter` exclusion
  (don't just skip *damage* for the pair), and prefer spawning just outside
  a collider rather than inside/on top of it.
- **Box2D forbids creating/destroying bodies from inside a contact
  callback.** The standing pattern for any contact-triggered logic: collect
  into a pending list during `beginContact`, resolve (apply damage, destroy
  bodies, broadcast VFX/SFX messages) right after that tick's
  `physicsSystem.update(...)` returns. Read any position/velocity needed for
  the resolution *inside* `beginContact` itself (before that step's
  velocity solver runs) if it needs to reflect the approach, not the
  post-bounce, state. When a resolution broadcasts a message and destroys a
  body, broadcast *before* destroying — a later side effect (e.g. a kill
  destroying a second body) must never run between reading a transform and
  destroying it.
- **Anything spawned mid-tick shouldn't be moved within that same tick
  before its first broadcast/render.** Order tick logic so spawning (fire a
  weapon, create a missile/mine/asteroid/power-up) happens *after* that
  tick's physics stepping, not before — otherwise a fresh body gets swept
  forward by however many physics steps that tick ran before its first
  observed position, which looks exactly like a spawn-offset bug.
- **A `BodyType.StaticBody` is the strongest way to guarantee "never
  moves, never rotates"** — stronger than `BodyDef.fixedRotation = true`
  on a dynamic body (which only locks rotation), with zero bookkeeping.
  Box2D still generates contacts between a static body and every dynamic
  body that touches it.
- **Dead reckoning, not easing, for anything fast/hit-relevant.** An
  exponential ease toward the latest snapshot lags a moving target by an
  amount proportional to its speed (a 50m/s object can trail its true
  position by several meters) — looks exactly like a wrong hitbox size, not
  a rendering-lag bug. Extrapolate `lastKnownPosition + velocity ×
  timeSinceSnapshot` instead. Easing is fine for slow/cosmetic-only follows
  (camera-follow).
- **A reconciliation/staleness "seed elapsed time instead of resetting to
  zero" trick only works for two *independently-integrating* quantities
  measured over the same real-world duration** (e.g. a locally-predicted
  body vs. the server's own state extrapolated forward) — it does *not*
  apply to a purely-recomputed-from-scratch render position (e.g. a remote
  projectile's `base + velocity × elapsed`), where the correct elapsed
  really is the data's true age. Applying the same trick there overshoots.
- **A missing-fresh-position case can be Box2D-adjacent, not literal
  physics:** a Kinematic "local mirror" body is the right tool when the
  client's own predicted body needs to physically interact with something
  server-authoritative and moving (e.g. asteroids) — not a full dynamic
  mirror, since the client always learns the true state from the next
  snapshot and never needs to simulate *how* it moves.

## Verification / debugging gotchas

- **Rebuild and restart both client and server together after any wire-
  shape change** (a Kryo-registered class's field/constructor shape, or a
  new registered type) — a stale pair fails at Kryo deserialization the
  instant a differing message crosses the wire; easy to misdiagnose as a
  logic bug if the two processes' build provenance isn't tracked.
- **Check full command lines, not just process names, before assuming
  nothing is running.** `Get-CimInstance Win32_Process -Filter
  "Name='java.exe'" | select ProcessId,CommandLine` — a stray `--mcp`
  client or an old server/client jar left running is a recurring cause of
  `mvn clean` failing to delete a locked jar, and of "no server running"
  false negatives. Record which PID is which *before* killing any of them.
- **If a real code change doesn't show up in runtime behavior after a
  supposedly-successful rebuild, suspect a stale artifact before the fix**
  — verify by decompiling the actual class inside the built jar (`javap -c`
  on an extracted `.class`), not by re-reading the source again. A plain
  `mvn install`/`package` (not `clean install`/`clean package`) has
  produced a stale jar before.
- **A verification composite/mockup is only as strong as whether it calls
  the real code path, not just reproduces the intended math.** A Python
  script simulating the *goal* of a layout calculation can't catch a bug in
  the actual method's argument order — it never exercises that call at all.
  When possible, verify by driving the real method, not a hand-written
  equivalent.
- **Driving a real running client from here (when not using the embedded
  MCP server, design.md 3.13):** PowerShell `SendKeys`/`keybd_event` +
  `SetForegroundWindow` + `PrintWindow` screenshots. `SendKeys` cannot hold
  a key (only rapid down/up) — use `keybd_event` with an explicit down,
  sleep, up for anything needing a genuine hold. `SetForegroundWindow`
  silently fails (no error) when Windows' foreground-lock heuristic blocks
  it — precede it with a `keybd_event` Alt key-tap, and always verify with
  `GetForegroundWindow()` before sending input, especially with two
  automated windows open. Use `PrintWindow` (`PW_RENDERFULLCONTENT`), not a
  screen-region `CopyFromScreen`, to capture one specific window regardless
  of z-order/overlap.
- **DPI awareness must be consistent within one script that both reads
  window geometry and sets cursor/click positions** — `SetProcessDPIAware()`
  (or its absence) has to match between the call that computes a target
  coordinate and whatever else reads geometry in that same sequence, or
  clicks/screenshots silently land on the wrong physical pixel on a scaled
  display (this environment runs at 125%).
- **Disposing `this` mid-method:** every subsequent line in that call (and
  the rest of that frame, up the call stack) must not touch what was just
  disposed — return immediately, don't fall through. Has caused a crash the
  very first time a button triggering a screen-transition-plus-dispose was
  actually pressed, never caught by a static screenshot or unit test.
- **A key's "just pressed" state can bleed into the very next screen's
  first frame** after a screen switch mid-frame (libGDX's flag lives for
  exactly one frame, which can still be true on the newly-shown screen). A
  screen reusing a key another screen also uses should absorb one frame of
  input right after `show()`.
- **A punctuation keycode isn't necessarily one the backend ever emits.**
  `Input.Keys.PLUS` exists but the lwjgl3 backend never produces it —
  check `DefaultLwjgl3Input.getGdxKeyCode` (in the backend's `-sources`
  jar) before picking any punctuation keycode as a keybind default, or the
  binding is silently dead. Related: the main-row `+`/`-` are different
  *physical* keys on a US vs. German layout, and libGDX reports physical
  position, so the numpad's own `NUMPAD_ADD`/`NUMPAD_SUBTRACT` are the
  only keys labeled "+"/"-" on both.
- A no-arg `TextureRegion()` needs an explicit `setRegion(Texture)`/full
  overload before first use — the UV-only `setRegion(u,v,u2,v2)` overload
  alone leaves the underlying `Texture` `null`.
- `TexturePacker.Settings.combineSubdirectories` defaults to `false` — set
  `true` or each subfolder under a packed source dir gets its own atlas
  page instead of sharing space.
- A `SpriteBatch.getColor()`/any libGDX getter returning a live mutable
  field needs an explicit `.cpy()` before a "save, change, restore" tint
  pattern — assigning the reference doesn't save a snapshot.
- A per-entity resource that outlives a single frame (a sound loop, a
  pooled effect holding external state) means *every* existing removal site
  for that entity's map needs checking, not just the obvious one (death) —
  a bare `removeIf`/`keySet()` one-liner is a sign a removal site might be
  silently discarding a value that needed cleanup first.

## Asset pipeline

- `assets-raw/<category>/...` — source files, committed (doubles as backup
  for art that otherwise only exists on a local drive). `assets/` — what
  the game actually loads at runtime. Never load loose PNGs from
  `assets-raw/` directly.
- Atlases generated via libGDX `TexturePacker`, driven by `AtlasPacker`
  (`lwjgl3/src/test/.../tools/AtlasPacker.java` — deliberately under
  `src/test`, `gdx-tools` is test-scoped, so neither ends up on the shaded
  runtime jar). Regenerate whenever `assets-raw/` changes:
  ```
  mvn -q -pl core -am install -DskipTests
  mvn -pl lwjgl3 dependency:build-classpath -Dmdep.outputFile=target/test-cp.txt -DincludeScope=test
  cd lwjgl3 && java -cp "target/classes;target/test-classes;$(cat target/test-cp.txt)" de.mkoehler.starwars.lwjgl3.tools.AtlasPacker
  ```
  Add a `pack(...)` call per new raw asset folder. **Running it always
  repacks every atlas, including unrelated ones** (e.g. `ships.atlas`) —
  check `git status` afterward and `git checkout --` anything that
  shouldn't have changed before committing.
- **Numeric-suffix filenames** (e.g. `mine_0000.png`..`mine_0063.png`, or a
  ship's bank-angle `_0020.png`) pack into one indexed region set
  automatically — retrieve via `TextureAtlas.findRegions(name)` (ordered
  `Array`) or `findRegion(name, index)`. A source file that doesn't follow
  this exact suffix convention packs as a plain unindexed region instead,
  and `findRegion(name, index)` silently returns `null` for it — this has
  caused a real spawn-time NPE crash once (a ship's art file was missing
  the suffix). Confirm the actual region count/indices after packing when
  this matters, don't assume.
- **Atlas-pack art that gets batched together in one draw call** (ship
  frames, HUD icons); **load-on-demand as plain `Texture`s for art where
  only one of many variants is ever shown at once** (e.g. Death Screen
  quote cards — one of 23 shown per death). Packing the wrong kind forces
  megabytes of unrelated pages onto the GPU synchronously on the render
  thread — caused a real multi-second freeze once.
  `assets-raw`/`assets` `.psd` source files the user drops alongside
  generated art are never touched by the atlas pipeline (only `ships`/
  `projectiles`/`menu`/`asteroids`/`powerup`/`mine` are packed) — safe to
  leave alone regardless.
- **Tileable backgrounds are never atlas-packed** (a shared atlas page
  bleeds at tile edges under `TextureWrap.Repeat`) — loaded as their own
  plain `Texture` straight from `assets/`.
- `Music` (streamed, e.g. background themes/ambience) vs. `Sound` (fully
  loaded, short clips) — use `Sound` for anything needing an independently
  volume-adjustable/stoppable *instance* of a continuous loop
  (`Sound.loop()` returns an id; `setVolume(long, float)`/`stop(long)` act
  on just that instance); `Music` has no such per-instance control, fine
  for a one-shot-per-screen track.
- A generated (Python/Pillow) UI asset should check
  `C:\Windows\Fonts`/`...\AppData\Local\Microsoft\Windows\Fonts` for a font
  already used elsewhere in the game's existing art (this project uses "SF
  Distant Galaxy" throughout) before picking a generic substitute — several
  of this project's fonts are installed locally, not guessable from the art
  alone. Always composite generated overlay art (rings, cones, sliders,
  tooltips) onto the *real* background before judging it, not a blank/white
  canvas — subtle alpha reads very differently on a dark panel.

## Git / GitHub

- Commits are GPG-signed (see the Kleopatra note above).
- `_gradle-legacy-backup/` at repo root holds the pre-Maven-migration
  Gradle build (gitignored) — the user's escape hatch, ask before deleting.
- Release tags are plain `vX.Y.Z`, annotated (not GPG-signed), summarizing
  what shipped since the last tag. Pushing a tag triggers both
  `release-client.yml` (Windows jpackage zip) and `release-server.yml`
  (Docker image to GHCR) automatically.

## Code style & testing

- **Javadoc — comprehensive, on every class and method**, HTML-formatted.
  Strictly *what the code does/is for* — parameters, return values,
  invariants. **Never** session/debugging narrative ("fixed in session X
  because of bug Y") — that belongs in the commit message.
- **JUnit 5, used selectively.** Core, logic-heavy/pure components get
  tests (message serialization, damage-split formulas, spawn-point picking,
  turret lead-angle math, collision-filter replication, etc.) — thin
  Box2D/Ashley/`Gdx.input`/`SpriteBatch` wiring and rendering code does not.
- **JSON — Jackson** (`jackson-databind`) everywhere: accounts store,
  client connection/keybinds/audio-settings config. One library, plain
  bean-style classes (public no-arg constructor + getters/setters) — as
  opposed to the immutable-final-field style used for Kryo network message
  classes.

## Conventions / preferences

- **Design decisions go into `design.md` immediately**, same session as
  the decision — not summarized after the fact.
- **When a sub-detail is left unspecified, propose a concrete default
  directly in the doc and flag it as unconfirmed**, rather than stalling to
  ask. Reserve `AskUserQuestion`/a real pause for choices that are
  foundational/hard-to-reverse (a core library, a balance-defining formula),
  not every minor default.
- **Proactively flag security-relevant concerns during design even
  unprompted** (e.g. "hash the password, don't store it raw") — kept brief,
  not a lecture.
- Sessions are incremental (evenings) — always leave `design.md` and this
  file fully in sync before a session ends, don't just describe changes in
  chat.
- **The user has Photoshop and will manually edit image assets on
  request** — if a texture needs a transformation easier done by hand
  (resize, re-tile, add an alpha channel), just ask.
- **Standing permission to extend the embedded MCP interface** (design.md
  3.13) whenever it would help verification — new tools, more screens,
  richer state — without asking each time; still worth a brief mention of
  *why* when it comes up.
- **Hand-tuned numeric constants the user has explicitly play-tested and
  settled on should not be "fixed" toward a calculated value if they come
  up again** — ask before changing further. Known examples: X-wing thrust
  200N/torque 150N·m; Snowspeeder thrust 150N/torque 50N·m plus a 0.5
  engine-turn-response exponent (deliberately different baseline handling
  from every other ship); missile thrust 15N/torque 1.0N·m.
- **Advisor before and after** on any multi-file feature: consult before
  writing code (catches design-level issues — missing reciprocal collision-
  mask edits, tick-ordering traps, unclamped physics risks) and again once
  the implementation looks complete (catches integration-level issues). Two
  separate passes have repeatedly caught different classes of bug.
