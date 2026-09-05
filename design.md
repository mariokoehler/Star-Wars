# Star Wars — Design Document

This is a living document. It describes what the game is, the architecture we're
building it on, and the outstanding work broken into components. Keep it in sync
as decisions are made — update it in the same session as the decision, not after.

## 1. Concept

An online multiplayer, top-down deathmatch shooter set in the Star Wars universe.
Players fly iconic starships against each other with a Newtonian flight model
(momentum/inertia-based movement, not arcade "instant turn and stop"). Up to
8 players per match. Simple XP-based progression unlocks additional starships
to fly. A power-distribution mechanic (2.2) adds a layer of tactical
decision-making on top of straightforward dogfighting.

- **Genre:** top-down arena shooter / deathmatch
- **Players:** 2–8 per match
- **Flight model:** Newtonian (thrust, inertia, angular momentum — not
  velocity-capped arcade controls)
- **Progression:** earn XP from matches → unlock more starships. No other
  meta-progression planned for v1 (no skill trees, no cosmetics yet).
- **Session shape:** dedicated server on a public IP/hostname; players download
  a client build and connect to a server address. No matchmaking service, no
  server browser for v1 — you connect directly to a known address.
- **Players are international** (playing with friends in the UK, Belgium and
  Norway) — non-US/UK keyboard layouts (e.g. Belgian AZERTY) are a real
  consideration, not an edge case. See 3.8 / 5.2.
- **Fan project note:** this uses Star Wars ships, characters, quotes and
  likenesses purely as a non-commercial personal project. Not for
  distribution/monetization — keep that in mind if that ever changes.

## 2. Core gameplay mechanics

### 2.1 Newtonian flight

Covered in detail once implementation starts (see 3.3 for the Box2D-based
approach). Default controls are in 5.3.

### 2.2 Power distribution

Each ship has a power core producing a fixed amount of power per second,
divided between three systems: **Shields**, **Weapons**, **Engines**. How a
player allocates that power is a real-time tactical choice, not a one-time
loadout decision:

- **Shields** — more power = faster shield regeneration.
- **Weapons** — more power = higher rate of fire.
- **Engines** — more power = more thrust and better overall agility
  (turn rate).

**Weapon capacitor — decided:** a single shot's energy cost is bigger than
what the power core can deliver in one frame, so the weapon system needs a
small energy buffer to fire from — otherwise its power requirement would
never be met in the instant the player presses fire, and the weapon would
simply never work. The core continuously trickle-charges a **capacitor**
(sized for roughly 5-6 shots' worth of energy at full charge) up to its
max; firing draws that shot's cost from the capacitor instantly, and
firing again before it's topped back up costs whatever's left stored —
this is what actually drives the practical rate of fire, on top of any
weapon-specific cooldown. More power routed to Weapons means faster
capacitor recharge (and thus a higher sustainable fire rate), not a
bigger capacitor. Capacitor charge level is server-authoritative
per-ship state and, like the power split itself, isn't synced to other
clients — same reasoning as below.

This also opens up natural v2+ hooks without needing a new system: a
pickup that temporarily raises max capacitor size, or an XP-spent
permanent per-ship capacitor upgrade. Both are out of scope for v1, but
worth keeping in mind while building the component so it doesn't need
reworking later.

**Shields and Engines don't need a capacitor — decided:** both scale
directly and continuously off the power available *that frame* (shield
regen rate; engine thrust/torque), with no shot-like instantaneous draw
to buffer for. Revisit only if a future mechanic wants an instant burst
from either (e.g. an afterburner or an emergency shield boost button) —
not planned for v1.

This is a continuous throughput split (percentages of current output), not
a stored/depletable battery — simple to reason about and to implement.

- **Baseline:** power is split evenly across all three systems (~33.3%
  each) by default.
- **Adjustment:** three keybinds, one per system (defaults in 5.3).
  Pressing a system's keybind shifts **+5%** to that system and **-2.5%**
  from *each* of the other two (net zero — always sums to 100%).
- **Reset keybind:** returns the distribution to the even baseline.
- **Floor — decided:** no system may ever drop below **10%**.
- **Clamping algorithm — decided**, applied on every adjustment keypress
  for the target system `A` and the other two systems `B`/`C`:
  1. **Normal case:** if both `B - 2.5%` and `C - 2.5%` stay `>= 10%`,
     apply that (`A += 5%`, `B -= 2.5%`, `C -= 2.5%`).
  2. **Redirect case:** if exactly one of `B`/`C` would drop below 10%
     under the normal case, but the *other* one can absorb the full
     `-5%` and stay `>= 10%`, take the whole 5% from that one instead
     (`A += 5%`, the constrained system is left unchanged, the other
     takes the full `-5%`).
  3. **No-op case:** if neither the normal split nor the redirect keeps
     every system `>= 10%`, the keypress has no effect this time.
  This guarantees the 10% floor is never violated without needing an
  upper-bound rule. Simulating repeated presses of the same system from
  the even baseline: the two losing systems reach the 10% floor together
  at roughly **78.3%** for the dominant system (case 3 kicks in from
  there) — comfortably under the "never above 80%" ballpark, so no
  separate upper-bound check is needed, matching the intent.
- **Not synced to other clients — decided:** an opponent's current power
  split is intentionally hidden. Other players can only *infer* it from
  observable secondary effects (faster fire rate, tighter turns, shield
  visibly regenerating quicker), never see the numbers directly. This also
  means allocation state doesn't need to go out over the network to
  anyone but the owning client (relevant for 3.5).

### 2.3 Leaving a match (ESC) and the combat lock

Pressing **ESC** during gameplay returns the player to Ship Selection (see
5.1). To prevent using ESC as a "safe escape hatch" out of a losing
dogfight, leaving is only allowed while the player is **not engaged in
combat**, defined as:

> more than 20 seconds have passed since the player last fired their
> weapon, **and** more than 20 seconds have passed since the player was
> last hit by another player.

If either condition isn't met, ESC is blocked. **Decided:** the client
shows a warning message — *"Emergency ejection not available during combat
operations"* — accompanied by a deliberately annoying sound effect, and
otherwise does nothing.

When a player does leave via ESC, **their ship explodes**, visually
indistinguishable to other players from being destroyed in combat — this is
mostly about reusing the existing destroy/death VFX for consistency rather
than a second anti-exploit layer (the combat lock above is what actually
prevents the abuse case). **Decided:** no kill credit/XP is awarded to
anyone for a self-destruct leave, since it wasn't caused by another
player's weapon. This behavior is server-authoritative — the combat-lock
check happens on the server, not the client, same as everything else
safety-relevant.

Note this differs from the death-by-combat flow: a self-destruct leave goes
straight back to Ship Selection, **not** through the Death Screen — the
Death Screen's "rub it in" quote is specifically for actually losing a
fight (see 5.1).

## 3. Architecture

### 3.1 High-level shape

Client/server, with an **authoritative dedicated server**. The server owns
simulation truth (positions, hits, deaths, XP); clients render, collect input,
and predict their own ship locally for responsiveness, reconciling against
server state as it arrives. This is required for a competitive shooter — an
authoritative server is the only way to keep hit detection and physics fair
against a client that could otherwise lie about its own position. It's also
what makes the combat-lock rule (2.3) trustworthy — the server tracks
last-fired/last-hit timestamps itself, not the client.

### 3.2 Modules (Maven)

Current repo layout (see root `pom.xml`):

- `core` — shared code: the network layer (message classes,
  `NetworkServer`, `NetworkClient` — see 3.4/3.5) used by **both** client
  and server, plus `de.mkoehler.starwars.sim` (Ashley/Box2D ship
  simulation — components, systems, `ShipFactory`, `ShipStats`).
  **Updated (2026-09-05): `sim` is effectively server-only now** — since
  the server is the sole simulator of ship physics (3.5), only
  `GameNetworkServer` (in `server`) actually uses it; the client no
  longer runs Box2D/Ashley for ships at all, it just renders network
  snapshots. Kept in `core` anyway (not moved into `server`) since it has
  no libGDX-backend-specific dependency and a future client-side
  prediction milestone will very likely need the client to run this same
  simulation code locally again.
- `lwjgl3` — desktop client (rendering, input, audio, UI). No longer
  depends on Box2D/Ashley for gameplay as of 2026-09-05 (see above) —
  still depends on `gdx-box2d-platform` transitively via `core`, harmless
  to leave in place.
- `server` — dedicated server module, built on `gdx-backend-headless`
  (`com.badlogicgames.gdx:gdx-backend-headless`). Runs the standard
  libGDX application lifecycle (`create()`/`render()`/`dispose()`,
  `Gdx.app`, `Gdx.files`, `Gdx.net`) with no window/graphics/audio, while
  depending on `core` directly. **Implemented (2026-09-05):** `GameServer`
  is a thin wrapper starting/stopping `GameNetworkServer` and calling its
  `tick()` every `render()`; `ServerLauncher` is the process entry point.
  Needs **two** native libraries despite being headless: `gdx-platform`
  classifier `natives-desktop` (some core libGDX utilities, e.g.
  `Gdx.files`, are backed by native code regardless of backend) **and**,
  since 2026-09-05, `gdx-box2d-platform` classifier `natives-desktop` too
  (the server now runs Box2D directly via `GameNetworkServer`) — both
  only surfaced by actually running the packaged jar, not by `mvn
  package` succeeding or tests passing.

**Decided (was tentative): no separate `network` module.** Shared wire
message/DTO classes live directly in `core`, under `de.mkoehler.starwars.net`
— simplest option for a handful of classes; revisit only if that package
grows large enough to justify splitting out.

### 3.3 Simulation stack (already implied by the generated project's deps)

The Liftoff-generated `core` module already pulls in:

- **Ashley** (`com.badlogicgames.ashley:ashley`) — Entity-Component-System.
  Ships, projectiles, pickups etc. become entities composed of components
  (`Position`, `Velocity`, `Thrust`, `Health`, `Weapon`, `PowerAllocation`,
  ...). This maps well onto networking too: components are natural units to
  serialize into state snapshots.
- **Box2D** (`com.badlogicgames.gdx:gdx-box2d`) — 2D rigid body physics.
  Plan is to drive the Newtonian flight model through Box2D bodies (apply
  force/torque for thrusters, let Box2D integrate velocity/angular velocity
  and handle collisions), rather than hand-rolling physics integration. The
  engine-power allocation (2.2) scales the force/torque applied per input,
  and thus feeds directly into this.

Both of these are treated as **decided**, not open questions — they're already
project dependencies and fit the requirements well.

**Updated (2026-09-05):** with ship physics now server-only, the
client-facing rendering classes from the single-player prototype
(`RenderSystem`, `SpriteComponent`) were deleted rather than kept unused
— the client draws ships directly from network snapshot data now, not
via Ashley. `PlayerInputSystem` was renamed to `ShipControlSystem` and
changed to read a `NetworkInputComponent` (updated from received
`PlayerInputMessage`s) instead of calling `Gdx.input` directly — this
also happens to make it fully headless-safe (no libGDX-input/graphics
dependency at all), which is exactly why it now runs server-side. A new
`ShipStats` class holds per-ship-type tuning (radius, thrust, torque) as
a small first step toward the "Ship roster (data-driven)" TODO (6) —
both the server (building the Box2D body) and the client (sizing the
drawn sprite consistently with it) reference the same constant, rather
than duplicating the numbers.

**Box2D units — important implementation note:** Box2D is tuned to work
well for objects roughly in the 0.1–10 meter range and its stability
degrades if it's fed raw pixel dimensions as if they were meters (large
values act like large masses/distances to the solver). We need one fixed
`PIXELS_PER_METER` conversion constant, applied consistently everywhere a
Box2D body's position/size is translated to/from screen-space sprite
coordinates — never mix the two unit systems ad hoc. **Proposed default
(unconfirmed):** 32 pixels per meter, chosen so ships work out to a
plausible few-meters "length" in Box2D terms; revisit once real ship
sprite dimensions (4.3) are actually plugged into a scene, since that's
what will make an off scale obvious.

**Fixed-timestep rendering requires interpolation — found and fixed
(2026-09-05):** `PhysicsSystem` steps Box2D at a fixed
`PhysicsConstants.TIME_STEP` (1/60s) via an accumulator, independent of
the variable render frame rate. Drawing entities at their raw,
just-stepped Box2D position causes visible jitter — some render frames
get 0 fixed steps, others get 1 or 2, so the on-screen position doesn't
advance by a consistent amount per frame even though the *simulation* is
advancing at a constant rate. This is imperceptible at low speed
(small per-step position delta) but very visible at higher/terminal
speed (large per-step delta) — exactly the symptom reported from
play-testing ("jitter only at terminal velocity, background stays
smooth"). **Fix:** `PhysicsBodyComponent` tracks a pre-step "previous"
position/angle snapshot (taken once per render frame, before that
frame's fixed steps run); `RenderSystem` draws at the position/angle
*interpolated* between that previous snapshot and the current (post-step)
state, using `PhysicsSystem.getAlpha()` (how far the accumulator is
toward the next step) as the blend factor — the standard "fix your
timestep" technique. `Client.java`'s camera-follow target uses the same
interpolated position, for the same reason. This is the same category of
technique (interpolating between two known simulation states using a
blend factor) that 3.5's "other clients' ships: interpolated/extrapolated
between received snapshots" will need over the network later — good to
have already exercised it locally.

**Superseded, kept for reference (2026-09-05, same day):** once ship
physics moved server-only (3.5's "networked ship movement" milestone),
this exact interpolation code was removed from `PhysicsSystem`/
`PhysicsBodyComponent` — the client no longer steps Box2D locally at
all, so there's nothing left to interpolate between on that side. Left
this section intact rather than deleting it, since the same technique
will likely return (applied to a locally-*predicted* position instead of
a locally-*stepped* one) once client-side prediction is implemented.

### 3.4 Networking

**Decision: [KryoNet fork](https://github.com/crykn/kryonet) —
`com.github.crykn:kryonet` (via JitPack), maintained fork of the original
`com.esotericsoftware:kryonet` (dead upstream since ~2014, but the protocol
design is sound and this is still the path of least boilerplate for a libGDX
project of this size).

Why this over raw Netty or hand-rolled UDP: KryoNet gives us a TCP (reliable)
+ UDP (unreliable) dual channel and automatic Kryo object serialization out of
the box, which is exactly the reliable/unreliable split a real-time shooter
needs (see 3.5), with far less protocol plumbing to write ourselves. We can
swap the transport later since game code will sit behind our own message
layer, not talk to KryoNet directly.

libGDX's own `Gdx.net` is **not** used for game traffic — it only offers HTTP
and raw TCP sockets, no UDP, and doesn't work on GWT. Not evaluated further
since there's no browser client planned.

**Implemented (2026-09-05), pinned to `kryonet` 2.22.9** (which bundles Kryo
5.5.0). The parent `pom.xml` adds the JitPack repository since this
dependency isn't on Maven Central. `de.mkoehler.starwars.net.MessageRegistry`
is the single place both ends register wire message classes with Kryo, in a
fixed order — required because Kryo identifies classes on the wire by a
registration-order id, not by name, so client and server must register
identically. `NetworkServer`/`NetworkClient` (both in `core`, no libGDX
dependency, so they're directly unit-testable) are thin wrappers around
KryoNet's `Server`/`Client` handling connection lifecycle, a handshake
(`HandshakeRequest`/`HandshakeResponse`), and a TCP + UDP ping/pong pair
proving both channels work end-to-end.

### 3.5 Netcode approach (movement now networked 2026-09-05; prediction still planned)

- **Reliable channel (TCP):** login/account handshake, join/leave, ship
  selection, spawn/despawn, death/kill events, chat, match state changes.
- **Unreliable channel (UDP):** per-tick position/velocity/rotation
  snapshots — frequent, latest-value-wins, fine to drop a packet since the
  next one supersedes it.
- **Not networked at all:** a player's own power allocation (2.2) — it's
  intentionally hidden from other players, so it never needs to leave the
  owning client/server pair beyond what's needed for the server to apply
  its gameplay effects.
- **Client-side prediction — still not implemented, deliberately
  deferred:** each client will eventually simulate its own ship locally on
  input immediately, then reconcile against the authoritative server
  snapshot for that ship. For now (2026-09-05 milestone, see below), the
  client does **not** predict — even the local player's own ship is drawn
  purely from server snapshots, meaning there's a visible network round
  trip between pressing a key and seeing the ship react. Accepted
  simplification for this milestone; implementing prediction is the next
  one, once the plain server-authoritative path is proven (below).
- **Other clients' ships — implemented, simplified:** eased toward the
  latest received snapshot every frame (the same "lerp toward a target"
  technique already used for camera-follow), not a proper timestamped
  interpolation-with-delay buffer. Good enough at LAN/loopback latency;
  revisit if it looks bad at real internet latency.
- Tick rate, snapshot rate, and interpolation buffer sizing: **still not
  tuned.** The server broadcasts one `WorldSnapshotMessage` every
  simulation tick (30Hz placeholder, see below) — snapshot rate and sim
  rate aren't decoupled yet.
- At 8 players, we can likely broadcast full world state to everyone (no
  interest management / area-of-interest filtering needed at this scale).
- **Server logging — decided:** plain `Gdx.app.log(...)`, available on the
  headless backend the same as on the client, rather than adding SLF4J/
  Logback — no need for a separate logging dependency at this scale.

**First implementation milestone (2026-09-05) — done:** connection
lifecycle (connect/disconnect), a handshake round trip, and a ping/pong
round trip over *both* the TCP and UDP channels, covered by
`NetworkServerClientIntegrationTest` (real loopback sockets, not mocked)
in `core`.

**Second implementation milestone (2026-09-05) — networked ship
movement, done:** the server is now the sole simulator of ship physics.
Architecture:

- **Player identity:** the server uses KryoNet's own `Connection.getID()`
  as the player id directly, rather than inventing a separate counter —
  one less thing to keep in sync, and KryoNet already guarantees
  uniqueness per connection.
- **New messages** (`core.net.messages`): `PlayerJoinedMessage`
  (server→client, sent right after handshake acceptance: assigned player
  id + spawn position), `PlayerInputMessage` (client→server, sent every
  frame over UDP: which of the 4 movement keys are currently held),
  `ShipState` + `WorldSnapshotMessage` (server→client broadcast over UDP,
  every tick: every currently-connected ship's position/angle),
  `PlayerLeftMessage` (server→client broadcast over TCP, on disconnect,
  so a departed ship disappears immediately rather than going stale).
- **`GameNetworkServer`** (`server` module) owns the authoritative Box2D
  `World`/Ashley `Engine`: on handshake, spawns a ship (fixed spawn point
  for now — real spawn points are a map/arena design question, §7);
  applies each player's latest received input to their ship every tick
  via `ShipControlSystem` (see below); steps physics; broadcasts a
  snapshot.
- **Client is now a pure "send input, render snapshots" loop** — it no
  longer runs Box2D/Ashley for ships at all (see 3.2/3.3's updated notes).
  This is *why* the local fixed-timestep interpolation work from the
  single-player prototype became temporarily unused: there's no more
  local physics stepping to smooth over. The same interpolation
  technique (blend between two known states via a factor) will very
  likely reappear once client-side prediction needs to reconcile a
  locally-predicted position against a server correction — the concept
  wasn't wasted, just its current call site.
- **Cross-thread correctness — decided and important:** KryoNet invokes
  connection/message callbacks on its own network thread, never the
  thread the simulation tick (or, client-side, rendering) runs on.
  Mutating Box2D/Ashley state (or the client's ship-rendering map)
  directly from inside a network callback would be a real, unpredictable
  concurrency bug — Box2D in particular is not thread-safe for concurrent
  modification. **Fix (applied on both ends):** network callbacks only
  ever enqueue a `Runnable` onto a `ConcurrentLinkedQueue`; the actual
  mutation happens later, drained at the start of the next tick/render
  frame on the single thread that owns that state. See
  `GameNetworkServer`'s and `Client`'s class Javadoc for exactly where
  this applies. This is a general pattern to keep following for anything
  else added to either side later, not a one-off fix.
- **Visual distinction (placeholder, confirmed good enough for now by
  the user):** since there's no ship selection/customization yet, the
  client tints every ship that isn't its own a light blue
  (`Client.OTHER_SHIP_TINT`) so two ships are at least tellable apart
  during testing. **Future direction (not yet scheduled):** once display
  names exist (3.6/3.7), show a player's display name as a floating
  label over/under their ship instead of relying on a color tint —
  works regardless of how many players share the same ship type, unlike
  a fixed tint palette. Keep the tint for now; this is a HUD-adjacent
  feature to pick up alongside that work, not blocking anything current.
- **Verified (2026-09-05):** ran one real dedicated server process plus
  two real client processes simultaneously on the same machine (exactly
  the "single machine, two clients" testing setup the user needed) —
  both connected, ran for several seconds exchanging input/snapshot
  traffic continuously, and disconnected cleanly, with zero exceptions on
  either side. This is real interprocess verification over real sockets,
  not just the in-process JUnit integration test.
- **Multi-instance testing — no special handling needed:** two client
  processes connecting to one server on one machine work with no extra
  configuration — the server already accepts multiple simultaneous
  connections, and each client uses an OS-assigned ephemeral local port,
  so there's no port collision between client instances. Recommended way
  to test locally: `mvn clean package` once, then run
  `java -jar lwjgl3/target/StarWars-<version>.jar` from two separate
  terminals — cleaner than running two concurrent `mvn exec:exec`
  processes.

### 3.6 Accounts & persistence (server-side)

**Decision:** accounts are stored server-side in a flat JSON file (no
database for v1 — player counts are small and this keeps the dedicated
server dependency-light and easy to back up/inspect by hand).

A `PlayerAccount` record has:

- `login` — unique login name, used to identify the account.
- `passwordHash` — **never** store the raw password. Hash + per-account
  random salt server-side (e.g. `SHA-256` with a random salt is enough for
  this project's threat model — no need to pull in bcrypt/Argon2 for a
  hobby dedicated server, but don't store plaintext).
- `displayName` — the name other players see in-game. Separate from
  `login` so a player can present differently than they authenticate.
  **Decision: not required to be unique** — two accounts can share a
  display name, not worth the complexity of enforcing otherwise.
- `xp` — total accumulated XP, drives which ships are unlocked (a ship's
  unlock is an XP threshold check against this value — no separate
  "unlocked ships" list needed).

**Account creation flow — decided:** no separate registration step.
Connecting with a `login` that doesn't exist yet on the server auto-creates
the account using the supplied password/display name. Connecting with a
`login` that already exists requires the password to match, otherwise the
connection is rejected with an error shown on the client's connect dialog
(see 5.1). This matches the "fill it out once, then it's quick" experience
the client-side config (3.7) is also designed around.

Storage shape: a single JSON file (e.g. `server/data/accounts.json`)
mapping `login -> PlayerAccount`. Simple to reason about at this scale;
revisit only if the server needs concurrent multi-process access or the
account count grows large enough that rewriting the whole file on every XP
change becomes a problem.

### 3.7 Client local config (connection)

**Decision:** the client persists the four connect-dialog fields (server
host, display name, login, password) to a local JSON file after a
successful connect, and pre-fills the dialog from it on next launch — so
after the first connect, starting the game is a one-click "connect" for the
player.

- Storing the raw password locally in plaintext is a real, accepted
  trade-off for simplicity in this project (single-purpose game login, not
  meant to double as a general-purpose credential). Worth revisiting later
  (e.g. store a server-issued session token instead of the password once we
  have one) but not blocking for v1.
- File location/format: implementation detail for later (likely
  `Gdx.files.local("config.json")` or an OS user-config directory) — not a
  design-level decision.

### 3.8 Client local config (keybinds)

**Decision:** keybinds are fully player-configurable via a Keybind Setup
screen (5.2), persisted to a client-local JSON file, separate from the
connection config (3.7) so a player can reset their controls without
touching saved login info. Driven directly by the "playing with friends in
the UK, Belgium, and Norway" requirement — WASD is not universally
sensible (most notably for Belgian AZERTY keyboards, where the physical
key cluster used for movement is conventionally labelled ZQSD instead).

Implementation note to remember when we get there (not a design decision,
just worth not re-discovering later): libGDX's desktop backend (via GLFW)
reports key codes by **physical key position on a reference US layout**,
not by the character the OS layout actually produces. That means a
"press a key to bind" capture UI works correctly out of the box for any
layout (no per-country presets needed) — but if we want to *display* a
sensible label for the bound key (so a Belgian player sees "Z" instead of
a misleading "W"), we need to resolve the physical key to the localized
character the OS would actually produce, rather than hardcoding the
US-layout glyph as the label.

### 3.9 JSON serialization

**Decision: [Jackson](https://github.com/FasterXML/jackson) (latest
`jackson-databind`)** for every JSON file this project reads/writes — the
server-side accounts store (3.6), the client's connection config (3.7),
and the client's keybinds config (3.8). One library everywhere rather
than mixing libGDX's own `Json` class with something else, and Jackson's
annotation-driven `ObjectMapper` approach maps cleanly onto plain record/
POJO classes like `PlayerAccount` without needing libGDX-specific
serialization hooks.

## 4. Rendering & presentation

### 4.1 Camera

Two behaviors, both **decided** as goals; exact tuning is deferred until
there's a flyable ship to tune it against:

- **Speed-linked zoom:** the camera zooms out as the ship's speed
  increases, and back in as it slows. This is a gameplay-driven choice,
  not just style — at high speed a player's reaction time isn't enough to
  react to obstacles only visible at a fixed, close-in zoom, so zooming
  out trades detail for forward visibility precisely when it's needed
  most. Implementation is a zoom factor driven by current ship speed,
  presumably via a smoothed/clamped curve rather than a linear mapping —
  exact curve is a tuning pass, not a design decision.
- **Camera inertia:** the camera does **not** rigidly lock the ship to
  screen center. It instead eases toward the ship's position — tracking
  closely under normal flight, but visibly lagging behind for a moment
  during hard maneuvers (sharp turns, sudden thrust changes) before
  catching back up. Deliberate feel/juice (a dynamic, "weighty" camera
  reads better than a rigid follow), not a gameplay mechanic. A
  lerp/spring toward the target position rather than a hard snap is the
  obvious implementation.

**Partially implemented (2026-09-05):** `Client.java` currently does a
plain exponential-ease camera follow (lerp toward the ship's position
every frame) as part of the single-player flight prototype — this is
just enough to make flying testable, **not** the full speed-linked zoom
model above, which is still unimplemented.

**Window/viewport size — decided:** 1920×1080 initially
(`Lwjgl3Launcher`'s `setWindowedMode`) — plain 16:9 HD, chosen over the
Liftoff template's 640×480 default now that there's something worth
seeing on screen.

**Resize behavior — decided and implemented (2026-09-05):** the window
is user-resizable (libGDX's default), and resizing to a different aspect
ratio must **not** stretch/distort the rendered content — the player
should just see more (or less) of the world, matching the "more world
visible, no distortion" way most 2D games handle this. Implemented with
libGDX's `ScreenViewport` wrapping the `OrthographicCamera` in
`Client.java`, updated from `resize(width, height)`. This is a natural
fit specifically because our "world" units already *are* screen pixels
(everything is scaled through `PhysicsConstants.PIXELS_PER_METER` before
reaching the camera) — `ScreenViewport` keeps that same 1:1 pixel
mapping and just grows/shrinks the visible area with the window, rather
than rescaling content the way `StretchViewport` (the bug's cause) or
`FitViewport` (would add letterboxing instead) do. `viewport.update(...,
false)` on resize deliberately doesn't recenter the camera, so it keeps
following the ship rather than snapping back to the world origin.

Both are purely client-side presentation — they don't touch simulation
state and don't need to be networked, unlike the combat-lock timers in
2.3, which must stay server-authoritative. Every client can run its own
camera feel independently with no fairness implications.

### 4.2 Parallax starfield background

**Decided:** at least two parallax star layers behind gameplay, each
moving at a different fraction of camera movement (a distant layer
slower, a near layer faster) to fake depth cheaply — a simple, well-worn
effect for this genre/viewpoint that reads well for very little cost. A
third, near-static "very distant" layer is worth trying too, but two
layers is the committed v1 minimum.

**Implementation — decided and implemented (2026-09-05):**
`ParallaxBackground`/`ParallaxBackground.Layer` (`core`,
`de.mkoehler.starwars.render`) implement each layer as a single
seamlessly-**tileable** texture with `TextureWrap.Repeat` set on both
axes, sampled with a texture-coordinate offset that slides as
`cameraPosition * parallaxFactor`, and drawn as one quad covering the
current viewport — not discrete sprite copies tiled next to each other.
This covers an arbitrarily large/moving world with a single draw call
per layer and no "ran out of tiles" edge case. It's drawn in its own
`batch.begin()`/`end()` pass before the Ashley `RenderSystem` runs, so it
always sits behind every gameplay entity.

- **Art requirement:** every layer's image must tile seamlessly (edges
  match up), full stop. **Transparency is only required for every layer
  except the backmost one** — the backmost layer is opaque and fully
  covers the screen itself (replacing the plain space-black clear
  color), while every layer drawn on top of it needs transparency so the
  layers beneath still show through. This is looser than originally
  assumed; see the real asset below, which is opaque and works fine as
  the back layer specifically because of this.
- **Any number of layers, any mix of imagery is fine** — the code takes
  an arbitrary number of independently-configured `Layer`s. Perceived
  depth comes from the *relative motion* between layers (different
  `parallaxFactor`s), not strictly from using different images, though
  different imagery (e.g. a nebula behind sparser/denser star fields)
  reads even better when available.
- **Real asset in place (2026-09-05):** the backmost layer is now
  `assets/textures/backgrounds/blue_nebula.png` ("Blue_Nebula_08" from
  Screaming Brain Studios' *Seamless Space Backgrounds* pack, **CC0 1.0 /
  Public Domain** — no attribution required, license text kept at
  `assets-raw/backgrounds/blue-nebula/License.txt`, referenced in
  `README.md`). It's tileable but **not** transparent — exactly why it's
  the backmost layer. One transparent, procedurally-generated star layer
  (`PlaceholderStarfield.generate(...)`, same package — still a
  placeholder, per design.md's "Awaiting from the user" note in
  CLAUDE.md, until real transparent star art is sourced) is drawn on top
  of it for closer, faster-scrolling depth. Swapping either texture for
  something else is a one-line change in `Client.java` —
  `ParallaxBackground.Layer` doesn't care where its texture came from.
- **Bug found and fixed (2026-09-05):** the vertical scroll direction
  was inverted (moving north/south visibly scrolled the background the
  wrong way, while east/west was always correct) — raw OpenGL texture V
  runs opposite to world/screen Y, so the V texture-coordinate offset
  needs negating relative to camera Y (U needs no such negation for X).
  Fixed in `ParallaxBackground.Layer.render(...)`; caught via actual
  play-testing, not something a unit test would realistically have
  caught.

### 4.3 Ship sprites & animation

**Asset source — decided:** reusing the hand-drawn sprite sets from an
earlier version of this same game concept the user built several years
ago (source code no longer exists, but the art survived), currently at
`R:\StarWars\sprites` on the local machine — not yet copied into the
repo's `assets/` folder; that's a to-do for whenever rendering work
actually starts (see 6).

Inventory as surveyed on 2026-09-05, one subfolder per ship:

- **`falcon`, `tieinterceptor`** — a 41-frame bank-angle sequence at
  **two** resolutions each (`*128_####.png` and `*256_####.png`), plus
  `portrait.png`.
- **`snowspeeder`, `stardestroyer`, `tiefighter`, `xwing`** — a 41-frame
  bank-angle sequence at one resolution, plus `portrait.png`.
- **`turret`** — not a per-ship bank sequence; 8 flat pixel-size variants
  (`turret32.png` up through the largest), meant to be drawn **on top
  of** a hull sprite. Historically used on the Falcon and Star Destroyer
  for an autonomously-firing turret.
- **`mine`** — a 64-frame self-rotation animation, no bank angles (it's a
  deployable pickup/weapon, not a ship). Out of scope for v1 — see below.

**Bank-angle sprite convention** (for the ships that have it): all 41
frames face up/north; frame `20` is neutral bank, frames `0–19` are
increasing left bank, frames `21–40` are increasing right bank — sprite
index is a direct function of the ship's current bank/turn state.

**Decision for v1: render only the neutral-bank frame (`..._0020.png`)
per ship, not the full bank-angle sequence.** The bank sprites look
noticeably better and give a convincing pseudo-3D feel through turns,
but based on prior experience building this exact effect, they cause
real trouble for anything that needs to track a specific point on the
hull — engine glow particles, weapon muzzle origins, the turret overlay —
because that attachment point visibly shifts as the bank frame changes.
That was never fully solved last time (the turret in particular always
looked wrong mid-bank). Revisit bank-angle rendering post-v1, once
there's room to solve attachment-point tracking properly (e.g.
per-frame authored anchor points) instead of alongside everything else
under time pressure.

**Direct implications of that decision:** turret-equipped ships (Falcon,
Star Destroyer) still work fine in v1 using their neutral-bank frame plus
a static turret overlay; deployable mines are **out of scope for v1**
entirely (revisit alongside other pickup ideas, e.g. the capacitor
pickup mentioned in 2.2).

**Confirmed constraint:** the game is strictly 2D — every moving visual
element is a sprite (ships, projectiles, pickups) or a particle effect
(engine glow, explosions, muzzle flashes). No 3D models anywhere.

**Texture atlas pipeline — decided (2026-09-05):** loose PNGs aren't used
at runtime; sprites are packed into texture atlases with libGDX's
`TexturePacker` (`com.badlogicgames.gdx:gdx-tools`), which has a plain
`process(...)`/`main()` API — no GUI step needed, it's run as part of the
dev workflow.

- **Raw source images are committed to the repo** under `assets-raw/`
  (e.g. `assets-raw/ships/xwing/xwing128_0020.png`), copied in from
  `R:\StarWars\sprites` on a per-ship, per-frame basis as each is actually
  used — not a bulk import of the whole local sprite library up front.
  Committing the source (not just the packed atlas) doubles as backup for
  art that otherwise only exists on a local drive.
- **Packed atlases are generated into `assets/textures/`** (e.g.
  `ships.atlas` + `ships.png`), which is what the game actually loads at
  runtime via `TextureAtlas`.
- **`gdx-tools` is a test-scoped dependency** of `lwjgl3` (see
  `AtlasPacker` under its `src/test`), specifically so it never ends up on
  the shaded runtime jar's classpath — it's a build-time tool, not
  something the shipped client needs.
- **Regenerate the atlas whenever `assets-raw/` changes** with:
  `mvn -pl lwjgl3 -am install -DskipTests` (installs `core` locally so
  `lwjgl3` resolves it as a plain module dependency) then
  `mvn -pl lwjgl3 dependency:build-classpath -Dmdep.outputFile=target/test-cp.txt -DincludeScope=test`
  and run `AtlasPacker`'s `main()` with a classpath combining
  `target/classes`, `target/test-classes` and that file's contents — see
  CLAUDE.md for the exact commands; this isn't wired into a Maven phase on
  purpose, since it only needs to run when art actually changes, not on
  every build.
- TexturePacker automatically groups same-prefix, numbered files (like
  `xwing128_0020.png`) into one named, indexed region
  (`atlas.findRegion("xwing/xwing128", 20)`) — convenient if/when the
  bank-angle frames (above) are added to the same atlas later, since
  they'd automatically become one indexed animation rather than needing
  manual region bookkeeping.

### 4.4 UI framework

**Decision: [Scene2D](https://libgdx.com/wiki/graphics/2d/scene2d/scene2d)
(libGDX's built-in UI/scene-graph module) as the base, with
[VisUI](https://github.com/kotcrab/vis-ui) layered on top for the
menu-style screens.**

The UI need actually splits into two different shapes:

- **In-match HUD** — health/shield readouts, a radar/minimap, kill feed,
  scoreboard. Wants tight integration with the game camera/viewport and
  world state, is mostly custom-drawn bars/icons rather than standard
  form widgets, and is exactly what plain Scene2D (`Stage` + `Table` +
  custom `Actor`s, drawn through the existing `SpriteBatch`) is good at
  with no extra dependency.
- **Menu-style screens** — Connect Dialog, Keybind Setup, Ship Selection.
  Closer to a traditional desktop-app form: text fields, buttons,
  list/grid selection, validation, error messages. Plain Scene2D can do
  this too, but means hand-building and hand-skinning every widget.

Given that split, **VisUI is worth adding** for the menu screens: it's
built directly on Scene2D (same `Stage`/`Actor` model, so it composes
cleanly with plain Scene2D used elsewhere, e.g. the HUD), ships a
complete, decent-looking default skin so we're not authoring one from
scratch, and covers what the menu screens actually need (validated text
fields for the connect dialog, list/table widgets for ship selection,
dialogs for error messages) — meaningfully less boilerplate than raw
Scene2D for that part, with no lock-in away from Scene2D itself. It's
actively maintained and Apache-2 licensed.

**Considered and not chosen:** raw Scene2D everywhere (fine, but a lot of
hand-rolled skinning work for the form-heavy screens for no real payoff
here); Dear ImGui bindings (`gdx-imgui` and similar) — well suited to
fast internal debug/dev-tool UI, not meant for polished player-facing
menus, so not a fit here (could still be worth adding later purely as an
internal debug overlay, e.g. live-tuning Newtonian flight constants —
not a v1 concern).

## 5. UX flow

### 5.1 Screen flow

```
 ┌────────────────┐
 │  Connect Dialog │  (host, display name, login, password — prefilled
 │                 │   from local config if present)
 └───────┬─────────┘
         │ connect (creates account if login is new,
         │          else validates password)
         │ on failure: show error, stay on this screen
         ▼
 ┌────────────────┐  ◄──────────────► ┌──────────────────┐
 │ Ship Selection  │                    │ Keybind Setup    │
 │ (ships unlocked │◄───────────────┐  │ (remap controls, │
 │  by XP)         │                │  │  saved locally)  │
 └───────┬─────────┘                │  └──────────────────┘
         │ select ship                │ ENTER
         ▼                            │
 ┌────────────────┐   ship destroyed  ┌┴────────────────┐
 │    Gameplay     ├───────────────────►   Death Screen   │
 │                 │                  │ (random quote +  │
 └───────┬─────────┘                  │  matching image) │
         │ ESC (only if not in combat,└──────────────────┘
         │      see §2.3 — ship explodes)
         └──────────────────────────────► back to Ship Selection
```

- **Connect Dialog:** entry fields for server host, display name, login,
  password. Values persisted locally per 3.7. Failed auth (wrong password
  for an existing login) shows an error and keeps the player on this
  screen — it does not fall through to ship selection.
- **Ship Selection:** shown right after a successful connect, and is the
  screen the player always returns to (leaving a match via ESC, or after
  dying and pressing ENTER on the death screen). Ships available here are
  gated by the account's current XP. Also the entry point to the Keybind
  Setup screen (5.2).
- **Keybind Setup:** reachable from Ship Selection, lets the player remap
  every control; persisted locally per 3.8.
- **Gameplay:** the main match scene. Ends for this player either by
  pressing **ESC** while not in combat (2.3) — ship explodes, straight back
  to Ship Selection — or by the ship being destroyed by another player
  (→ Death Screen).
- **Death Screen:** shows a randomly-selected Star Wars quote paired with a
  matching image, meant to rub in the loss (e.g. Qui-Gon Jinn's "There's
  always a bigger fish!" paired with an image of the Naboo swamp monster
  chasing the sub in *The Phantom Menace*). Pressing **ENTER** returns to
  Ship Selection. This screen is reached **only** by dying in combat, not
  by a voluntary ESC leave.

### 5.2 Keybind Setup screen

Lists every remappable action with a "press a key to bind" capture field
(see 3.8 for why this approach is layout-safe). Actions to cover: thrust
forward/reverse, rotate left/right, fire, the three power-distribution
keys, and power reset. **ESC (leave match) is fixed, not remappable** —
decided: every keyboard/layout has an ESC key, so there's no
internationalization reason to expose it here, and it's simpler to keep it
hardcoded. Changes save immediately to the local keybinds file.

### 5.3 Controls (v1, defaults)

Keyboard only for now; more keybinds will follow as further features are
added. All of the below are just the **defaults** — every action is
remappable via the Keybind Setup screen (5.2).

- **W** — thrust forward
- **S** — thrust reverse / brake
- **A / D** — rotate ship (turning is rotational thrust, consistent with
  the Newtonian model — no instant-snap turning)
- **SPACE** — fire primary weapon
- **ESC** — leave match, back to Ship Selection (blocked while in combat,
  see §2.3)
- **Power distribution (2.2):** **I / J / L**, chosen because they form a
  triangle under the right hand (resting comfortably while the left hand
  stays on WASD) — confirmed: `I` = Shields, `J` = Weapons, `L` = Engines,
  with **K** (the natural center of the triangle) as the reset key.

This is the classic "Asteroids-style" Newtonian control scheme (rotate +
thrust along facing direction) — reasonable default, open to tuning once
it's actually flyable.

## 6. Components / TODOs

Rough build order — check items off as they land, add detail as sub-bullets
once a component is actually being worked on.

- [x] **`server` Maven module** — `gdx-backend-headless`, depends on `core`.
      Runs the placeholder 30Hz loop (3.5); the actual authoritative
      simulation loop is still to come.
- [x] **Networking layer (first milestone)** — KryoNet fork wired into
      `core`, shared message classes, connect/disconnect handling, a
      handshake round trip, and a TCP+UDP ping/pong round trip, covered by
      an integration test.
- [x] **Networked ship movement (2026-09-05)** — server-authoritative:
      `GameNetworkServer` spawns/simulates/despawns ships and broadcasts
      `WorldSnapshotMessage`s; clients send `PlayerInputMessage`s and
      render every ship (including their own) from received snapshots,
      no local physics. Verified with a real server + two real client
      processes on one machine. Still open: tick/snapshot rate tuning
      (3.5), client-side prediction/reconciliation (next milestone).
- [ ] **Account system (server)** — JSON-file-backed `PlayerAccount` store,
      auto-register-or-validate-on-connect flow, salted password hashing.
- [ ] **Client local config** — load/save connection fields (3.7) and
      keybinds (3.8) to separate local JSON files.
- [ ] **Connect Dialog screen** — host/display name/login/password fields,
      prefilled from local config, error display on failed auth.
- [ ] **Keybind Setup screen** — press-to-bind capture, localized key-label
      display (see 3.8 implementation note), persists to local config.
- [x] **Entity/component model (first pass, server-side)** — Ashley set
      up in `core` under `de.mkoehler.starwars.sim`, used by
      `GameNetworkServer`: `PhysicsBodyComponent`, `PlayerControlledComponent`,
      `PlayerIdComponent`, `NetworkInputComponent`, plus
      `PhysicsSystem`/`ShipControlSystem` and a server-oriented
      `ShipFactory`. Only covers one player-controlled ship type so far —
      projectiles/pickups aren't modeled yet. The client no longer uses
      Ashley at all (2026-09-05) — it renders directly from network
      snapshot data instead.
- [x] **Newtonian flight model (first pass, 2026-09-05; tuning in
      progress)** — Box2D force/torque on a fixed-timestep world, driven
      by input received over the network (`PlayerInputMessage` →
      `NetworkInputComponent` → `ShipControlSystem`), not local
      `Gdx.input` directly (that was true only for the earlier,
      superseded single-player-only version of this prototype).
      `PhysicsConstants.PIXELS_PER_METER` is a real code constant (still
      the proposed 32px/m default from 3.3, unconfirmed/untuned); thrust
      force, turn torque, and linear/angular damping are all placeholder
      `ShipStats.XWING` numbers meant to be tuned by feel — turn torque
      already bumped 15→22.5 N·m (+50%) after the first play-test felt
      too sluggish.
- [ ] **Power distribution system** — server-authoritative allocation
      state per ship (not networked to other clients), feeding shield
      regen rate / weapon fire rate / engine thrust & agility; the
      three-keybind +5/-2.5/-2.5 adjustment with the 10% floor/redirect/
      no-op algorithm (2.2), and reset-to-even logic.
- [ ] **Weapon capacitor** — per-ship energy buffer (2.2) that trickle-
      charges from the power core and drains per shot; recharge rate
      scales with Weapons power allocation.
- [ ] **Combat-lock ESC logic** — server tracks last-fired/last-hit
      timestamps per player, gates ESC-triggered leave on the 20s rule,
      triggers the self-destruct/explode VFX + blocked-ESC warning
      message/sound on leave attempts.
- [ ] **Weapons & projectiles** — at least one weapon type to start
      (blaster/laser cannon), hit detection, damage, death.
- [ ] **Ship roster (data-driven)** — stats (mass, thrust, turn rate, hit
      points, weapon loadout) per ship, starting with a small roster (2–3
      ships) before expanding.
- [ ] **Ship Selection screen** — lists ships unlocked by current XP.
- [ ] **Client-side prediction & reconciliation.**
- [ ] **Match/arena flow** — single continuous deathmatch arena for v1
      (join → spawn → fight → respawn on death); no lobby/matchmaking yet.
- [ ] **Camera system** — speed-linked zoom and inertia/lag-behind
      follow behavior (4.1).
- [ ] **Parallax starfield background** — at least 2 layers (4.2).
- [ ] **Ship sprite rendering** — import sprite sets from
      `R:\StarWars\sprites` into `assets/`; render the neutral-bank
      (`_0020`) frame per ship for v1, plus the static turret overlay
      for turret-equipped ships (4.3).
- [ ] **UI framework integration** — add VisUI on top of Scene2D (4.4);
      build out the Connect Dialog, Keybind Setup, and Ship Selection
      screens against it.
- [ ] **HUD** — health, shield, target/radar or minimap, kill feed,
      scoreboard. (No power-distribution readout for *other* players —
      that's intentionally hidden, see 2.2.)
- [ ] **Death Screen** — random Star Wars quote + matching image; needs a
      content pool (see 6.1) and ENTER-to-continue handling.
- [ ] **XP & progression** — award XP per match/kill, unlock additional
      ships at XP thresholds (persisted via the account system above).

### 6.1 Content TODO: death screen quotes

Need to compile a pool of (quote, matching image) pairs before this screen
can ship. Starting example:

- Qui-Gon Jinn — *"There's always a bigger fish."* — image of the Naboo
  swamp monster chasing the sub (*The Phantom Menace*).

Add more pairs here as we pick them; keep it to one clear "you just died,
here's the universe laughing at you" beat per entry.

## 7. Open design questions

Track unresolved decisions here so they don't get lost. Move an item into
the relevant section above once decided.

- **Client distribution**: how do players get the client build? Plain jar
  download for now is the likely v1 answer — revisit if that's too much
  friction.
- **Ship roster**: which specific iconic ships, and their relative
  stats/balance.
- **Map/arena design**: single arena to start — size, obstacles (asteroid
  fields? capital ship hulls?), boundary handling (do you die if you fly off
  the edge, or is it wrapped/bounded?).
- **Tick rate / snapshot rate** for the netcode.
- **Lag compensation** for hit detection (rewind-time hit registration vs.
  simple current-state checks) — matters more as ping increases.
