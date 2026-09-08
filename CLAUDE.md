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

**Accepted as-is, at the time:** interpolation reduced the jitter
significantly but didn't eliminate it completely at top speed — the
user tried it, judged the remainder good enough to live with for now
(half-joked it could even pass as an intentional near-max-speed
camera-shake effect). **Revisited and actually root-caused 2026-09-06**
once the user reported it again, this time with a phone photo showing a
clear two-position "ghost" — see design.md 3.3's addendum for the full
writeup: the "previous position" snapshot for interpolation was being
taken once per render *frame*, not once per fixed *step*, in both this
implementation and the original one, so a frame batching two steps
together (normal accumulator carry-over) rendered a whole step-pair
stale. Fixed by moving the snapshot into the same per-step
`beforeEachStep` callback already used for reapplying thrust — the
identical "once per call vs. once per step" mistake already fixed once
for force application, just not spotted here too until now. **General
rule worth remembering:** a user-accepted "good enough" tradeoff is a
snapshot of their patience at the time, not a permanent waiver — revisit
it seriously if they bring the same symptom up again, rather than
reciting the old acceptance back at them.

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

**Client-side prediction — implemented 2026-09-05, same session as the
lag was confirmed.** `Client` now runs its own local Box2D `World`/`Body`
for its own ship (`ShipFactory.createBody`, no Ashley involved — just one
body, doesn't need entity/component machinery), applying held input to
it immediately via `ShipControlSystem.applyInput(...)` (extracted as a
public static method specifically so client prediction and server
authority always run identical force/torque math — any divergence
between the two would otherwise show up as constant, unnecessary
reconciliation corrections). Each `WorldSnapshotMessage` now also
carries velocity per ship (`ShipState` gained `velocityX/Y`,
`angularVelocity`); for the local player's entry, `reconcileWithServer`
blends 20% toward the server's state for small errors (≤3m) or hard-
snaps position+angle+velocity for large ones (>3m). **Deliberately not
implemented:** sequence-numbered input buffering + exact replay (the
"proper" competitive-shooter technique) — judged more complexity than
this project needs; revisit only if the simplified blend/snap approach
misbehaves once tested over real (non-loopback) internet latency between
the actual UK/Belgium/Norway players, not just localhost. Other players'
ships are untouched by this — still pure snapshot interpolation, no
prediction, since you can't predict someone else's future input.

The old single-player-only fixed-timestep interpolation
(`PhysicsBodyComponent`/`PhysicsSystem.getAlpha()`) predicted to
resurface for this exact purpose (see the "Superseded" note added
earlier the same day) did resurface, just re-homed: `PhysicsSystem` was
untouched (it never needed Ashley), but interpolation state now lives as
plain fields directly on `Client` (`myPreviousX/Y/Angle`) rather than on
`PhysicsBodyComponent`, since there's exactly one predicted body to
track client-side, not a generic entity family.

Verified end-to-end: ran a real server + two real client processes for
the client-side-prediction code path specifically (not just the earlier
milestone's plain sync), continuous input+snapshot+reconciliation
traffic for the full run, zero exceptions on any side.

**Important Box2D gotcha, found via play-testing (2026-09-05) — remember
this for any future force-applying system:** Box2D clears a body's
applied forces/torques after **every** `world.step(...)` call. If your
update rate is slower than the fixed physics timestep (our
`NetworkConstants.SIMULATION_TICK_RATE_HZ` = 30Hz server tick vs.
`PhysicsConstants.TIME_STEP` = 1/60s physics step means almost every
tick needs *two* steps), applying a continuously-held force/torque
*once per outer call* only survives into the first of however many
steps actually run — every further step in that call applies zero
force, silently and systematically weakening the effect. This is
exactly what made ship movement feel "like slow motion" compared to the
unnetworked prototype (not a numbers-tuning problem — `ShipStats.XWING`'s
thrust/torque were never wrong, they just weren't being fully applied),
and it's also what caused the local-prediction-vs-server jitter reported
in the same play-test (prediction ran at full strength, the
authoritative server didn't, so reconciliation was constantly fighting
a large systematic gap rather than smoothing small noise — one root
cause, two symptoms). **Fix, now the standing pattern:** `PhysicsSystem`
has an `update(float deltaTime, Runnable beforeEachStep)` overload that
invokes the callback immediately before every individual step; any code
applying continuous forces/torques (`GameNetworkServer.tick`,
`Client.predictLocalShip`) must go through that overload, re-applying
input inside the callback, not call `update(deltaTime)` once and apply
force separately beforehand. Apply this same pattern to any future
system that applies continuous (not one-shot/impulse) forces.

**Tuning follow-up (2026-09-05, same session):** with the force-halving
bug fixed, the user confirmed speed/turn rate now match the original
unnetworked prototype — then asked to double both anyway for a more
agile feel, partly because the bigger 1920×1080 view (vs. the original
640×480) makes the same absolute speed read as slower. After that
doubling (thrust 30→60N, torque 22.5→45 N·m), the user then hand-tuned
further themselves (editing `ShipStats.XWING` directly) and settled on
**thrust 200N / torque 150 N·m** as feeling good. These are empirical,
by-feel values, not derived from a formula — don't "fix" them toward a
calculated number if they come up again; ask the user before changing
them further.

**Weapons & combat (first pass) — implemented 2026-09-05.** See design.md
2.4 for the full writeup. Summary of what's new:

- **New sim classes** (`core.sim`): `WeaponStats` (one constant,
  `BLASTER`), `CollisionCategories` (Box2D filter bits so projectiles
  don't collide with each other), `ProjectileFactory`; components
  `HealthComponent`, `WeaponComponent`, `ProjectileComponent`; systems
  `WeaponSystem`, `ProjectileLifetimeSystem`. `ShipFactory`/`ShipStats`
  gained health + a fixture collision filter.
- **New messages** (`core.net.messages`): `ProjectileState` +
  `WorldSnapshotMessage` extended to carry them, `ShipDestroyedMessage`.
  `PlayerInputMessage` gained a `firing` boolean.
  **`PlayerJoinedMessage` was renamed to `ShipSpawnedMessage`** — it's
  now sent both on initial join and on respawn after death, so the old
  name stopped being accurate; same payload/handling either way.
- **Server (`GameNetworkServer`)** runs a real `ContactListener` for
  projectile-vs-ship hit detection. Important Box2D API rule followed
  here: **you cannot create/destroy bodies from inside a contact
  callback** — hits are collected into a pending list during the
  callback and only resolved (damage applied, projectile/ship bodies
  destroyed) right after `physicsSystem.update(...)` returns for that
  tick, same general "don't act mid-callback, defer and drain" shape as
  the existing cross-thread queue pattern, but for a different reason
  (a Box2D API constraint, not a threading one — this callback actually
  fires on the same thread as `tick()`, since Box2D contacts happen
  synchronously during `world.step()`).
- **Respawn:** on death, `ShipDestroyedMessage` broadcasts immediately,
  then a 3-second timer (`RESPAWN_DELAY_SECONDS`, per-player, tracked in
  `respawnTimers`) respawns the ship and sends that one player a fresh
  `ShipSpawnedMessage` directly on their own `Connection` — which
  required adding `connectionsByPlayerId` (populated via the same
  queued-action pattern as everything else network-callback-originated,
  for consistency, even though sending itself is thread-safe from any
  thread — only mutating simulation state isn't).
- **Client** sends `firing` alongside movement input, and renders
  projectiles the same way it renders other players' ships — snapshot-
  interpolated, no prediction, not even for the local player's own
  shots (deliberate simplification, see design.md 2.4). Own shots tint
  red, others' blue, via two small provided sprites packed into a new
  `projectiles.atlas` (see "Texture atlas pipeline" below).
- **No HUD, no health display, no kill credit/XP, no capacitor mechanic
  (2.2), no ship roster/balance** — this is a working first combat pass,
  not a finished system. Also: both spawn and respawn still use the same
  fixed (0,0) point (existing simplification from the movement
  milestone) — now more consequential since two overlapping ships means
  an easy point-blank hit; deliberately not fixed here, it belongs to
  the still-open "map/arena design" question.
- **Verified:** ran a real server + two real client processes for an
  extended (15s) session with the new systems (weapon, projectile
  lifetime, contact listener) active every tick — zero exceptions. Could
  **not** verify actual hit registration/damage/respawn behavior this
  way (no way to simulate real key presses from here) — that needs the
  user actually playing and firing at another ship.

**Real bug found via play-testing, fixed same day: spawning an entity
inside another entity's collider is dangerous in Box2D.** Projectiles
spawned at their shooter's exact position (perfectly overlapping that
ship's own collision circle). The self-hit check only skipped *damage*
for that pair, not the underlying *physical* collision — Box2D still
tried to resolve the overlap, and with two exactly coincident circles
the separation direction is undefined, so it fell back to some fixed
axis unrelated to the ship's actual facing — silently redirecting the
projectile's velocity. Symptom was specific and had thrown me off at
first purely from code review: shots tracked the ship correctly for the
first 180° of a continuous turn, then flew the *opposite* rotational
way past that, meeting up again after a full 360° — looked exactly like
an angle-wraparound bug, but wasn't; `rotateRad`/`lerpAngle` are already
correctly periodic/wraparound-safe everywhere they're used here, and
tracing through the math confirmed the spawn-direction calculation
itself was correct. The actual cause only became clear from thinking
about what Box2D physically does with two coincident circles, not from
the angle math. **General rule for later:** don't just skip *damage* for
a same-owner projectile/ship pair, prevent the *collision itself* via a
`ContactFilter` — and prefer spawning a new body just outside another
body's collider rather than exactly inside/on top of it, for any future
spawn-one-entity-near-another scenario (pickups, mines, etc.).

**Second real bug from the same play-test session, also fixed
2026-09-05: rendering lag, misreported as "the hitbox looks 4x too
big."** Other players' ships and every projectile were rendered by
*easing* toward the latest snapshot (same technique as camera-follow) —
but an exponential ease lags a moving target by an amount proportional
to its speed. At the rate this used, a 50m/s projectile lagged behind
its true position by roughly **5 meters at steady state** — over double
a ship's 2m radius. So a shot would already have registered its hit
server-side and vanished from the next snapshot while the client's
rendering was still catching up to where it truly was, making it look
like it disappeared "early" / like the target's hitbox extended well
past its visible sprite. Not a hitbox problem at all. **Fixed by
switching to dead reckoning:** extrapolate `lastKnownPosition +
velocity × timeSinceLastSnapshot` every frame instead of easing toward
a stale target — `ShipState` already carries velocity (added earlier
for reconciliation), and a projectile's velocity doesn't need to be sent
at all since it's fully determined by its (already-sent) angle and the
weapon's known constant speed. **General rule for later:** don't use an
"ease toward target" follower for anything that needs to track a
*fast-moving* networked entity precisely (hit-relevant rendering
especially) — its lag is proportional to speed and can get large very
quickly; dead reckoning (extrapolate using reported/known velocity) is
the correct tool once speed matters, easing is fine for slow/cosmetic
follows (camera-follow, still eased, is deliberately not hit-relevant).

Read `design.md` in full before continuing further implementation — this
project moves in explicit milestones the user signs off on one at a time,
not open-ended feature sprints.

**Polygon hitboxes & sprite attachment points — implemented 2026-09-05.**
See design.md 2.5 for the full writeup. New `dev-tools` Maven module (a
plain Swing app, no libGDX — run with `mvn -pl dev-tools compile
exec:java`, `core` must be `mvn install`ed first same as `server`) for
visually authoring, per ship sprite, a convex hitbox polygon and named
attachment points (`PROJECTILE`/`ENGINE`/`LIGHT`/`DAMAGE_SMOKE`), saved as
`<name>.meta.json` next to the sprite. New `core.sim.metadata` package
(`PixelPoint`, `ShipSpriteMetadata`, `ShipSpriteMetadataLoader`) is
Jackson's first real usage in this codebase (design.md 3.9). `ShipStats`
now loads `assets/shipdata/<name>.meta.json` (classpath) into an
`Optional<ShipSpriteMetadata>`; `ShipFactory.createBody` builds a
`PolygonShape` from it (≥3 points) instead of the old `CircleShape`, and
`WeaponSystem` spawns one projectile per `PROJECTILE` attachment point
instead of the old single fixed offset — both fall back to the previous
behavior when no metadata exists, which is every ship as of this writing
(no `.meta.json` has actually been authored yet, this milestone is
data-model + wiring only). `server`'s POM gained a `<resources>` block
bundling `assets/` (it didn't need sprite data before). Verified: full
`mvn clean verify` across all 4 modules (13 core tests +
`SpriteCoordinatesTest`'s 4), and a real server+client boot with no
metadata present — zero exceptions, confirming the fallback path.
**Editor tried by the user (2026-09-05), works well.** They authored the
real X-wing hitbox (7-point convex polygon) and all four attachment-point
types at `assets/shipdata/xwing.meta.json` — filename matched the
`shipdata/<name>.meta.json` convention exactly, no rename needed. One
usability piece of feedback applied: the sprite was still too small to
place points precisely at 2x, so `SpriteCanvas.ZOOM` is now **4x** (was
2x) — a single constant, nothing else needed to change since every
paint/pick/mouse calculation already went through it. Re-verified with
this real metadata in place: `mvn clean verify` green across all 4
modules, and a real server+client boot — the server now actually builds
the 7-point `PolygonShape` and the client's local prediction body does
too, zero exceptions either side. Not yet playtested for feel (does the
tighter polygon hitbox actually feel different in a real dogfight, do
the two `PROJECTILE` points fire visibly from both wingtip cannons) —
that needs the user actually flying/shooting, same as previous combat
verification steps.

**Real bug found via play-testing the authored X-wing metadata, fixed
same day: projectiles appeared to spawn translated forward of their
attachment point.** `GameNetworkServer.tick()` called
`weaponSystem.update(...)` *before* `physicsSystem.update(...)` — so a
projectile created this tick got swept forward by however many physics
steps this tick ran (~2, at 30Hz tick / 60Hz physics step) before its
position was ever broadcast, ≈1.7m for the 50m/s blaster. Same direction
as the attachment point, just translated along it — looked exactly like
a spawn-offset bug, wasn't. **General rule for later, same family as the
earlier dead-reckoning fix:** anything spawned mid-tick shouldn't be
stepped/moved *within that same tick* before its first observable
state (render, broadcast, etc.) — order tick logic so creation happens
after that tick's simulation stepping, not before. Fixed by swapping
`weaponSystem.update(...)` to run after `physicsSystem.update(...)` in
`tick()`. Re-verified: `mvn clean verify` green, real server+client boot
with the real `xwing.meta.json` metadata, zero exceptions.

**Unrelated flaky-test fix found while verifying this milestone
(2026-09-05):
`NetworkServerClientIntegrationTest`'s `findFreePort()`, called twice
back-to-back (`new ServerSocket(0)` opened then immediately closed each
time), reliably got handed the *same* port number both times on this
machine — Windows appears to hand back a just-closed ephemeral port
immediately on the next bind request. The test then tried to bind both
TCP and UDP test servers to that one port, and `NetworkServer.start()`'s
UDP bind failed with `BindException: Address already in use`. 100%
reproducible, not intermittent. **Fix:** open both `ServerSocket`s before
closing either (`findTwoFreePorts()`), so the OS can't hand back the same
number twice. **General rule for later:** never call a
"reserve-a-free-port via open-then-immediately-close" helper twice in a
row for two ports that need to be distinct — get both from open sockets
first, close both after.

**Shield/hull damage model + hull/shield status HUD — implemented
2026-09-05.** See design.md 2.6 for the full writeup. User provided HUD
art (`assets-raw/hud/`: holographic panel background, circular shield
ring, green-to-red X-wing hull gradient — all 512x512, sharing one canvas
for alignment) plus the exact damage-split spec (shield absorbs its
current-fraction share of each hit, e.g. 90% shield → 90/10 split) and
regen model (regenerating shield, non-regenerating hull beneath it).
Summary of what's new:

- **`ShipType` enum** (one value, `XWING`) — introduced now, ahead of a
  second ship type actually existing, so ship-type-keyed resources
  (`shipdata/<name>.stats.json`, `.meta.json`, `textures/hud/<name>_hull.png`)
  have one shared identifier instead of every consumer hardcoding "xwing".
- **`ShipTypeConfig`** (new Jackson bean, `shipdata/xwing.stats.json`,
  **required** unlike the optional sprite metadata) now holds what used
  to be hardcoded Java constants — radius/thrust/torque/hull-max, exact
  values preserved (200N/150 N·m/2m/100, still don't recalculate these
  toward a formula) — plus 6 new numbers: shield max capacity, shield
  recharge rate (both untuned placeholders: 100, 5/sec), and 4 HUD clip
  pixel coordinates (2 per overlay: shield 130–437, hull 175–377, both
  out of a 512px canvas). Extracted `ShipSpriteMetadataLoader`'s
  classpath-loading into a small generic `JsonResourceLoader` so this
  didn't duplicate it.
- **`HealthComponent` renamed to `HullComponent`**; new `ShieldComponent`
  (current/max/rechargePerSecond). New `ShipDamage.apply(shield, hull,
  damage)` — a pure, unit-tested (`ShipDamageTest`, 5 cases) function
  implementing the proportional split, including a self-decided overflow
  rule the spec didn't cover: if the shield's designated share exceeds
  what's left of it, the excess bleeds through to hull too rather than
  being silently absorbed for free. New `ShieldRegenSystem` (flat rate,
  no regen-delay-after-hit — a common enhancement, deliberately not
  built until asked for), run in `GameNetworkServer.tick()` after hits
  resolve.
- **`ShipState` gained 4 fields** (hull/shield current/max), broadcast for
  every ship (not just the local player) even though only the local
  player's HUD reads them today — cheap now, avoids a protocol change
  for a future enemy-health readout.
- **New `core.render.ShipStatusHud`** draws the widget: background panel,
  then a clipped shield ring, then a clipped hull silhouette, all at the
  same position — clipping is a bottom-anchored "fuel gauge": at 100% the
  full authored pixel range renders, below that only the *bottom* portion
  does, so the visible slice shrinks from the top down as health drops.
  The actual pixel math is a pure `HudGaugeClip.compute(...)`, pulled out
  specifically so it's unit-testable without a GL context (a `Texture`
  needs a running libGDX app; plain floats don't) — same
  logic/rendering split as `SpriteCoordinates` in the dev-tools editor.
  `HudGaugeClipTest` (6 cases) covers full/zero/half fractions, the
  shared bottom screen edge across every fraction, and clamping.
- **Client** uses a second, screen-space `OrthographicCamera` (updated on
  resize) and a second `batch.begin()/end()` pass after the world-space
  one to draw the HUD — safer than swapping `SpriteBatch`'s projection
  matrix mid-batch. Widget sits at a fixed bottom-left position (220px,
  24px margin, untuned placeholders); defaults to full hull/shield right
  at spawn/respawn so it doesn't flash empty before the first
  `WorldSnapshotMessage` arrives.
- **Verified:** full `mvn clean verify` (28 tests) across all 4 modules;
  a real server+client boot, zero exceptions; and — since I can't drive
  keyboard/mouse input into a running LWJGL window from here — a
  PowerShell screen-capture of the actual running client, confirming the
  widget renders correctly at full health (panel + full shield ring +
  full green hull gradient, all aligned). **Not verified this way: the
  partial-clip case under real damage** — only checked via
  `HudGaugeClipTest`'s pure math, not an actual live gauge drain; needs
  the user to take a real hit and confirm it looks right.

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

**Second atlas added (2026-09-05):** `projectiles.atlas`, from
`assets-raw/projectiles/` (`red_dot.png`/`blue_dot.png` — see design.md
2.4). `AtlasPacker.main()` now has two `pack(...)` calls; both run every
time it's invoked, so the one command above regenerates everything.

**Five more ships imported (2026-09-05): Falcon, Snowspeeder, Star
Destroyer, TIE Fighter, TIE Interceptor.** See design.md 4.3 for the
full writeup. Neutral-bank (`_0020`) frames only, copied from
`R:\StarWars\sprites\<ship>/` into `assets-raw/ships/<ship>/` — same
convention as the X-wing. Falcon/TIE Interceptor exist at both 128px and
256px; picked 256px (display size comes from `radiusMeters`, not source
resolution, so this was a free quality choice — flagged as a default,
not asked about). Portraits not imported — user is handling Ship
Selection screen assets separately. **Bug found while packing:**
`TexturePacker.Settings.combineSubdirectories` defaults to `false`, so
6 ship subfolders produced 6 separate atlas pages (`ships.png`..`ships6.png`)
instead of sharing space — fixed by setting it `true` in `AtlasPacker`;
all 6 ships now pack into one `1024×512` page. **Not done on purpose:**
no `ShipType` enum entries/`.stats.json`/`.meta.json` for these five yet
— the user is authoring hitboxes/attachment points next via the
`dev-tools` editor, which gained a `TURRET` suggested attachment name
for the Falcon's/Star Destroyer's turret mounts (turret weapon system
itself still not built). Making these ships actually spawnable/playable
is a later milestone. Verified: full `mvn clean verify` (28 tests,
unaffected by an art-only change), a real server+client boot on the
regenerated `ships.atlas` (X-wing rendering unaffected), and the
dev-tools editor launching cleanly.

**Ship Selection screen (first pass) — implemented 2026-09-05.** See
design.md 5.1 for the full writeup. **The app is now a `Game`, not a
single `ApplicationAdapter`** — new `StarWarsGame` is the real entry
point (`Lwjgl3Launcher` constructs it now, not `Client`), `Client` was
converted `ApplicationAdapter` → `Screen` (`create()`→`show()`, `render()`
gained its `delta` param, added no-op `pause()/resume()/hide()`). New
`ShipSelectionScreen`: cycles all six `ShipType`s (mouse click on
arrow buttons, or left/right arrow keys), Start button or ENTER hands
off to `new Client()`. Raw `SpriteBatch` + manual hit-testing, not
Scene2D/VisUI — deliberate, see the screen's class Javadoc; VisUI
(design.md 4.4) stays reserved for a screen that actually needs form
widgets. New `core.render.DialogLayout` (public, pixel-perfect
placement math, unit-tested — `DialogLayoutTest`, 6 cases) and
`core.render.ScrollingBackground` (autonomous tiled-background drift,
for screens with no camera to tie a parallax scroll to).

Assets: portraits imported for all six ships (`R:\StarWars\sprites\<ship>/portrait.png`
→ `assets-raw/ships/<ship>/`, picked up by `ships.atlas` automatically).
`Menu_Background.png` moved to `assets-raw/backgrounds/menu-starfield/`
(tileable, same "don't atlas-pack it" rule as `blue_nebula.png`). New
`menu.atlas` for the rest of `assets-raw/menu/` (dialog, arrows, start
button, description images).

**Two real bugs found while building this:**
- `ScrollingBackground`'s `TextureRegion` field used the no-arg
  constructor (leaves the underlying `Texture` `null`) and only ever
  called the UV-only `setRegion(u,v,u2,v2)` overload, which doesn't (and
  can't) attach a texture — crashed with an NPE on the very first frame.
  Fixed by constructing `new TextureRegion(texture)` in the constructor
  *body* (not a field initializer — `texture` isn't assigned yet when
  field initializers run). **General rule:** the no-arg `TextureRegion()`
  constructor needs an explicit `setRegion(Texture)`/full-overload call
  before first use; the UV-only overload alone isn't enough.
- Same `combineSubdirectories` gotcha as the ship-import bug above, this
  time for `assets-raw/menu/` — same fix (already flipped on globally).
  Also bumped `AtlasPacker`'s max page size 1024→2048: six 512×512
  portraits on top of existing hull frames no longer fit one page.

**Known limitation, deliberate:** Start always launches `new Client()`
regardless of which ship is selected/shown — only the X-wing has a real
`.stats.json` and fits `Client`'s current square-bounding-box rendering
(the Star Destroyer's sprite is 256×432, non-square). Selection itself
is fully real; wiring it into what's actually flown is a follow-up
milestone. Verified: full `mvn clean verify` (30 tests); a real client
boot + screenshot confirming correct layout (logo/dialog/arrows/
portrait/description all where specified); a real server+client boot
confirming Start correctly transitions into gameplay when a server is
reachable (confirmed the hard way — a stray Start/ENTER during testing,
with no server running yet, hit `Client.connectToServer`'s existing
"no Connect Dialog yet, a connection failure is simply fatal" behavior,
which is pre-existing/documented, not a new bug). **Not separately
verified:** arrow/Start-button hover-texture swap — implemented, but a
static screenshot can't show mouse-over; ask the user to check it by
moving the mouse over them.

**All six ships made spawnable/flyable — implemented 2026-09-05, same
day, once the user finished authoring the remaining `.meta.json`
files.** See design.md 2.7 for the full writeup. New `.stats.json` for
Falcon/Snowspeeder/Star Destroyer/TIE Fighter/TIE Interceptor —
thrust/torque/hull/shield/hud-clip numbers copied verbatim from the
X-wing's (explicitly requested: "use the same performance attributes for
all the ships for now"), but `radiusMeters` and two **new** fields,
`spriteWidthMeters`/`spriteHeightMeters`, are per-ship, derived from each
ship's real sprite pixel size — this is also what fixed the
square-bounding-box rendering limitation flagged in the previous entry:
`Client` now draws each ship at its own width/height instead of forcing
`height = width`, so the Star Destroyer (256×432, non-square) renders
correctly instead of squashed into a square.

**Bug in the above, found by the user and fixed 2026-09-06: deriving
`spriteWidthMeters`/`spriteHeightMeters` from source-art pixel dimensions
at one global `PIXELS_PER_METER` meant a ship imported at higher
resolution rendered — and, since Box2D derives mass from fixture area at
a uniform density, physically weighed — more, purely as an accident of
its art asset's resolution, not a deliberate choice.** Falcon/Star
Destroyer/TIE Interceptor were all imported at 256px "purely a free
quality choice" vs. the 128px X-wing/Snowspeeder/TIE Fighter — the user
correctly diagnosed this themselves (reported both the TIE Interceptor
rendering ~2x an X-wing's size and ships generally feeling too heavy)
before any fix was discussed. See design.md's addendum to this same
entry for the full writeup and exact values; short version:
`spriteWidthMeters`/`spriteHeightMeters` are gone entirely, replaced by
one per-ship `pixelsPerMeter` on `ShipTypeConfig`, used for
`ShipFactory`'s hitbox-polygon conversion and `WeaponSystem`'s
attachment-point conversion (both previously hardcoded to the global
rate — the same bug, just would have shown up as guns desynced from the
hull the moment only the hitbox got fixed). Rendered size is no longer
separately authored at all — `Client` now derives it every frame from
the real loaded `TextureRegion`'s actual pixel dimensions ÷
`pixelsPerMeter`, so visual size and physical hitbox size are tied to
one number and structurally can't drift apart again. **General rule
worth remembering for any future per-ship pixel-space data (hitbox
polygons, attachment points, and now this): a value picked "for free"
during asset import (like choosing a sprite's resolution) can silently
become gameplay-relevant later if anything downstream assumes a fixed
global pixels-per-meter — check whether it should be per-ship instead.**
Verified: full `mvn clean verify` green; real server+client boot flying
an X-wing and a TIE Interceptor, screenshots compared side by side
confirming genuinely comparable size now (not ~2x); zero exceptions.

**Follow-up the same day, once the user actually flew the corrected
ships:** confirmed it "feels much more consistent," plus two more asks.
(1) **Snowspeeder scaled to 75%** — should read as the smallest ship,
was sharing the X-wing/TIE Fighter's 4×4m. `pixelsPerMeter` 32→42.6667,
`radiusMeters` 2.0→1.5 (same 0.75× scale, keeping the
radius-equals-half-width convention every ship follows). (2) **TIE
Fighter's source art replaced** with a user-made 256×256 texture +
hand-authored `.meta.json` — this ship uniquely never had a 256px option
in the original sprite library the way Falcon/Star Destroyer/TIE
Interceptor did. Mechanically just the TIE Interceptor's own playbook
again: `pixelsPerMeter` 32→64 (same 4×4m real size, 2x the source
pixels), old `tie_fighter128_0020.png` deleted outright (this ship never
kept both variants side by side), new file dropped in, `tiefighter.
meta.json` overwritten with the new hitbox/attachment data,
`Client.hullRegionName`'s `TIEFIGHTER` case repointed to
`"tiefighter/tie_fighter256"`, atlas repacked via `AtlasPacker`.

**Process note:** the two new TIE Fighter files showed up as untracked,
very-recently-modified in `git status` *before* the user mentioned them
— caught mid-session, correctly left completely alone and flagged to the
user rather than assumed-safe-to-touch, exactly per the "investigate
before touching unfamiliar files" rule; turned out to be the user
working in the `dev-tools` sprite editor in parallel with this session.
Verified: full `mvn clean verify` green; real server+client boot flying
both the new TIE Fighter (crisp, correctly sized, no exceptions) and the
resized Snowspeeder, confirmed visibly smaller than the X-wing via a
side-by-side pixel crop.

**Ship type now flows through the whole stack:** `HandshakeRequest`
gained a `ShipType` (sent from the Ship Selection screen's pick),
`ShipSpawnedMessage`/`ShipState` each gained one too (other clients only
ever learn a ship's type from `ShipState`, never a spawn message meant
for someone else). `ShipType` is now `kryo.register()`ed like every
other wire type. `GameNetworkServer` tracks each player's requested type
(`shipTypeByPlayerId`, set at handshake, read again at respawn) and
spawns via `ShipStats.forType(...)` instead of hardcoded
`ShipStats.XWING`. New `ShipTypeComponent` (Ashley) lets `WeaponSystem`
look up an entity's own stats instead of assuming X-wing (the last
hardcoded spot). `Client` now keys a per-`ShipType` hull-sprite-region
map and tracks each remote/local ship's own type for correct
rendering; hull region names don't follow one naming convention, so
there's an explicit lookup table (`Client.hullRegionName`), same shape
as `ShipSelectionScreen.descriptionRegionName`. `ShipStatusHud` falls
back to the X-wing's hull art (and, since clip numbers are copied too,
correct clip range) for any ship type without its own HUD art yet.

**Real bug found via an actual end-to-end test (not just unit tests) —
fixed same day:** `ShipSelectionScreen.startMatch()` disposes this
screen's own textures/batch when switching to `Client` — but it ran
from inside `handleInput()`, itself called partway through `render()`,
so that same `render()` call kept going afterward and tried to draw
with the now-disposed `logoTexture`, crashing (`GdxRuntimeException: No
buffer allocated!`) the very first time Start/ENTER was actually
pressed — never caught by unit tests or a static screenshot, only by
actually pressing the button in a running client. Fixed by having
`handleInput()` report whether a transition happened and `render()`
returning immediately if so. **General rule:** disposing `this`
mid-method means every subsequent line in that call (and the rest of
that frame, up the call stack) must not touch what was just disposed —
return immediately, don't fall through.

**Verified, for real this time — not just build+screenshot:** used
PowerShell `SendKeys` (`SetForegroundWindow` + `SendKeys.SendWait`) to
drive actual keyboard input into the running LWJGL window — the only way
found to do this from here — to cycle the Ship Selection screen to the
Star Destroyer and press ENTER, then screenshotted the live gameplay
view confirming it renders at its correct non-square size with the
expected X-wing-fallback HUD hull art, and confirmed via server/client
logs a clean connect→spawn→snapshot round trip. Full `mvn clean verify`
(34 tests) green across all 4 modules. **Also found and killed a stray
`java -jar` server process left running from way earlier in this
session** that was silently holding `server/target/StarWars-Server-*.jar`
locked, breaking `mvn clean` (`Failed to delete ... .jar`) — a reminder
to actually check `Get-Process java,javaw` (or, better, list full
command lines via `Get-CimInstance Win32_Process`, which is what
actually found this one after the simple name filter didn't) when a
clean/build fails for a file-lock reason on Windows, rather than
assuming no Java process is running just because a plain name-based
check came back empty.

**All six ships got their own real HUD hull art the same day**, provided
by the user right after the above (`HUD_Status_Background_<Ship>.png`
per ship in `assets-raw/hud/`, copied to `assets/textures/hud/<name>_hull.png`)
— the X-wing-fallback screenshot above was accurate for exactly as long
as it took the user to send the real art. Each one's HUD clip pixel
range was computed (alpha-channel bounding box, ≥50/255 threshold) rather
than eyeballed — validated first against the X-wing's own known-correct,
user-supplied numbers (matched almost exactly) before trusting it for
the rest. See design.md 2.7 for the exact per-ship numbers. Re-verified
with a real server+client boot flying the TIE Fighter and the Falcon
(via the same `SendKeys` technique) — both show their own correct hull
silhouette in the HUD, not the X-wing fallback. Full `mvn clean verify`
still green.

**Power distribution + real weapon capacitor — implemented 2026-09-06.**
See design.md 2.8 for the full writeup. Before implementing, resolved two
things design.md left open via `AskUserQuestion` (both foundational/
gameplay-balance-defining, not minor defaults): build the real weapon
capacitor now (rather than defer it further, since power distribution
was its stated prerequisite) — "Build full capacitor now"; and the
power-fraction-to-effect-multiplier formula, since design.md 2.2 doesn't
specify one — agreed on a linear multiplier relative to the even
baseline (`fraction / (1/3)`), same formula for all three systems. New
pure/testable `PowerDistribution` (`core.sim`, + `PowerSystem` enum) —
immutable, implements the exact normal/redirect/no-op clamping algorithm
from 2.2, unit-tested including the ~78.3% ceiling example design.md
itself calls out. **Server-authoritative but never networked to other
clients by construction, not by filtering:** the owning client keeps its
own local copy in sync purely by applying the identical deterministic
`adjust`/`reset` transition to every keypress it also sends to the
server (over the *reliable* TCP channel — `PowerAdjustMessage` is a
discrete one-shot event, unlike per-tick UDP movement/fire input where a
dropped packet is harmless since it's resent next tick). Both sides
running the same pure function over the same in-order event stream means
the two copies can't actually diverge — no reconciliation logic needed
for this state, unlike ship physics.

New `PowerDistributionComponent` (Ashley) added to every ship
(`ShipFactory`); `ShipControlSystem`/`Client.predictLocalShip` scale
thrust/torque by the Engines multiplier before calling the shared
`applyInput`; `ShieldRegenSystem` scales shield regen by the Shields
multiplier. `WeaponStats`' old fixed-cooldown-only placeholder gained a
real capacitor (max charge, per-shot cost, base recharge rate);
`WeaponComponent` tracks current charge and gates firing on
`canFire()` (cooldown expired **and** capacitor charged enough);
`WeaponSystem` recharges it every tick scaled by the Weapons multiplier.
The old mechanical cooldown stays as a hard cap "on top of" the
capacitor, per 2.2's own wording, rather than being replaced by it.

New `render.PowerDistributionHud`, reusing `ShipStatusHud`'s
package-private `HudGaugeClip` clipping math — simpler than that widget
since there's one shared clip range for all three bars/every ship type,
no per-ship config threaded through. User's HUD art
(`assets-raw/hud/HUD_Distribution_*.png`) copied to
`assets/textures/hud/hud_distribution_*.png` (not atlas-packed, same
convention as the other HUD panel art). Placed directly right of the
existing hull/shield widget on screen — an untuned placeholder position.

**Verified for real, not just build+tests:** full `mvn clean verify` (43
tests) green across every module; a real server+client boot with zero
exceptions; and — via `SendKeys` — actually pressing the power keybinds
in a live client and screenshotting the result: repeated **L** presses
visibly redirected power into Engines (toward the ~78% ceiling) while
Shields/Weapons visibly dropped toward the floor, and **K** visibly reset
all three back to equal. One screenshot-capture gotcha hit along the way,
worth remembering: **a separate PowerShell process is not DPI-aware by
default, so `GetWindowRect`/`CopyFromScreen` return coordinates in a
different scale than the actual screen**, producing a screenshot that
silently shows the wrong window/region (looked like a real bug — a
"reset" keypress appearing to do nothing — until re-captured correctly).
Fix: call `[User32]::SetProcessDPIAware()` once at the start of *every*
such PowerShell invocation (it's a per-process setting, doesn't persist
across separate `powershell.exe` calls) before any `GetWindowRect`/
`CopyFromScreen` call. **Not verified this way:** actual gameplay feel
of scaled thrust/shield-regen/fire-rate in a real dogfight — needs the
user actually flying with power reallocated, not just watching the gauge
respond.

**Keybind remap + hold-to-maximize — same day, right after first
play-testing the above.** See design.md 2.8's follow-up note for the
full writeup. User feedback: I/J/L → Shields/Weapons/Engines didn't line
up with the HUD's left-to-right bar order (confusing), and wanted holding
a key to instantly max that system instead of only incrementing.
**Remap:** `J`=Shields, `I`=Weapons, `L`=Engines (`J`/`L` flank the reset
key `K` on the home row, matching the bars' left-right order; `I` takes
the one system left over, Weapons). **New:**
`PowerDistribution.maximize(target)` — an unconditional jump to
`target`=80%/others=10%, same "always succeeds" character as `reset()`,
unlike the incremental, sometimes-no-op `adjust()`. `Client` tracks each
of the three keys' held duration independently via a small per-key
`PowerKeyHold` (heldSeconds + already-maximized-this-press), firing
`maximize` once after 0.4s held (untuned) — a tap still just increments.
`PowerAdjustMessage` gained a `Kind` enum (`ADJUST`/`MAXIMIZE`/`RESET`)
rather than inferring the action from a nullable target, now that there
are two non-reset actions. **Verification gotcha worth remembering:**
the ~5% fraction differences from a couple of taps are only a few screen
pixels tall on this widget — not reliably visible by eye in a screenshot
thumbnail, even though the underlying numbers were correct (confirmed by
sampling exact pixel colors/rows with Python/PIL instead of eyeballing).
For the hold-to-maximize test specifically, `SendKeys` can't simulate an
actually-held key (it only sends rapid down+up per character) — used
`keybd_event` with an explicit key-down, a real `Sleep`, then key-up
instead, which did produce a real sustained `isKeyPressed` state and a
clearly visible (no pixel-measurement needed) result.

**Leaving a match via ESC + the combat lock — implemented 2026-09-06.**
See design.md 2.3 for the full writeup. New server-side
`CombatTimerComponent`/`CombatTimerSystem` (same shape as
`ShieldRegenSystem`) track seconds-since-last-fired/last-hit per ship,
marked by `WeaponSystem` (on firing) and `GameNetworkServer` (on
resolving a hit); unit-tested (`CombatTimerComponentTest`) per
CLAUDE.md's own testing-conventions section, which had named this exact
logic as a future test candidate. New payload-free, reliable-channel
messages `LeaveMatchRequest`/`LeaveMatchDeniedMessage`. A granted leave
reuses the *same* `ShipDestroyedMessage` a combat death sends (no VFX
exists for either path yet, so "visually indistinguishable" holds
trivially) but skips the respawn timer — `GameNetworkServer.
destroyShipEntity` was factored out of `handleShipDestroyed` so the new
`selfDestructShip` could share the teardown without that side effect.

**Client had no way to switch screens before this** — `Client`'s
constructor never took a `Game` reference (only `ShipSelectionScreen`
did), since nothing on the gameplay screen previously needed to leave it
itself. Gained one, threaded through `ShipSelectionScreen.startMatch()`.
Since a granted leave and a combat death share one message, `Client`
tells them apart with a local `leavingMatch` flag (set when the request
is sent, cleared on denial) rather than a protocol field. Reused the
exact disposed-screen-crash pattern/fix from `ShipSelectionScreen.
startMatch()` (a `transitionedAway` guard right after draining
`pendingUpdates`, before any batch/texture calls) since `Client` now
also disposes itself mid-frame on a granted leave.

**Two things flagged as deliberately incomplete, not gaps found by
accident:** the warning banner ("Emergency ejection not available during
combat operations") was initially plain programmatic `BitmapFont` text,
not pre-rendered art — a first for this codebase, whose UI text has
otherwise always been an image (Ship Selection's dialog/descriptions).
**Got real art the same day** (see below) — the "deliberately annoying
sound effect" design.md also calls for is still not implemented at all —
no audio has ever been wired into this project and no sound asset exists
yet, same "still awaiting from the user" status as the earlier star-dot
background art. The banner works fine without it.

**Verification gotcha, same family as the power-distribution one above:**
a first attempt to test the "denied" path — tap SPACE via `SendKeys`,
then ESC — was wrongly granted, because the tapped SPACE never actually
registered as a fire (same `SendKeys`-can't-hold-a-key limitation).
Switched to a genuine `keybd_event` hold for the fire key too, which then
correctly triggered the denial and the visible warning banner. Verified
for real: a fresh spawn's ESC is granted instantly (back to Ship
Selection, connection actually closes, a later Start reconnects cleanly
with a fresh player id); firing a real shot then pressing ESC is denied,
shows the warning, leaves the ship fully flyable; zero exceptions on
either side throughout. Not exercised: the 20-second window actually
elapsing and re-permitting a leave (would need a real 20s wait).

**Combat-lock warning banner — real art, implemented 2026-09-06, same
day.** See design.md 2.3's addendum for the full writeup. **The
`/design` skill (Claude Design's canvas editor) does not work on this
machine** — it requires Node.js or Bun to assemble its payload and
neither is installed; tried once, failed cleanly, no workaround
attempted (per the skill's own instructions not to hand-edit the
payload). Fell back to generating the art directly: a Python/Pillow
script built a holographic alert plate matching the existing HUD art's
material language (dark glass panel, glow, thin metal border — sampled
by eye from `hud_status_background.png`) but in warm red/amber for a
warning instead of the blue/lavender status readouts, reading "EJECTION
LOCKED / Combat systems engaged" (user explicitly approved shortening
the full sentence as long as the intent survives). **User caught a
real miss on the first draft**: it used a generic condensed sans
(Bahnschrift) for the header, and the user pointed out the game already
has "SF Distant Galaxy" installed and in use for "SELECT YOUR SHIP!"
and the logo — swapping to it immediately made the asset look like it
belonged to the game instead of bolted on. **General rule for future
generated art in this project: check `C:\Windows\Fonts` /
`...\AppData\Local\Microsoft\Windows\Fonts` for a font already used in
the game's existing art before picking a generic substitute** — several
of this project's title/logo fonts are installed locally, not obviously
guessable from the rendered art alone.

Shipped as `assets-raw/hud/HUD_Warning_EjectionLocked.png` /
`assets/textures/hud/hud_warning_ejection_locked.png`, standalone
`Texture` (not atlas-packed, same as the other HUD chrome). `Client`
draws it centered horizontally, near the **top** of the screen
(`WARNING_BANNER_TOP_MARGIN` below the top edge) rather than mid-screen
— explicit user request: the player's own ship sits near screen-center
via camera-follow, and this warning is most likely to fire during a
tense combat moment, so it must stay clear of the ship. Verified live
(same server+client/SendKeys setup): banner renders centered and clear
of the ship, no exceptions.

**Turret weapons (Falcon/Star Destroyer only) — implemented
2026-09-06.** See design.md 2.9 for the full writeup. Player-toggled
(**T**) autonomous weapon system, fully server-side: each `"TURRET"`
attachment point becomes an independently-tracking mount that scans for
the closest live enemy within its ship type's configured scan range
(30m, user-specified), leads its shot (a from-scratch quadratic
intercept solve, `sim.TurretAiming`, unit-tested including a full
independent geometric cross-check — needed because this project's
0-rad-is-north angle convention meant deriving a fresh
direction→angle inverse, `MathUtils.atan2(-dx, dy)`), and fires once
aligned and off cooldown, draining the **same shared capacitor** as the
main gun (only `WeaponSystem` recharges it, avoiding a double-recharge
bug for ships with both). New `TurretConfig` (scan range/cooldown/turn
rate) lives in each ship's `.meta.json`, sibling to
`ShipSpriteMetadata`. Turret art: `R:\StarWars\sprites\turret`'s 40px
variant for the Falcon, 32px for the Star Destroyer, matching each
ship's own `pixelsPerMeter` — imported the same way as ship art, no
`AtlasPacker` code changes needed.

**Real gotcha hit while verifying this live (two real client processes,
`SendKeys`/`keybd_event` + window-focus automation):** `SetForegroundWindow`
from an automation script **silently fails** (returns without error, no
exception) when Windows' foreground-lock heuristic blocks it — keys then
go to whichever window already had focus, not the intended target. First
symptom looked exactly like a data/rendering bug (one ship's turret
seemingly never rendering) but was actually a mis-targeted key press the
whole time (a "T" and a ship-selection sequence both landed on the wrong
of the two windows). **Fix that actually works:** simulate an Alt
key-tap (`keybd_event` down+up on VK_MENU) immediately before
`SetForegroundWindow` — satisfies the heuristic reliably — and always
verify with `GetForegroundWindow()` afterward before sending further
input; don't trust `SetForegroundWindow`'s return value or assume success
from timing alone. **General rule for future multi-window `SendKeys`
verification in this project: always do the Alt-tap-then-verify dance,
and when two automated windows are involved, confirm which one actually
received each input via a screenshot before drawing conclusions from an
absence of an expected effect** — screen-region `CopyFromScreen`
screenshots are also useless for telling two overlapping windows apart
(both grab whatever's topmost); use `PrintWindow` (flag `PW_RENDERFULLCONTENT`)
per-window instead, which captures a window's own content regardless of
z-order/overlap.

**Confirmed genuinely working, not just "no exceptions":** once keys were
verified reaching the right window, the full scan→track→lead→fire→hit→kill
loop was observed to be actually lethal — repeated real kills/respawns
with both turrets enabled, including from a real non-zero distance (flew
the Falcon away first via a held thrust key). **Found, not fixed (out of
scope, pre-existing):** because respawn still always uses the same fixed
origin point (documented limitation from the weapons milestone), an
enabled turret makes it much more consequential — respawning ships land
right back in another ship's turret range at point-blank distance,
producing a rapid kill/respawn loop. Belongs to the still-open "map/arena
design" open question, not to the turret feature itself.

**Projectile art upgraded from dot to oval — 2026-09-06.** See design.md
2.4's addendum for the full writeup. User feedback: the circular
`red_dot`/`blue_dot` sprites were hard to see and didn't convey motion;
replaced with elongated `red_oval`/`blue_oval` (7×10 vs. the old 7×7,
same convention as ship art — authored nose-up so it lines up correctly
under the existing rotation with no new math). **Turned out to already
be fully supported:** `Client.drawProjectiles()` was already rotating
each sprite to its broadcast travel angle every frame — invisible on a
circle, so nothing new to build there. Only real change:
`batch.draw(...)` previously reused one square `sizePixels` (from the
physical Box2D hit-radius) for both width and height; switched to a
separate width (still tied to the physical radius) and a height derived
from the region's own pixel aspect ratio, so the art alone controls how
elongated it looks. Old `red_dot.png`/`blue_dot.png` deleted outright
(fully replaced, not kept side by side — same call as the earlier TIE
Fighter art swap). Verified live: fired while rotated off-axis and
confirmed via a zoomed screenshot that the ovals point along the actual
diagonal travel direction.

**Connect Dialog + account system — implemented 2026-09-06.** See
design.md 3.6/3.7/4.4/5.1 for the full writeup. `StarWarsGame` now
starts on a new `ConnectScreen` instead of Ship Selection — logging into
a player account (auto-created on a new login, password-checked on an
existing one) is the first thing the app does. New server-side
`server.accounts` package (`PlayerAccount`, `PasswordHasher`,
`AccountStore` — SHA-256+salt, JSON file at
`Gdx.files.local("data/accounts.json")`, no libGDX dependency so it's
plain-JUnit-testable, 15 new tests) wired into `GameNetworkServer.
handleHandshake`. `HandshakeRequest` dropped its `shipType` field and
gained `login`/`password`; a new `SpawnRequest` (ship type only) is sent
separately once Ship Selection's Start is pressed — splitting "log in"
from "spawn a ship" was necessary because the old combined handshake
would've spawned a real ship into the world just to validate a password.
Client-side local config (design.md 3.7) is `net.ConnectionConfig` +
`ConnectionConfigStore`, saved only after a successful login.

**User asked for hand-made art here too, same as the combat-lock
banner** — generated via the same Python/Pillow + "SF Distant Galaxy"
font technique, but themed to match `Select_Ship_Dialog.png`'s navy/gold
palette (sampled directly from that file with PIL) instead of the
banner's warning amber, since this is a normal-flow screen, not an
alert. Scope call, flagged rather than asked: VisUI's *default* skin
covers labels/error text/the button's own label; only the dialog
background and the Connect button's two states got custom art — a full
custom VisUI skin (every widget re-themed) would have been a lot more
art for not much more payoff, and the default skin was easy to just
tint-match at the field level.

**Two real bugs found only by actually driving the screen with
SendKeys, not by reading the code:**
- **libGDX `TextField.focusTraversal` defaults to `true`** and handles
  TAB itself (in Stage actor-tree order) via its own listener, which
  runs *before* a stage-level listener added for the same purpose ever
  sees the event. Result: one TAB press advanced focus twice (confirmed
  by watching typed text land two fields ahead of where it should have).
  Fix: `field.setFocusTraversal(false)` on every field, so only the
  custom listener drives order. **General rule: any custom Tab/focus
  handling on a Scene2D `TextField` must disable the built-in
  `focusTraversal` first**, or the two will fight.
- **A key's "just pressed" state can bleed into the very next screen's
  first frame.** ENTER-to-submit-login on `ConnectScreen` occasionally
  also read as ENTER-to-start-match on `ShipSelectionScreen`'s first
  `render()` right after the screen switch, skipping ship selection
  entirely. Fix: `ShipSelectionScreen` now ignores input on its first
  frame after `show()` — libGDX's "just pressed" flag only ever lives
  one frame, so absorbing exactly one frame fully closes the gap.
  **General rule for any future screen transition that reuses a key
  (ENTER, ESC, etc.) across adjacent screens: don't assume a clean input
  slate on the new screen's first frame.**
- Also worth remembering: VisUI's `VisTextField.VisTextFieldStyle` adds
  its own `backgroundOver` field (hover-only) *on top of* the base
  `TextField.TextFieldStyle`'s `background`/`focusedBackground` —
  overriding only the base fields left a light hover box from the
  default skin showing through on mouse-over, clashing with the dark
  theme. Found by screenshot, not by reading VisUI's source; fixed by
  also setting `backgroundOver` to the same transparent drawable.

**Verified live, end-to-end, for real (same PowerShell `SendKeys`/
window-focus technique as every previous milestone):** typed all four
fields via TAB navigation alone, submitted with ENTER, confirmed a real
account written to `accounts.json` with a proper salted hash; retried
the same login with a wrong password, got the exact server-side
rejection message on-screen with every field left intact; fixed just
the password and resubmitted successfully; a fresh launch afterward
came up with all four fields correctly pre-filled from the saved local
config; completed the flow through Ship Selection into a real Falcon
spawn, confirming the second, fresh handshake + `SpawnRequest` path
works end to end. Also confirmed Shift+TAB wraps focus backward
correctly and that submitting with an empty field shows the client-side
validation error without attempting a connection at all. Full `mvn
clean test` (78 tests) green across every module throughout.

**VisUI libGDX-version warning, silenced 2026-09-06.** User noticed
`[VisUI] Warning, using invalid libGDX version. You are using libGDX
1.14.2 but you need 1.14.1.` on every launch. Checked Maven Central:
VisUI 1.5.9 (already what we use, and still the latest release as of
this writing) is itself pinned to gdx 1.14.1 in its own POM — there's no
newer VisUI to bump to yet. Downgrading our own `gdxVersion` back to
1.14.1 just to silence a UI toolkit's warning was judged not worth
losing whatever 1.14.2 fixed. Used VisUI's own intended escape hatch
instead: `VisUI.setSkipGdxVersionCheck(true)` before `VisUI.load()` in
`ConnectScreen` — confirmed via `javap` on VisUI's actual bytecode that
the flag genuinely short-circuits the whole check (not just the log
line), and this exact 1.14.1-expected/1.14.2-actual combination had
already been exercised live via this screen's own verification with
zero issues. **Gotcha hit fixing this:** the very first rebuild attempt
(`mvn -q -pl core -am install` then `-pl lwjgl3,server package`) silently
produced a **stale** jar — decompiling the packaged `ConnectScreen.class`
with `javap` showed the old bytecode (`isLoaded` → `load`, no
`setSkipGdxVersionCheck` call in between) despite the source and a
successful build. A full `mvn clean install`/`clean package` picked up
the change correctly. **General rule: if a real code change doesn't
show up in runtime behavior after a supposedly-successful rebuild,
suspect a stale artifact before suspecting the fix — verify by
decompiling the actual class inside the built jar (`javap -c` on an
extracted `.class`) rather than re-reading the source again.** Revisit
(delete the `setSkipGdxVersionCheck` call) once a VisUI release targets
gdx 1.14.2 or later.

**Death Screen — implemented 2026-09-06.** See design.md 5.1/6.1 for the
full writeup. User provided all 23 finished quote cards
(`assets-raw/after_death/Quote_1.png`..`Quote_23.png`) plus a shared
`Dialog_Background.png`, all 818×618. New `render.QuoteDeck` (shuffle-bag,
4 unit tests) guarantees no repeat until all 23 have shown, per the
user's explicit ask — session-local only, owned by `StarWarsGame` (not
`DeathScreen` itself, which is recreated fresh every death and would
otherwise forget what it had already shown). Every screen constructor
(`ConnectScreen`, `ShipSelectionScreen`, `Client`) was retyped from
`Game` to the concrete `StarWarsGame` so they can reach
`getQuoteDeck()` — there's only one `Game` implementation in this
project, so nothing was lost. Combat death now disposes and leaves the
match immediately (same teardown as a voluntary ESC leave) instead of
silently waiting for the server's old mid-match auto-respawn timer,
which is now simply never exercised by this client (still harmless
server-side if a connection somehow outlived it). The delivered art
also settled a spec ambiguity: each quote image bakes in its own "Press
'ESC' to continue", so ESC is the real continue key, not the ENTER
design.md originally sketched before this art existed.

**Real bug, found only by the user actually playing it — a
multi-second, whole-window freeze between dying and the Death Screen
appearing.** First version atlas-packed all 23 quote images the same
way every other art category in this project gets packed
(`AtlasPacker`) — but only one quote is ever drawn per death, so
packing forced ~4 full 2048×2048 pages (~67MB decoded) into GPU memory
synchronously, on the render thread, every single time. The user's own
instinct nailed the root cause before I'd finished diagnosing it:
atlases exist to share one texture bind across many sprites drawn
*together* in the same scene, which never applies here. **General rule
this project already half-knew but hadn't stated outright: atlas-pack
art that gets batched together in one draw call (ship frames, HUD
icons); load-on-demand as plain `Texture`s art where only one of many
variants is ever shown at once** — this project's tileable backgrounds
(`blue_nebula.png`, `menu_starfield.png`) already followed this by
convention, just without it ever being written down as the general
rule, which is exactly why it got missed here on the first pass. Fixed
by dropping `after_death` from `AtlasPacker` entirely and copying the
raw files loose into `assets/textures/after_death/`; `DeathScreen` now
loads only `Dialog_Background.png` and whichever single `Quote_<n>.png`
was dealt, as plain `Texture`s. **Not independently re-verified by a
live automated death this session** (driving a real kill via `SendKeys`
across two clients proved too fiddly to aim reliably, and the user
took over testing directly instead) — the user confirmed the freeze was
real and reproducible before this fix, and is re-testing the fix
themselves.

**Connect Screen background music — implemented 2026-09-06.** See
design.md 4.5 for the full writeup. First real audio in this codebase:
the user's `StarWarsTheme.mp3` plays on `ConnectScreen` via libGDX
`Music` (streamed, not `Sound`'s load-fully-into-memory model), not
looped - if it ends before the player leaves, it just stays stopped, no
special handling needed. Faded out over 1.5s (not cut) when the player
leaves, handled by a new `StarWarsGame.fadeOutAndDisposeMusic` +
`StarWarsGame` overriding the top-level `Game#render()` — necessary
because a fade outlives whichever screen started it (`ConnectScreen` is
already disposed by the time the fade finishes), the same "outlives
individual screen instances" shape `QuoteDeck` already needed
`StarWarsGame` for. **Verified the only way actually possible here: the
user confirmed by ear that it plays** - screenshots/logs can't confirm
audio at all, so this one's live-verification note is genuinely just
"the user listened to it," not a euphemism for something else checked
instead.

**A-Wing added as a 7th ship type — implemented 2026-09-06.** See
design.md 7 (ship roster) for the user's "Ship Tree" idea this rounds
out for later (Rebel branch X-wing → A-Wing → Falcon, mirroring the
Imperial TIE Fighter → TIE Interceptor → Star Destroyer branch,
Snowspeeder as the neutral start) - not implemented, just why this ship
specifically got added now. Mechanically the same playbook as every
earlier ship import: `ShipType.AWING`, `assets/shipdata/awing.stats.json`
(same baseline thrust/torque/hull/shield numbers as every other ship,
per-ship `radiusMeters`/`pixelsPerMeter` derived from its 256px source
art - same size class as the X-wing/TIE Fighter/TIE Interceptor, 4m
real-world diameter), new `case AWING` in both of `Client`/
`ShipSelectionScreen`'s exhaustive switches over `ShipType` (the compiler
catches a forgotten one - flagged directly in `ShipType`'s own class
Javadoc now, since this is the second time this exact pair of switches
needed a synchronized update). **Also already has a real
`assets/shipdata/awing.meta.json`** — an 8-point hitbox polygon, two
wingtip `PROJECTILE` points, `ENGINE`/`DAMAGE_SMOKE`/`LIGHT` points, and
`turretConfig: null` (correctly absent - the A-Wing isn't one of the two
turret-equipped ships) — found already sitting there mid-session, not
authored by this session's own work; presumably the user ran the
dev-tools editor in parallel while this ship was being wired up, the
same kind of concurrent-authoring this project has seen before (CLAUDE.md
history, the TIE Fighter art swap). Confirmed loading and taking effect
correctly (no fallback circle/single-shot needed) via the same live
flight test below - Ashley/Box2D never complained about it, so unlike
the region-lookup bug this one just worked.

**Real bug found via live testing, not review: a `NullPointerException`
crash on spawn, specific to this one ship.** Every hull region lookup
(`Client.show()`) calls `shipsAtlas.findRegion(hullRegionName(type), 20)`
— the hardcoded `20` assumes every hull sprite's source filename ends in
the bank-angle sequence's neutral-frame suffix (e.g. `xwing128_0020.png`),
which TexturePacker parses into an *indexed* region (retrievable only via
the two-arg `findRegion(name, index)` overload) rather than a plain named
one. The user's original `awing.png` had no such suffix, so it packed as
an unindexed region — `findRegion("awing/awing", 20)` then had nothing
matching index 20 to return, came back `null`, and `Client.drawLocalShip`
crashed dereferencing it the instant the ship actually spawned (Ship
Selection itself never touches this lookup, so the portrait/description
panel looked completely fine right up until Start was pressed - only
caught by actually flying it, exactly the kind of gap review alone
wouldn't have found). Fixed by renaming the source file to
`awing_0020.png` (matching every other ship's convention exactly) and
repacking - no Java changes needed once the filename matched the
convention the shared lookup code already assumed.

**HUD hull art arrived mid-session, after the ship was already flying
without it.** User added `HUD_Status_Background_AWing.png` once they
noticed the gap themselves. Copied to `assets/textures/hud/awing_hull.png`
(the exact `<resourceName>_hull.png` name `ShipStatusHud` looks for, so
this ship no longer needs the X-wing-fallback path other ships used
before their own art existed) and its clip pixel range computed the same
way as every other ship's (alpha-channel bounding box, ≥50/255 threshold)
— cross-checked the method against the X-wing's own known-correct,
user-supplied numbers first (178/377 computed vs. 175/377 actual — close
enough to trust for a ship with no independent source of truth).

**General reminder from this session, worth stating plainly: always
check every raw-asset folder a user mentions before assuming something
is missing.** Two separate times this session, art the user had already
provided went unnoticed until it either rendered unexpectedly (the A-Wing
description panel, already fully authored and sitting in
`assets-raw/menu/AWing_Description.png`) or until they pointed it out
directly (the HUD art above) - both were simple `ls`/`find` checks that
should have happened before writing a comment claiming an asset "doesn't
exist yet."

**Verified live, end-to-end:** full `mvn clean test` (82 tests) green
across every module; a real server + client boot flying the A-Wing to
confirm the crash fix, screenshotted mid-flight with the correct hull
sprite rendering; Ship Selection screenshotted showing the real
description panel and portrait. **Gotcha hit again during this
session's testing, worth restating since it bit twice:** a client jar
built against a server that doesn't share the exact same `ShipType`
enum (e.g. testing a freshly-rebuilt client against the user's
still-running, older server process) fails at the Kryo deserialization
boundary the instant a new enum constant crosses the wire — expected,
not a bug, but easy to misdiagnose as one if the two processes' build
provenance isn't tracked. Rebuilding and restarting *both* ends
together is the only fix.

**Real bug fixed 2026-09-06: projectiles didn't inherit their shooter's
velocity.** See design.md 2.4 for the full writeup. User noticed shots
appearing to fly "backwards" — a ship moving faster than the blaster's
50 m/s muzzle speed literally outran its own shots, since
`ProjectileFactory` only ever set a fired projectile's velocity to
`direction × muzzleSpeed`, never adding the firing ship's own current
velocity on top. Wrong for this project's own Newtonian model: a shot
fired from a moving platform should keep that platform's velocity, same
as a bullet fired from a moving plane in reality. Fixed at the one
shared factory method (`ProjectileFactory.createProjectile` now takes
the shooter's velocity and adds it to the muzzle velocity), so both
`WeaponSystem` and `TurretSystem` got the fix from one change. Flagged,
not fixed: `TurretAiming`'s lead solve still assumes the shot's speed
equals bare muzzle speed, not muzzle speed plus the turret's own ship's
velocity — negligible in practice since a turret's own platform is
normally far slower than its shots, noted in `TurretAiming`'s Javadoc
for whenever that stops being true. Verified live: accelerated a ship to
speed with sustained thrust, fired while still moving fast, and
screenshotted mid-flight (holding the fire key so `PrintWindow` had
something to actually catch, since a single instantaneous shot the
first two attempts happened to land between two identical-looking
captures) — the two shots sat clearly, visibly ahead of the ship instead
of hovering near it. Full `mvn clean test` (82 tests) green throughout.

**Live TTF text via `gdx-freetype` — implemented 2026-09-07.** See
design.md 4.4's addendum for the full writeup. User asked whether TTF
fonts are usable in this project at all; answer was yes, via
`gdx-freetype` (official libGDX extension, `FreeTypeFontGenerator`) —
then wired it up same-session. New dependencies: `gdx-freetype` in
`core`'s POM, `gdx-freetype-platform` classifier `natives-desktop` in
`lwjgl3`'s POM (server skipped, it never renders text). The actual "SF
Distant Galaxy.ttf" (already installed locally,
`C:\Users\mario\AppData\Local\Microsoft\Windows\Fonts\`, and already
used for this project's baked art) is now also bundled as a repo asset:
`assets-raw/fonts/sf-distant-galaxy/` (source copy) →
`assets/fonts/sf_distant_galaxy.ttf` (what's actually loaded, no
packing step needed for a `.ttf` unlike PNG atlases). New
`core.render.GameFonts.generateSfDistantGalaxy(sizePx)` wraps
generate/dispose of the `FreeTypeFontGenerator` itself (only needed to
bake the glyph texture, disposed immediately after) — the returned
`BitmapFont` owns that texture from then on and is the caller's
responsibility to dispose, same as any other texture-backed resource.
`ConnectScreen` is the first consumer: generates one 24px font and
assigns it directly to the three custom `Style` copies it already
builds (text fields, error label, Connect button) rather than touching
VisUI's global `default-font` skin entry — scoped to this screen since
it's currently the only one with live (not pre-baked) text.

**Verified live, not just build+tests:** full `mvn clean test` (82
tests, unaffected) green across every module; packaged and ran the real
client jar, screenshotted the Connect screen via the project's usual
`PrintWindow`-based technique — field text ("LOCALHOST", the saved
login, masked password dots) and the "CONNECT" button label render in
the real "SF Distant Galaxy" font now, not VisUI's default; zero
exceptions. The still-baked chrome labels ("SERVER", "DISPLAY NAME",
etc., part of the dialog panel art, untouched by this change) are
visually indistinguishable from the new live text, confirming the live
render actually matches the baked font rather than just resembling it
from a distance.

**Flagged, not resolved: `SF Distant Galaxy.ttf`'s redistribution
license is unknown.** It was fine to *use* locally to bake art before
(nothing left this machine), but the raw font file is now committed to
the repo and would ship inside the game's assets to any other player —
a different, unverified legal question. Revisit before sharing/
distributing this project beyond the current player group; see
design.md 4.4's addendum for the same flag.

**Significant upstream drift discovered pushing the above commit,
2026-09-07 — worth flagging since it wasn't visible from this session's
starting context.** `git push` was rejected (remote had diverged); a
rebase pulled in **ten commits done outside this session**: jgitver
automatic Maven versioning (design.md 3.10 — `<version>` is now `0` in
every POM, the real version is computed at build time; **`core` must be
reinstalled after every new commit lands**, a stale local install can
silently go missing under a version other builds now expect), a
client/server handshake version check (rejects a mismatched pair rather
than misbehaving), tier-weighted kill XP (design.md 2.10), a packaged
Windows client release pipeline (jpackage, design.md 3.11), and a
Docker/QNAP server deployment (design.md 3.12). The rebase itself was
clean (no conflicts — this session's POM edits and the upstream
jgitver/versioning changes touched adjacent, not overlapping, lines).
**Take this as a reminder to `git fetch`/check for upstream drift before
assuming this file's own "where we left off" framing is current** — it
's accurate for what *this* session did, but this project is
apparently also being worked on elsewhere between sessions.

**Scoreboard overlay (design.md 2.11) — implemented 2026-09-07, same
session.** User-provided art (`assets-raw/hud/Scoreboard.png`) plus
exact column X positions/first-row Y; holding TAB on the gameplay
screen shows every connected player's name/XP/session kills/session
deaths. New `ScoreboardMessage`/`PlayerScoreEntry` (broadcast over TCP
every 1s, not per-tick — this data isn't render-critical), new
`GameNetworkServer` session-only `killsByPlayerId`/`deathsByPlayerId`
maps, new `core.render.ScoreboardHud` (the second real consumer of
`GameFonts`/`gdx-freetype` after `ConnectScreen`, at 14px to match the
panel art's baked headers). Full writeup in design.md 2.11.

**Verified live with a real kill, not just a static screenshot:** ran a
real server + two real client processes, held TAB and confirmed both
connected players listed and correctly sorted, then actually landed a
kill and confirmed the killer's row updated (`KILLS` incremented, real
kill-XP awarded per design.md 2.10's formula) in the very next
broadcast, and the victim correctly vanished from the list the instant
their client disconnected to the Death Screen (a death always
disconnects — expected, not a bug). Also directly confirmed the
rendered "0" digit is this font's actual stylized-ring zero glyph, not
a broken/missing-glyph rendering bug — checked by comparing a bare `0`
against the `0` inside a real `30` XP value, both identical.

**Automation gotcha hit getting two distinct logged-in test accounts
for this verification, worth remembering for next time:** sending
literal `Ctrl+A` via `SendKeys` into a focused `VisTextField` does
**not** trigger select-all — it inserts a literal, unhandled control
character (rendered as a tofu/box glyph by the game font), corrupting
the field instead of clearing it. What actually works: `ConnectScreen`'s
own custom Tab-handling already calls `selectAll()` on whichever field
Tab moves focus *to* (see this file's Connect Dialog history) — so
tabbing into a field and immediately typing correctly replaces its
existing content, no explicit select-all needed at all; only the
*first* field (focused directly at `show()`, never reached via Tab) has
no such auto-select, and needs `{END}` then `+{HOME}` (shift+home) to
select-to-start before typing over it. A first attempt at clicking
fields via raw `mouse_event` at guessed screen coordinates also failed
silently (every field's text landed in whatever field already had
focus, i.e. clicking never actually changed Scene2D keyboard focus) —
abandoned in favor of the Tab-based approach above rather than debugged
further, since it worked immediately once tried.

**Embedded dev-only MCP server for remote-controlling the client —
implemented 2026-09-07, same session, right after the above.** User's
own framing: OS-level `SendKeys`/screenshot verification (the note
directly above is one of many examples) was a growing pain as screens
multiplied; asked to explore whether an MCP server embedded in the
client itself could let Claude Code drive it directly instead, then
green-lit a minimal version scoped to just the Connect screen. Full
writeup, architecture, and the real gotchas hit getting it working in
design.md 3.13 — short version: new `core.remote` package
(`RemoteControllable`/`RemoteControlRegistry`/`RemoteControlQueue`),
`ConnectScreen` is the first screen wired up (`remoteLogin` reuses the
existing `attemptConnect()` unchanged), new `lwjgl3.mcp.McpBridge`
(`io.modelcontextprotocol.sdk:mcp-core`+`mcp-json-jackson2`, stdio
transport) exposing `get_active_screen`/`connect_screen_login`, started
only when the client is launched with `--mcp`.

**Process note worth repeating for next time an SDK is this new:**
looked up the exact Maven coordinates/API via `gh api` against the
SDK's own GitHub repo (real source files, `docs/quickstart.md`,
`docs/server.md`) rather than trusting a `WebFetch`-summarized example,
after that summarized example *had* invented a wrong dependency/version
on the first attempt. Paid off directly - the actually-verified code
compiled clean on the first try with zero API-name guessing errors.

**Verified live, fully end-to-end** by speaking raw MCP JSON-RPC
directly to a real running client process's stdin/stdout (no Claude
Code MCP-client wiring involved yet, just manual protocol calls) — full
handshake, tool discovery, and a real `connect_screen_login` call
against a real running dedicated server that actually logged in and
transitioned the client to Ship Selection, confirmed both via the tool
call's own JSON response and a follow-up screenshot of the live window.
See design.md 3.13 for the two real bugs hit getting the test harness
itself right (a UTF-8 BOM PowerShell's `Process.StandardInput` was
prepending, and why a one-shot file-fed stdin can't validate a *slow*
tool call) — both were test-harness issues, not bugs in this code, but
the BOM fix was applied defensively on the Java side too since a real
client could plausibly hit the same thing.

**Registered, project-scoped, committed** — user chose this over
local-only after the above was verified. `.mcp.json` + `start_mcp_client.cmd`
(new, mirrors `start_client.cmd`'s "always reinstall core/repackage
first" convention); see design.md 3.13 for the wrapper script's stdout-
hygiene requirements (everything it and Maven print must go to stderr)
and its one known limitation (a hardcoded absolute path — a relative
one resolved unreliably through this exact `cmd.exe /c` spawn path,
worth re-testing if this project ever moves to a second machine).
Verified by running the *exact* configured command end-to-end, not
just the underlying jar directly. **Requires a Claude Code
restart/reconnect to actually pick up** — registering `.mcp.json`
doesn't retroactively connect an already-running session.

Only `ConnectScreen` is remote-controllable; every other screen still
needs the old keyboard/screenshot approach until this pattern proves
worth extending.

**Kills/deaths moved from session-only to persisted account totals; the
Death Screen gained its own scoreboard — implemented 2026-09-08.** Full
writeup in design.md 2.11/5.1's addenda. User feedback after living
with the scoreboard for a session: kills/deaths reset to zero on every
death, since a combat death disconnects the client and they were
tracked as in-memory state keyed by the ephemeral `playerId`. User
proposed their own fix ("maybe it's just easier to persist them in the
account") - went with it: simpler than any session-scoped alternative
(reused the exact `AccountStore.addXp` pattern already there for XP),
more robust (survives death *and* reconnect *and* even a server
restart), and net *removed* code from `GameNetworkServer` (its two
in-memory maps are gone, `broadcastScoreboard()` now reads
kills/deaths from the account the same way it already read XP).
`PlayerAccount`'s constructor gained two params (`kills`, `deaths`) -
its only other caller, `AccountStore.login`'s account-creation path,
was updated too; old `accounts.json` entries missing the new fields
deserialize them as `0` for free (confirmed live against this
project's own real accounts file, which predates the change).

Second half of the ask: "it would be nice if you could call up the
scoreboard while in the death screen as well" - own stats only, since
that screen has no live server connection (the user correctly
anticipated this as the likely complication themselves).
`Client.goToDeathScreen()` now hands `DeathScreen` a one-shot
`PlayerScoreEntry` snapshot; `GameNetworkServer.handleShipDestroyed`
also broadcasts the scoreboard once immediately (not just its existing
1-second cadence) right before the death notification, both over the
same ordered TCP channel, so that snapshot is guaranteed fresh -
without this the death that just happened wouldn't yet be reflected.

**Verified live, fully end-to-end, with a real kill** (not just the new
`AccountStoreTest.addKill`/`addDeath` cases mirroring the existing
`addXp` ones): two real client processes, a real kill landed, the
killer's live scoreboard immediately showed the update; the victim's
Death Screen, held TAB, showed its own `DEATHS=1` mid-death, fully
disconnected - the exact moment that used to always read zero. Then
sent the victim through ESC → Ship Selection → respawn (a genuinely new
connection/`playerId`, confirmed via the server's connection log) and
confirmed the live scoreboard still showed `DEATHS=1`, not reset -
proving the fix survives a real reconnect, not just the Death Screen's
own one-shot snapshot. Full `mvn clean test` green throughout.

**Real bug found by the user, fixed 2026-09-08: pressing TAB on the
Connect screen typed a literal tab character into whichever field it
just moved focus to.** Only became visible once the live TTF font
landed — "SF Distant Galaxy" happens to render a real (small
rectangle) glyph for the tab character, where VisUI's old default font
apparently didn't, or rendered something inconspicuous enough nobody
had spotted it. Root cause, confirmed by actually reading
`TextField.java`'s real source (`gdx-1.14.2-sources.jar`, not
guessed): `TextField.InputListener.keyTyped`'s `checkFocusTraversal`
only swallows the tab character when `focusTraversal` is `true` -
`ConnectScreen` sets it `false` on every field (see this file's own
earlier Connect Dialog history - needed to fix a *different*,
already-resolved double-focus-jump bug), so with it off, tab silently
falls through to the same "insert this character" branch every other
keystroke goes through - landing in the newly-focused field, since
that stage-level listener's `keyDown` already reassigned focus before
this same keypress's separate `keyTyped` event fires. Fixed by adding
a `TextField.TextFieldFilter` (`c != '\t'`) to every field - narrower
than reverting `focusTraversal`, and doesn't reopen the bug that
setting it false originally fixed. **General rule worth repeating:**
when you need precise details of how a lightly-documented library
method actually behaves, check whether its sources jar is already in
`~/.m2` (`find ~/.m2/repository/... -iname "*sources*"`) before
guessing from Javadoc or memory - it was, here, and reading the real
`keyTyped` implementation directly is what turned a plausible theory
into a confirmed root cause. Verified live: typed into all four fields
via Tab navigation, screenshotted, confirmed no stray glyph at the
start of any field's text anymore. Full `mvn clean test` green
throughout.

**Ship unlocks — implemented 2026-09-08.** See design.md 2.12 (full
writeup) and 5.1's addendum for the details. Now that XP persists and
is visible to the player (2.10/2.11), the user asked to spend it:
every non-Snowspeeder ship costs XP to unlock, by tier — 1000/1500/2000
for tiers 2/3/4, a new `unlockCostXp` field on each ship's
`.stats.json`. Snowspeeder is free/unlocked for everyone from account
creation. Affordability is never a stored/decremented balance — it's
computed on the fly as **available XP = total XP − the summed unlock
cost of every already-unlocked ship**, in a new pure, unit-tested
`core.sim.ShipUnlocks` (`isUnlocked`/`availableXp`) used identically by
the client (which padlock to show) and the server (the actual
authoritative check) — same shared-pure-function convention as
`ShipDamage`/`PowerDistribution`.

User-provided art (`assets-raw/menu/Padlock_Green.png`/`Padlock_White.png`,
packed into the existing `menu.atlas`) is drawn centered over the ship
portrait on Ship Selection: green ("'SPACE' to unlock") when the
locked ship is affordable, white ("not enough XP") when it isn't — both
messages are baked into the art, no extra text needed. `PlayerAccount`
gained a persisted `Set<ShipType> unlockedShips` (mirrors how XP/kills/
deaths already work there) — its first ever *mutable collection* field,
which needed real defensive-copying discipline (constructor, setter,
and `copy()` all copy rather than alias) since `AccountStore`'s
background flush thread snapshots accounts via `copy()` concurrently
with live mutation.

**`ShipSelectionScreen` becomes the second screen (after `ConnectScreen`)
to hold a live server connection** — it previously had none at all,
picking a ship was fully offline until Start. It does its own fresh
handshake (logging in again is harmless, same precedent `Client`
already relies on) to learn XP/unlocked ships, and — unlike
`ConnectScreen`, which disconnects the instant login is validated —
keeps that connection open for as long as the player browses ships, so
pressing SPACE on an affordable locked ship can send a live
`UnlockShipRequest` and get back an `UnlockShipResponse` carrying the
account's updated state. Deliberately **asynchronous**, unlike
`ConnectScreen.attemptConnect()`'s blocking pattern — blocking here
would freeze this screen's first frame for up to the connection
timeout, tolerable for a screen whose whole purpose at that moment *is*
connecting, not for one whose primary purpose is browsing; every locked
ship just shows white until the handshake resolves (self-corrects
within a frame or two on localhost). New protocol pieces:
`HandshakeResponse` gained `xp`/`unlockedShips` fields (any future
screen needing the same account snapshot can now read it from its own
handshake, no new message type needed); new `UnlockShipRequest`/
`UnlockShipResponse` messages, `UnlockShipResponse` always carrying the
account's current xp/unlockedShips regardless of outcome so the client
can just always replace its local copy rather than branching on
success/failure.

**Server never trusts the client's own padlock/Start-button gating** —
`SpawnRequest` handling now re-checks `ShipUnlocks.isUnlocked` against
the requesting player's actual account before spawning (a raw/crafted
spawn packet for a locked ship is silently dropped), and
`UnlockShipRequest` handling re-validates affordability server-side
too, same "don't trust the client" posture every other player action
here already gets.

**Real gap found while unit-testing this: `core`'s own test classpath
never had `assets/shipdata/*.json` on it at all** — only `lwjgl3`'s and
`server`'s *main* resources ever bundled `assets/`, and no earlier
`core` test happened to transitively call `ShipStats.forType(...)` (the
loader) to notice. The new `ShipUnlocksTest` was the first one to,
failing with `IllegalStateException: Missing required
shipdata/xwing.stats.json`. Fixed with a `<testResources>` block in
`core/pom.xml` pointing at `../assets` — test-scoped only, so `core`'s
own packaged jar is unaffected (it never needed to bundle ship data
before, and still doesn't).

**Verified live, fully end-to-end, real server + client restarts
included:** a fresh account at 0 XP showed every non-Snowspeeder ship
white-padlocked; seeded to 1000 XP (direct edit of `data/accounts.json`
+ server restart — a deterministic way to reach a precise XP value
without a real kill), TIE Fighter's padlock turned green; SPACE
unlocked it, padlock gone; immediately re-checked the A-Wing (also
1000, tier 2) and confirmed it now showed white too — available XP
correctly read `1000 − 1000 = 0`, proving the summed-cost subtraction
across ships, not just a single-ship check; Start correctly refused a
still-locked ship and correctly launched a real match flying the
newly-unlocked TIE Fighter; **stopped both the server and client
entirely, restarted both, logged in again, and confirmed the unlock was
still there** — real persistence across a full restart, not just within
one session. Full `mvn clean test` green throughout.

**Ship Tree (branch-order unlock gating) + hover tooltips — implemented
2026-09-08, same session, right after the above.** See design.md 2.13
for the full writeup. Once the user saw the full gameplay-loop status
recap (this file/design.md's §6/§7), they remembered the "Ship Tree"
idea from 2026-09-06 (§7) and asked for it now: unlocking isn't just
XP-gated (2.12) anymore, it's branch-gated too — **Imperial:** TIE
Fighter → TIE Interceptor → Star Destroyer; **Rebel:** A-Wing → X-wing
→ Falcon (Snowspeeder outside both, always unlocked). New pure,
unit-tested `core.sim.ShipTree` (`prerequisiteOf`/`prerequisiteMet`) is
the one place this table lives, used identically by the client (which
padlock to show) and the server (`GameNetworkServer.handleUnlockShipRequest`,
re-validated there same as the XP check already was — never trusting
the client's own padlock choice). A third padlock, user-provided
(`assets-raw/menu/Padlock_White_TierTooHigh.png`), shows whenever the
branch prerequisite isn't met, taking priority over the green/white
affordability padlock even when a ship would otherwise be affordable —
knowing which ship to unlock first is the more fundamental blocker.

**Note on the branch order itself:** the user's actual instruction here
was "A-Wing → X-Wing → Falcon" for the Rebel side — the *reverse* of
§7's original 2026-09-06 sketch (which had X-wing before A-Wing). Went
with the fresh instruction as authoritative and corrected design.md's
§7 accordingly. Turned out not to require any data changes at all:
`ShipType`'s tiers (2.10, set the same day the idea was first floated)
already had A-Wing at tier 2 and X-wing at tier 3 — i.e. already
encoded this exact order — so `ShipTree`'s prerequisite table just
needed to agree with tiers that were already correct.

**Tooltips — new user request, answered same session: "would it be
possible to display a tooltip when the player moves the mouse over the
padlock?"** Yes — new `core.render.Tooltip`, a floating text box with
its own drawn background (a tinted, stretched 1×1-pixel `Texture`
rather than pulling in a `ShapeRenderer` for one rectangle), using
`GameFonts`' live "SF Distant Galaxy" text (third consumer, after
`ConnectScreen`/`ScoreboardHud`). Shows exactly two messages, only for
the two locked-and-blocked cases (never for green/already-unlocked,
both already self-explanatory): "You need to unlock the A-Wing before
you can unlock this." (tree-blocked) or "You're lacking 1000 XP to
unlock this ship." (affordable tree-wise, short on XP). New
`ShipType.getDisplayName()` (e.g. `"TIE Fighter"`) feeds the first
message — this codebase's first-ever need for a ship's name as live
text rather than baked into art.

**Real bug caught before it ever ran, by re-reading the draw call, not
by seeing it happen:** `SpriteBatch.getColor()` returns the batch's own
live, mutable `Color` field, not a snapshot. The first draft of
`Tooltip.render` saved that reference to restore "the previous tint"
after temporarily setting a background color — but since `setColor(...)`
mutates that very object in place, the "saved" reference would have
already become the new color by the time it was used to "restore" the
old one, permanently tinting every draw call after the first tooltip.
Fixed with an explicit `.cpy()` before mutating. **General rule for
later: any libGDX getter that returns a live mutable field (not just
`SpriteBatch.getColor()`) needs an explicit copy before a "save, change,
restore" pattern** — assigning the reference itself doesn't save
anything.

**Automation gotcha hit heavily during this session's live verification,
worth stating plainly since it cost real time: after a failed form
submit, keyboard focus stays wherever it last was — not back at the
first field — so a fixed "N tabs from the start" script only works on a
screen's very first `show()`, not after any retry within the same
running instance.** Blindly re-running the same tab sequence after a
correction typed new text into the *wrong* field more than once here.
**Fix that actually worked: after any correction, take a checkpoint
screenshot and visually confirm every field's content before pressing
Enter again**, rather than trusting the tab-count math to still hold.
Also re-confirmed the existing "always verify `GetForegroundWindow()`
before typing" rule (CLAUDE.md, turret-weapons milestone) the hard way:
one `SendKeys` call fired without re-checking foreground focus first
landed nothing useful, quietly. **Also: killing several `java.exe`
processes by PID after enumerating them with `Get-Process` requires
recording which PID is the server *before* tearing anything down** — a
blind `taskkill` across "all the PIDs from the last listing" once
killed the dedicated server along with the client it was meant to
target (exec:java runs the server in-process, no fork, so it doesn't
get its own obviously-separate PID the way exec:exec's forked client
does), producing a red herring "could not reach 'localhost'" login
error that had nothing to do with the actual game code.

**Verified live, fully end-to-end, with a real server + client and a
real unlock:** a fresh account showed the X-wing tier-too-high
(correctly overriding the white padlock that would otherwise apply)
with a hover tooltip reading "You need to unlock the A-Wing before you
can unlock this."; TIE Fighter (no prerequisite) showed plain white
with "You're lacking 1000 XP to unlock this ship."; seeded to 6000 XP
with TIE Fighter already unlocked, TIE Interceptor showed green,
SPACE unlocked it; **Star Destroyer, previously tier-too-high, switched
to green the instant TIE Interceptor's unlock landed — no restart
needed**, confirming the prerequisite check reads the account's
just-updated state, not a stale one. Full `mvn clean test` green
throughout.

**Shared `AssetManager` + splash screen — implemented 2026-09-08.** See
design.md 3.14 for the full writeup. The user noticed occasional longer
pauses switching screens/scenes while playing and asked directly
whether this codebase had a load-everything-up-front `AssetManager`
the way their past projects did — it didn't: every screen
(`ConnectScreen`/`ShipSelectionScreen`/`Client`/`DeathScreen`)
constructed and disposed its own `Texture`/`TextureAtlas` instances in
`show()`/`dispose()`, reloading `ships.atlas`/`menu.atlas` from scratch
on almost every transition. Auditing it further turned up something
worse than the user had even flagged: `ShipStatusHud`/
`PowerDistributionHud`/`ScoreboardHud` were *also* each constructing
fresh copies of their own textures every time `Client`/`DeathScreen`
was shown — meaning every single combat death, not just a rarer screen
change, reloaded a stack of HUD textures from disk.

Fixed with the exact shape the user described: `StarWarsGame` now owns
one `AssetManager` for the app's whole run; new `SplashScreen` (logo on
a black background, a simple tinted-pixel progress bar, same technique
`Tooltip` already used for its background box) queues everything via
new `render.GameAssets` (the single list of every shared asset path)
and polls `AssetManager.update()`/`getProgress()` until done, then
hands off to `ConnectScreen`. Every consumer screen and HUD widget was
converted to read from `game.getAssets()` instead of loading its own
copies; `ShipStatusHud`'s per-ship-type hull-art fallback (design.md
2.7) was preserved by re-expressing it as `assets.isLoaded(...)`
instead of a `FileHandle.exists()` check. `ScrollingBackground` dropped
`Disposable` entirely (100% of its callers now pass an asset-managed
texture it never owned to begin with after this change);
`ParallaxBackground.Layer` gained an explicit `ownsTexture` flag
instead, since `Client`'s background genuinely mixes an asset-managed
layer (`blue_nebula.png`) with a still-procedurally-generated,
still-per-instance-disposed one (`PlaceholderStarfield`).

**Deliberately left outside the `AssetManager`** (see design.md 3.14 for
the reasoning on each): `GameFonts`-generated `BitmapFont`s (cheap to
regenerate, not worth the extra `FreetypeFontLoader` plumbing for a
cost this small), `PlaceholderStarfield`'s procedural texture (not a
file, still explicitly a stand-in for real art), and
`audio/StarWarsTheme.mp3` (loaded exactly once already, not a
repeated-load concern).

**Verification status, worth flagging plainly this time:** full `mvn
clean install` (all 4 modules, packaging included) and `mvn test`
green — but the user asked this session not to launch the client/server
at all today (working from the office; `ConnectScreen`'s background
music would otherwise play at an inopportune moment), so the splash
screen itself and the actual pause-reduction switching between Ship
Selection/gameplay/Death Screen are **not yet live-verified** — that
needs a real play-test before this milestone is considered fully
confirmed, same as every other one in this project.

**Screen-transition pauses — NOT fixed by the AssetManager, still
open — investigated 2026-09-08, same day.** The user played the build
above and confirmed the splash screen itself works (logo + progress
bar, art unchanged), but the original complaint — long pauses switching
screens — was **still there**, so the root cause is something else
entirely. Their own play-by-play was the key clue: KryoNet
connect/disconnect logs showed the client reconnecting fresh on every
screen transition (Connect → Ship Selection → gameplay → back), each
one stopping the previous screen's own connection right after opening a
new one — a real, existing, and per design.md 3.6 explicitly-intentional
pattern ("reconnecting is harmless"), not new from this session. The
actual multi-minute gaps sat *between* those connect/disconnect log
lines, so the KryoNet-level logs alone couldn't show where the time
actually went.

**Investigation, in order (ruled out two plausible theories with real
evidence before finding the actual one — worth the full trail, since
the wrong-but-plausible theories are exactly what a future session
might reach for again):**
1. Suspected `NetworkClient.stop()`/KryoNet's `Client.stop()` blocking
   on a thread-join. **Ruled out by disassembling the actual fork jar's
   bytecode** (`com.github.crykn:kryonet:2.22.9`, `javap -c` on the
   extracted `.class` files) — `stop()`/`close()` only flip a flag and
   wake the selector, no `Thread.join()` anywhere.
2. Suspected `InetAddress.getByName("localhost")` (confirmed, via the
   same bytecode read, to run **unbounded by `timeoutMillis`** before
   the actual socket connect in this fork's `connect(int, String, int,
   int)` overload — a real, separate, still-worth-fixing-eventually gap,
   just not the active cause here) hanging due to a corporate DNS/VPN
   quirk. **Ruled out empirically** — timed `[System.Net.Dns]::GetHostAddresses("localhost")`
   directly via PowerShell on the user's actual machine, got 37ms then
   0ms on repeat calls.
3. **Actual cause, found by instrumenting and just measuring:** added
   real elapsed-time logging around `NetworkClient.connect()`/`stop()`
   (see below) and re-ran the existing
   `NetworkServerClientIntegrationTest` (plain JUnit, zero game code,
   zero screens) repeatedly. A bare `SocketChannel.connect()`/`close()`
   pair took anywhere from **0ms to 22.8 seconds**, wildly inconsistent
   between runs, on this machine, with nothing else running. That
   single fact fully explains both reported symptoms: the render thread
   calls `connect()`/`stop()` synchronously on every screen transition,
   so a bad roll of this die freezes the whole game for exactly that
   long; and the ~20 seconds of erratic "teleporting" ship movement
   right after is a **secondary, already-understood consequence**, not
   a separate bug — once the render thread unblocks, that one frame's
   `deltaTime` is huge, and `PhysicsSystem`'s existing
   `MAX_STEPS_PER_FRAME` clamp (a deliberate, correct anti-spiral-of-death
   safety net, see the "Important Box2D gotcha" entry above) then takes
   many subsequent frames to drain the backlog — exactly the
   teleport-then-settle pattern described. One consistent pattern
   across every run so far: **the *first* connect/stop pair in a freshly
   started JVM process is always the slow/variable one; a second pair
   moments later in the same process is always near-instant (0-3ms)** —
   suggestive of a one-time per-process check (a security/firewall
   product evaluating a new process's first network activity), though
   this doesn't cleanly match the original game session's own log order
   (there, the *first* connect was the fast one) — flagged as a lead,
   not a confirmed mechanism.
4. **User's hypothesis: the active corporate VPN.** Disabled it and
   re-ran the same test three more times: **no improvement** — 22844ms/
   1ms, 791ms/4011ms, 9290ms/15282ms. If anything, worse than some of
   the earlier VPN-connected runs. VPN ruled out as the (sole) cause.

**New permanent instrumentation added as a result (design.md — network
diagnostics), explicitly requested by the user and worth keeping
regardless of how this resolves:** new `core.net.NetworkLogging`
installs a custom minlog `Log.Logger` giving every KryoNet log line
(both processes) an **absolute** wall-clock timestamp
(`HH:mm:ss.SSS`) instead of minlog's default time-since-process-start
elapsed counter — the user pointed out directly that two independent
processes' relative timers can't be lined up against each other,
which is exactly what made this investigation harder than it needed to
be. Installed once from both `NetworkClient`'s and `NetworkServer`'s
constructors (idempotent). Also added, all flagged inline as temporary
diagnostic code tied to this specific investigation: elapsed-time logs
around `NetworkClient.connect()`/`stop()`; total-time logs around
`Client`/`ShipSelectionScreen`'s `show()`/`dispose()`; a
large-`deltaTime` stall warning in both `Client.render()` and
`GameNetworkServer.tick()` (>0.5s); and an elapsed-time log around
`GameNetworkServer.handleHandshake` (to rule out a slow
`AccountStore.login()` specifically — it wasn't; that class was already
audited as synchronous-in-memory-only, background-flushed, per an
earlier session's own work).

**Status: open, deliberately paused here rather than chased further
today** — the user said not to sink more time into it in this session;
next step is to reproduce (or fail to reproduce) the same
`NetworkServerClientIntegrationTest` timing on the user's home network
once they're off the corporate one, which will finally tell us whether
this is an office-network/corporate-security-software artifact or
something that follows the machine/JVM anywhere. **Pick this up by
re-running exactly that test** (`mvn -pl core -Dtest=NetworkServerClientIntegrationTest test`,
several times — the variability itself is the signal) **and comparing
against the numbers above** before touching any code further. If it
turns out to reproduce at home too, the next real angle is probably an
architectural one: stop reconnecting fresh on every screen transition
(design.md 3.6's "reconnecting is harmless" claim is now known to be
false time-wise on at least this machine) and keep one persistent
connection alive across Connect → Ship Selection → gameplay instead —
not started, flagged only.

**Radar / minimap infrastructure — implemented 2026-09-08, same day,
right after the networking investigation above.** See design.md 2.14
for the full writeup. User's ask: three detection mechanisms (60m
omnidirectional base, 120m ±30° forward cone, a 200m "R"-triggered
active pulse on a 30s cooldown that also makes the pulser visible to
everyone for 5s), all individually enable-able per ship type via
`.stats.json` for a future Ship Tree-gated rollout (§7) — but
explicitly **infrastructure only** this session, no minimap art exists
yet, so nothing renders; the goal was just making sure the client
receives exactly the data a minimap will eventually need.

**This turned out to require a real architecture change, not just new
components:** design.md 3.5's original model had the server broadcast
one shared `WorldSnapshotMessage` to every client. Radar/fog-of-war
only means anything if the server decides, per player, which enemy
ships they currently know about — so `GameNetworkServer.broadcastSnapshot()`
now builds a **personalized** `ShipState[]` per connected/spawned
player (their own ship always, plus whichever enemies their new
`RadarComponent` currently detects) and sends each one individually via
that player's own `Connection.sendUDP(...)`, rather than one
`sendToAllUDP` broadcast. Chose this over any client-side-only
filtering specifically because it's the only version consistent with
this project's standing "never trust the client" rule (every unlock/
spawn/movement input is already re-validated server-side) — a
client-side fog-of-war would be trivially bypassed by just drawing
every ship the client happens to receive. Projectiles are deliberately
**not** filtered this way — still broadcast to everyone unfiltered,
flagged in design.md as a scope boundary, not an oversight (gating
projectile visibility too raises its own separate design questions the
user didn't ask about).

**One simplification, flagged as a default, not confirmed by the
user:** the spec describes the pulse's own detection and its "you're
now visible to everyone" downside as two effects, only the second with
an explicit duration. Implemented as one shared, continuously-
re-evaluated window instead of an instantaneous snapshot (which would
be meaningless at 30Hz anyway) — see design.md 2.14 for the full
reasoning; revisit if the user wants them decoupled.

New: `sim.RadarDetection` (pure, unit-tested — 12 cases including exact
cone-boundary behavior — same "logic-heavy pure function separate from
system wiring" convention as `TurretAiming`/`ShipDamage`, reusing
`TurretAiming.angularDifference` for the cone's bearing check rather
than a third copy of that math); `sim.components.RadarComponent`
(added to *every* ship unconditionally, unlike the optional
`TurretComponent`); `sim.systems.RadarSystem` (server-side, recomputes
every ship's detected-enemy set each tick); `net.messages.RadarPulseRequest`
(empty payload, same shape as `TurretToggleMessage`). `ShipTypeConfig`/
`ShipStats` gained 9 fields; every ship type's `.stats.json` got the
same values (the user's own spec numbers), uniformly — the established
"same baseline for every ship now, differentiate later" convention
this project already applies to thrust/torque/hull/shield.

**Real client-side gap found and fixed while implementing this, not
by playtesting:** `Client.onWorldSnapshot` had never needed to remove a
ship from its `ships` map except on an explicit `PlayerLeftMessage`/
`ShipDestroyedMessage` — every ship used to be in every snapshot
unconditionally, so "stop appearing in snapshots" never used to happen
without one of those. With radar filtering, a ship can now legitimately
disappear from a snapshot just by leaving detection range, with no
disconnect/death event to trigger removal — without a fix, it would
have frozen in its last known position forever instead of vanishing.
Fixed by adding the exact same present-in-this-snapshot-or-remove
pruning already used for `projectiles` (`ships.keySet().removeIf(id ->
!presentShipIds.contains(id))`) — caught by reasoning through the new
data flow while writing it, before ever running it live.

**Verification status:** full `mvn clean install` (all 4 modules) and
`mvn test` green — 101 core tests (up from 88: +12 `RadarDetectionTest`,
+1 `MessageRegistryTest` for `RadarPulseRequest`'s round trip) + 30
server tests, all passing. **Not live-verified** — no minimap art
exists yet to actually look at, and this session's broader
network-launch restriction (see the entry above) was still in effect;
the real test once art exists will be actually flying near another
ship and confirming it appears/disappears on a minimap exactly when
radar should detect/lose it, plus a real "R" pulse revealing a distant
ship and making the pulser visible back.

**Radar HUD art + rendering — implemented 2026-09-08, same day, once
the infrastructure above was in place.** See design.md 2.14's addendum
for the full writeup. The user asked directly whether I could design
the art myself, the same way the combat-lock warning banner got done a
couple of days earlier — tried it, and it worked out, following the
exact same Python/Pillow + "SF Distant Galaxy" font technique rather
than the hull-status/power-distribution widgets' much more elaborate
3D-rendered look (winged side panels, ribbed gimbals), which isn't
realistically fakeable with a generated flat image. Reused the game's
existing green (sampled from `Padlock_Green.png`) as the scope's
accent, rather than inventing a new hue or reusing either of the two
already-meaningful colors (blue-lavender = the 3D-widget style
specifically, amber = reserved for alerts).

**Real design flaw the user caught themselves, before any code was
written — worth remembering the shape of this one:** the first
complete draft (shown for review, as asked) baked fixed-position rings
and a fixed-angle cone directly into the background art. The user
reasoned through the actual consequence unprompted: since each ring
represents a *different* detection mechanism's range, and those ranges
can differ per ship type (once the Ship Tree eventually differentiates
them), a ring at a fixed baked position would end up meaning different
real-world distances for different ships with no visual cue anything
had changed — quietly misleading, not just an aesthetic issue. They
also correctly anticipated the fix themselves (dynamic, linearly-scaled
rings/cone drawn as separate overlays) before asking whether it was
hard to implement. It wasn't — this codebase already had every
technique needed (`Client.drawTurrets`/`drawLocalShip`'s origin-based
rotate+scale `SpriteBatch.draw` overload) already in use elsewhere for
sprites, just never applied to a HUD element before.

**Three more real decisions, made through back-and-forth before writing
the renderer, not decided unilaterally:**
- North-up, fixed scope (only the cone overlay rotates with facing) —
  user's preference, confirmed over the alternative (whole scope
  rotates with the ship, like some arcade space games).
- Each ship type's scope scales to *that ship's own* largest enabled
  range, not one fixed distance shared by every ship — my
  recommendation, given as a real tradeoff (can't visually compare
  absolute distance between two different ships' minimaps) with a
  concrete reason (avoids the scope visibly "jumping" scale every time
  the pulse fires) — user agreed.
- A pulse-revealed contact beyond the observer's own range clamps to
  the scope's edge along its true bearing, rendered as a distinct
  chevron (not a plain dot) so it reads as "direction only" — the
  user's own idea, refined together (I noted the clamp needs no
  awareness of *why* a contact was detected, just a plain distance
  check, since ordinary detection can never itself produce an
  out-of-range contact).

**New `render.RadarScopeMath`** (9 unit tests) factors out the bearing/
clamp/range-fraction geometry from the actual `render.RadarHud`
renderer (untested itself, same as `ShipStatusHud`/`PowerDistributionHud`
— thin `SpriteBatch` wiring, not logic) — same "pure math needs a
GL-context-free home" split as `HudGaugeClip` already established.
`ShipStats` gained `getRadarMaxRangeMeters()`. Widget slots into
`Client`'s existing bottom-left HUD row as a third square, right of
power distribution, same size/gap convention as the first two.

**Verification of the art itself before writing any Java:** rather than
judge each of the 5 new textures (background, ring, cone, blip,
chevron) in isolation against a white/transparent backdrop, wrote a
small throwaway Python script that composited them together exactly the
way the real runtime math would (scaled rings, rotated cone, positioned
blips/chevron) for two scenarios — a ship with all three mechanisms
enabled, and a hypothetical base-only ship — specifically to confirm
the per-ship-max-range scaling concept actually reads well on screen
before committing to it in Java, not just on paper. Both composites
looked right; **general rule worth remembering for future generated-art
sessions: judging small UI/overlay assets against a plain white preview
background is actively misleading for anything with meaningful alpha
(a semi-transparent cone fill looks solid on white, subtle on the
actual dark panel) — always composite onto the real background before
judging.**

**Verification status:** full `mvn clean install` (all 4 modules) and
`mvn test` green — 110 core tests (up from 101: +9 `RadarScopeMathTest`)
+ 30 server tests, all passing. **Not live-verified** — same standing
restriction as everything else this session (user testing personally,
once given the go-ahead); the real test is actually flying near another
ship and confirming the rings/cone/blips look right and scale
correctly, plus a real pulse producing a visible chevron on someone
else's scope.

**First real play-test, same day: two fixes.** See design.md 2.14's
second addendum for the full writeup. (1) User reported the Snowspeeder
showing all three rings/the cone despite "supposed to have only the
base radar" — not a bug, this session's own uniform-baseline-for-now
default was still in effect; **implemented the actual tiered loadout**
the original request described (tier 1 base only, tier 2/3 base+cone,
tier 4 all three), by ship type tier. (2) Widget "too small," moved
from the bottom-left row to its own top-right corner and doubled in
size (220→440px).

**Network investigation update, same day, unprompted by the user's
current ask:** the user is now at home (moved from the office session
that started this investigation), still on the same laptop, and
**confirmed the long screen-transition pauses are still there** —
ruling out the office network/VPN specifically as the (sole) cause,
same conclusion this session's own build output kept independently
reaching anyway (`connect()`/`stop()` still showing multi-second,
wildly variable timings during this very session's `mvn clean install`
runs, e.g. 3066ms/5371ms just now, at home). **Explicitly told not to
investigate further right now** — they'll test on a genuinely different
machine next and report back before this gets picked up again. Next
session: check whether they found anything on the other machine before
assuming the "office security software" theory is fully dead — it could
still be something environment-wide (this same laptop, any network) or
something that always was going to require a different machine to
actually isolate.

**Radar play-tested successfully after the tiering/resize fixes above
— confirmed working by the user.** Two more pieces of feedback for
later, **not started, the user is planning to do the first one
themselves in Photoshop:** (1) `hud_radar_background.png` has a lot of
unused "real estate" around the actual circular scope area, wasting
space now that the widget is 440px; (2) the widget could use a visual
indicator for whether the active pulse is off cooldown —
`ShipState#getRadarPulseCooldownRemaining()` (design.md 2.14) already
carries exactly the data this would need, kept in sync for this
reason even before there was a consumer for it. Both explicitly
deferred, not blocking anything.

**One more `connect()`/`stop()` data point, unprompted, from this same
session's own `mvn clean install` runs at home (not investigated
further, per the user's own "no immediate to-do" on this right now):**
`stop()` took **24795ms** in one run — the worst single number seen
yet, at home, same laptop. Reinforces rather than changes anything in
the entry above; still parked until the other-machine test.

**Non-linear engine-power-to-turn-torque curve — implemented
2026-09-09.** See design.md 2.2's addendum for the full writeup. User
reported light ships (Snowspeeder) turning "ridiculously" fast with
Engines maxed, straight-line speed fine at the same setting — root
cause was `ShipControlSystem` applying the exact same linear power
multiplier to both thrust and torque, with nothing curbing the top end
for a low-inertia ship. Resolved the curve-shape choice via
`AskUserQuestion` (same "foundational gameplay-feel fork" bar as 2.2's
original linear-multiplier decision) with two options shown side by
side with actual worked numbers, not just formulas — **power law
(`torqueMultiplier = enginesMultiplier ^ exponent`) chosen** over a
piecewise-only-above-baseline alternative, specifically because it
always fixes at exactly 1.0 at baseline power regardless of the
exponent (zero re-tuning risk for every ship not touched) while
compressing both extremes symmetrically in log-space — accepted the
tradeoff that a starved-Engines split also gets softer, not just the
maxed-out end, as a direct consequence of the same simple curve.

New pure `sim.TurnResponseCurve` (5 unit tests), a new per-ship-type
`.stats.json` field `engineTurnResponseExponent` (`1.0` = today's exact
linear behavior — every ship keeps this except Snowspeeder, given
`0.5` as an explicitly untuned starting point for the user to feel-test
and adjust), `PlayerControlledComponent` gained a third baked-in-at-spawn
value, `ShipControlSystem` now splits thrust (still plain linear) from
torque (curved) instead of one shared multiplier, and `Client.
predictLocalShip` applies the identical curve client-side (via
`ShipStats` directly, no Ashley component needed there) so local
prediction can't drift from the server for reasons other than input —
same physics-parity rule this project has followed since client-side
prediction was first added.

**Verification status:** full `mvn clean install` (all 4 modules) and
`mvn test` green — 115 core tests (up from 110: +5
`TurnResponseCurveTest`) + 30 server tests.

**Play-tested, same day — the exponent alone wasn't enough, user hand-
tuned further.** `0.5` on its own didn't fully fix the feel; the user
edited `snowspeeder.stats.json` directly afterward, dropping
`thrustForce` 200→150 and `turnTorque` 150→50 (on top of keeping
`engineTurnResponseExponent` at `0.5`) — a real, empirical, by-feel
result, not a formula-derived one. **Same standing rule as every other
hand-tuned number in this project: don't "fix" these toward a
calculated value if they come up again**, they're the user's own
verified-by-flying-it result. Snowspeeder's baseline (even power split)
handling is therefore now genuinely different from every other ship,
not just its response-to-maxed-Engines curve — worth remembering if a
future balancing pass touches it.

**Screen-transition pause — root-caused and fixed, 2026-09-08.** See
design.md 3.15's addendum for the full writeup. The user played on
their home gaming PC (not this dev machine) and sent a real log
showing `[ShipSelectionScreen]`/`[Client] dispose() took Nms` tracking
`[net-client] stop() took Nms` almost exactly, ranging 750ms-9.8s —
directly asked "what takes so long inside dispose(), I'd assume we
don't have much to dispose since the global atlas migration (3.14)."
Correct instinct: it wasn't asset disposal at all (that stayed under
20ms every time, per `show()`'s own timing) — it was `NetworkClient
.stop()`'s call into KryoNet's `Client.stop()`, which (confirmed by
actually reading the fork's real source,
`kryonet-2.22.9-sources.jar`, not guessed) does almost no work of its
own; the delay is in `TcpConnection`/`UdpConnection.close()` calling
straight into `java.nio.channels.SocketChannel`/`DatagramChannel
.close()` — the JDK's/OS's own socket teardown. Reproduced
independently on *this* machine too, with zero game code involved: a
plain `mvn test` run of `NetworkServerClientIntegrationTest` showed a
genuine 5.5s `stop()` (and a 4.5s `connect()`) in between two fully
local, back-to-back test methods — confirming this is real,
environment-level socket-close latency (Windows-specific, likely
antivirus/firewall socket interception), not a bug in this project's
own `dispose()` logic.

**Fix: stop blocking the render thread on it, since nothing actually
needs to wait for it.** New `NetworkClient.stopAsync()` runs `stop()`
on a short-lived daemon thread instead of the caller's own thread —
safe because each screen's `NetworkClient` owns a fully independent
socket on its own ephemeral port (already proven fine for multiple
simultaneous connections), so the *next* screen's connection has no
dependency on the *previous* one's socket having actually finished
closing. `Client.dispose()`/`ShipSelectionScreen.dispose()` (the two
screens this bit, matching the user's log) now call `stopAsync()`;
plain `stop()` is untouched and still used by tests, where actually
waiting for completion matters. `ConnectScreen`'s own `client.stop()`
calls were deliberately left alone — that screen already blocks
synchronously by design while connecting (5.1), so this fix doesn't
change its contract either way, and it wasn't the screen the user
reported the pause on.

**Also found and cleared while verifying this: a stray, already-running
`--mcp` client process** (`java -jar lwjgl3\target\StarWars-*.jar
--mcp`, PID found via `Get-CimInstance Win32_Process -Filter
"Name='java.exe'" | select ProcessId,CommandLine`) was holding the
packaged client jar locked, failing `mvn clean` — same class of gotcha
CLAUDE.md has flagged before (the Ship-Selection-screen milestone's
stray server jar): check full command lines, not just process names,
before concluding nothing's running. Killed it, `mvn clean test` then
passed green (all 4 modules, including the reproduced-live-here 5.5s
`stop()` inside `NetworkServerClientIntegrationTest` — expected/
unrelated to the fix, that test intentionally calls the still-blocking
`stop()`, not `stopAsync()`).

**Not yet re-verified live with a real play session** — this was
diagnosed and fixed from the user's sent log plus reading the actual
KryoNet source, not by launching a client this side. Next time the
user plays, confirm the pause between Ship Selection ↔ gameplay is
actually gone (or at least no longer blocks anything visible) — the
`dispose()` timing logs stay in place specifically to confirm they now
report single-digit milliseconds regardless of how long the
now-backgrounded `stop()` takes underneath.

**Terminal-velocity jitter — root-caused and fixed, 2026-09-08, same
day.** See design.md 3.5's addendum for the full writeup. User report
from live play-testing: holding only "W" (constant thrust, no turning)
eventually hit a stable top speed and got visibly jittery there — the
user's own diagnosis was sharp and correct: "this should be the perfect
scenario for local prediction... which leads me to believe there is
still something wrong with our netcode or the prediction." They were
right. Root cause: `Client.reconcileWithServer` compared the server
snapshot's raw reported position directly against the local body's
live, current-frame prediction — but a snapshot is never "now," it's
already stale by at least one server tick interval plus transit/
queueing time by the time it's applied client-side. That manufactured a
phantom "error" out of pure staleness (`velocity × staleness`), scaling
directly with speed — exactly the reported symptom. Fixed by
extrapolating the snapshot forward by the wall-clock time since the
previous reconciliation, using its own reported velocity, before
computing the error against the body — the same dead-reckoning
`RemoteShip` already uses for every other player's ship
(`mySnapshotElapsedSeconds`).

**Process, worth repeating since it's the actual reason this got
documented correctly instead of prematurely:** the fix built clean on
the first attempt and looked complete from code-reading alone — but a
plausible-sounding steady-state analysis suggested a blended, position-
only correction should converge to a constant offset, not the reported
oscillation, casting doubt on whether staleness was really the (or the
whole) cause. Rather than write this up as a confirmed root cause on
reasoning alone, temporary diagnostic logging was added
(`reconcile:`/`renderDelta:` lines in `Client`, since removed) and the
user was asked to actually re-test with a real localhost A/B comparison
(same setup, pre-fix vs. post-fix) plus a log from the jittery phase —
**exactly the kind of live verification this file has praised
repeatedly elsewhere and should keep insisting on before declaring a
netcode fix confirmed.** The user's first log paste turned out to be a
copy/paste mistake (unrelated `connect()`/`stop()` timing output); the
corrected log is what actually confirmed the fix, not the first
build-and-reason-about-it pass. A clean ×0.8 (`1 - RECONCILE_SOFT_BLEND`)
geometric decay toward **zero** error after a one-off disturbance,
alongside a separate steady-state run holding error under a centimeter
at 93.6 m/s, is the actual evidence the extrapolation amount is
correctly centered, not just plausible.

**Real, flagged-not-fixed follow-up found in the same log data:**
reconciliation is now correct *on average* but not fully robust to
snapshot-delivery timing *noise*, amplified by speed — at ~90 m/s, only
~32ms of unaccounted jitter crosses the 3m hard-snap threshold. Two
concrete contributors identified, neither fixed yet: (1) `Client.
render()`'s `pendingUpdates` drain can process more than one queued
snapshot's reconciliation in a single frame after a stall, and every
call after the first extrapolates by ~0 elapsed time since it was just
reset, self-inflicting an avoidable snap; (2) `PhysicsSystem.getAlpha()`
was documented to return `[0, 1)` but isn't actually clamped there once
`MAX_STEPS_PER_FRAME` caps how much of a large `deltaTime` one call can
drain — real values of 1.44 and 4.25 were observed live during a stall,
meaning the render-side lerp extrapolated several body-lengths past the
current position for that one frame. Javadoc corrected to describe the
real behavior; the underlying clamp itself is still open. Both stalls in
the log showed the same "physics accumulator way behind" signature
already seen once before in this project (the still-open, wildly-
variable `connect()`/`stop()` timing investigation above) — flagged as a
possible shared cause, not confirmed as one.

**Verified live, with real before/after data from the user, not just
reasoning about the code:** two localhost play-test runs, one against
the pre-fix build (confirmed jittery, matching the original report) and
one against the fix (no discernible jitter at any speed over an
extended flight) — a genuine same-machine, same-setup A/B comparison.
Full `mvn clean test` green throughout. **Not yet tested over the real
(non-loopback) internet latency the actual player group uses** — the
localhost result rules out the reconciliation math being wrong, but
real transit latency (unlike localhost's near-zero transit time) could
still interact differently with the two flagged-not-fixed follow-ups
above; worth another look if jitter reappears in an actual multiplayer
session between the UK/Belgium/Norway players.

**`ConnectScreen`'s own `stop()` calls extended to `stopAsync()` too,
2026-09-08, same day — closing a gap explicitly left open by the
earlier screen-transition-pause fix above.** The user hit the exact
same multi-second pause again, this time switching from `ConnectScreen`
to `ShipSelectionScreen` right after a successful login (an 11.8s
`stop()`, logged the same way as before). That entry's own text had
deliberately left `ConnectScreen` alone, reasoning it "already blocks
synchronously by design while connecting" — true for the connect+
handshake wait itself (`attemptConnect()`'s own Javadoc says as much),
but that reasoning doesn't extend to the four `client.stop()` calls
*after* the handshake resolves, on both the success and every failure
path — those are just this now-unneeded connection's teardown, not
part of the documented "blocking connect" contract, and the exact same
"each `NetworkClient` owns an independent ephemeral-port socket"
argument that justified `stopAsync()` everywhere else applies here
unchanged. All four switched to `stopAsync()`, though only the success
path (line 452, the one that actually transitions screens) is the
evidenced fix — the other three (unreachable host, no response,
rejected login) are extended on the same reasoning, not from a separate
report, and stay on `ConnectScreen` where the user can retry
immediately. `responseLatch`/`responseRef` are per-call locals captured
by the connection's `Listener`, so a stale response arriving after a
now-abandoned attempt (while its `stopAsync()` is still winding down in
the background) lands on that old, discarded latch, not a subsequent
attempt's — no cross-call contamination from making these async.
`remoteLogin` (the MCP tool) needed no separate change — it just calls
`attemptConnect()`. Full `mvn clean test` green. **Not yet re-verified
live** — same situation as the original fix, diagnosed from a user-sent
log, not a client launched this side; note that the *original*
`Client`/`ShipSelectionScreen` `stopAsync()` fix above also still has no
live confirmation of its own — this `ConnectScreen` fix is independent
evidence-wise, not a substitute for testing that one.

**Projectiles appearing to spawn behind the ship's attachment points —
root-caused and fixed, 2026-09-08, same day.** See design.md 2.4's
addendum for the full writeup. User report from live play-testing: flying
the Snowspeeder east while holding "W" and SPACE, shots appeared to
originate from behind the ship, worse at speed, correcting back to normal
as the ship slowed to a stop. Root cause: `RemoteProjectile` (client-side
dead reckoning) extrapolated every projectile forward using
`WeaponStats.BLASTER.getProjectileSpeed()` alone — a leftover assumption
from before shots inherited the firing ship's own velocity (the
2026-09-06 fix, design.md 2.4). That earlier fix only ever reached the
server's own simulation; nothing updated how the client extrapolated a
projectile's *rendered* position, so the client was structurally
under-extrapolating by the shooter's own velocity component the whole
time, reset back to the true position every time a fresh snapshot
arrived — a sawtooth error scaling directly with ship speed, exactly the
reported symptom.

**Fix:** `ProjectileState` gained `velocityX`/`velocityY` (the
projectile body's real `Body.getLinearVelocity()`, already correct
server-side), same shape as `ShipState`'s existing velocity fields —
and dropped its `angle` field entirely, since nothing needs a
projectile's *fired* angle once its actual *travel* direction is
available directly (and those two can now genuinely differ, which is
exactly what caused this bug). `Client.drawProjectiles`'s sprite
rotation, which used to just reuse that same angle field, now derives
its rotation from the velocity vector instead — otherwise the visible
fix would have introduced a fresh bug (an oval pointing the wrong way
whenever fired while moving off-axis), caught before it ever ran by
re-reading the draw call rather than by seeing it live.

**A staleness-compensation tweak on top of the velocity fix — same
"seed elapsed time instead of resetting to zero" idea as the terminal-
velocity-jitter fix above — went through three rounds of live testing
before landing on the actual root cause. Worth the full trail, since
the middle two rounds each genuinely looked like the answer at the
time.** With the seed in place: the user confirmed the property that
matters ("the projectiles now spawn consistently from the same
position, regardless of speed or direction of travel") but also
reported the spawn point looked "quite a distance away, maybe 2/3rds of
the ship width." Removed the seed on a theory that turned out wrong
(that seeding breaks specifically on a brand-new projectile's first
render) — that brought back the *original*, smaller symptom: a spawn
point lagging visibly behind the ship, worse with speed. **What
actually settled it: the user sent a Photoshop mockup marking the exact
pixel position where two shots first became visible, lined up against a
background star for precision.** Cross-checked against the
Snowspeeder's real `snowspeeder.meta.json` (`PROJECTILE` at y=59px,
right at the hull's own y=60px front edge — the true spawn point is at
the nose, not floating ahead of it), the marked crosses sat roughly
2.3m past that point — very close to `shipSpeed × one tick interval`.
The seed genuinely was overshooting. **Why the same idea works for ship
reconciliation but not here:** `mySnapshotElapsedSeconds` isn't really
a staleness estimate — it works because it aligns two
*independently-integrating* quantities (the locally-predicted body, and
the server state extrapolated by the same real-world duration) over the
same window, so they converge regardless of the actual duration used.
`RemoteProjectile` has no local integration to align with; every
frame's render position is recomputed from scratch as
`base + velocity × elapsed`, where the *correct* elapsed really is the
data's true age — near-zero on localhost, not a full tick interval.
Same mechanism, two different quantities. **Seed removed for good.**

**A second, genuinely separate bug found and fixed alongside it:**
`extrapolateProjectiles` runs every frame after the snapshot drain,
including the very frame a projectile is created in — that first call
added a full frame's `deltaTime` (covering the interval *before* this
projectile existed) on top of an already-correct spawn position.
`RemoteProjectile` now skips exactly one `extrapolate` call right after
creation (`skipNextExtrapolate`), so its constructor-set position
survives untouched for that first visible frame. This is a real
off-by-one-frame bug, not another staleness estimate — deliberately
scoped to the one-time creation case, not generalized to every
snapshot update the way the reverted seed was.

**Deliberately still not extended to `RemoteShip`** — no one has
reported remote ships rendering wrong, so there's no evidence pulling
either change there.

**Also confirms, not just repeats, an existing flagged note:**
`TurretAiming.computeLeadAngle`'s Javadoc already flagged that its
intercept solve assumes shot speed equals bare muzzle speed as
"negligible since a turret's platform is normally far slower than its
shots" — this bug is direct proof the underlying assumption (shooter
velocity meaningfully changes a shot's true speed) is real, not just
theoretical, since the exact same server-side velocity addition is what
broke here. Still not worth fixing preemptively — the Falcon/Star
Destroyer's turrets just haven't been reported as visibly mis-aiming
yet.

**Wire-compatibility gotcha, same class already flagged twice
elsewhere in this file:** `ProjectileState`'s constructor shape changed
(angle dropped, velocity added). Registration order is unchanged, so
ids still line up, but a client and server built from different commits
will silently disagree on this message's field layout — rebuild and
restart both ends together, same rule as every other wire-shape change
here. Full `mvn clean test` green throughout (including
`MessageRegistryTest`'s updated `ProjectileState` round-trip).
**Live-tested three times this session** — velocity fix confirmed;
seed-overshoot confirmed via the marked screenshot cross-checked
against real attachment-point data.

**Fourth live test, 2026-09-09: `skipNextExtrapolate` confirmed fixed
at rest, but a distinct residual bug surfaced under motion.** At rest,
shots now spawn exactly at the attachment point — that fix holds. But
flying east, shot spawn points drift west (behind the ship) again,
worse with more speed — same shape as the original report, just
smaller. The at-rest test could never have ruled this out:
`gap = shipVelocity × staleness` is zero at rest for *any* staleness
value, not just zero, so "localhost staleness ≈ 0" was never actually
validated, only the old tick-interval-sized overshoot was.

**Root cause, found by re-checking the server's tick ordering against
this exact question — not a `RemoteProjectile` bug at all.**
`GameNetworkServer.tick()`'s ordering (weapons fire after physics
stepping, broadcast reads that same tick's fresh positions) is
confirmed correct — a shot's spawn position is exactly right the tick
it's created, no extra-tick delay server-side. The real mismatch is
structural: the local player's **own ship** renders from client-side
prediction (always "now," zero perceived input lag by design), but a
**shot fired from that ship** only exists once the server has
processed the fire input and broadcast it back — a full input round
trip of latency, small but nonzero even on localhost. So the ship the
player sees is always some distance ahead of where the server's shot
was actually spawned, by `shipVelocity × roundTripLatency`. This isn't
a new bug so much as the exact tradeoff design.md 2.4 already named and
flagged "revisit if it ever feels laggy" back when projectiles were
first built as server-only/never-predicted — it's now visibly laggy.

**User chose to build the real fix rather than live with the residual —
local shot prediction implemented same day.** See design.md 2.4's
addendum for the full writeup. `Client#predictLocalWeapon`/
`#spawnPredictedProjectile` mirror `WeaponSystem#processEntity`'s exact
cooldown/capacitor/attachment-point logic locally, via a client-owned
`WeaponComponent` (a plain state holder, reused directly — no Ashley
entity needed) ticked every frame using the same `myPowerDistribution`
mirror already driving thrust prediction. `WeaponStats` gained a shared
`PROJECTILE_ATTACHMENT_NAME` public constant (was private to
`WeaponSystem`) so client and server can't silently disagree on the
attachment point name — same pattern as `TurretConfig.ATTACHMENT_NAME`.

**The handoff (predicted shot → real, id-tracked shot on confirmation)
needed two real fixes before it could work invisibly — both caught by
advisor review of the code, before ever asking for a live test:**
- **Backward pop, fixed by seeding elapsed time instead of resetting
  it.** A confirmed `ProjectileState` reports the shot's position as of
  the server tick that created it — already stale by round-trip latency
  once received — while the predicted object's current render position
  already reflects that shot's true elapsed flight time. Resetting
  elapsed-since-update to zero on adoption (the normal snapshot-update
  behavior) would snap the shot backward. Fixed by seeding it with the
  *predicted* object's own already-accumulated elapsed time instead, on
  the adoption path only (`RemoteProjectile#updateFromSnapshot`'s new
  5-arg overload) — same "align two independently-integrating estimates
  of one event using the same real-world duration" trick 3.5's
  `reconcileWithServer` already uses, not a latency guess.
- **A meaningless match threshold, fixed by matching spawn-to-spawn.**
  Matching a predicted shot to its confirmation by comparing the
  predicted object's *current* (already-extrapolated) position against
  the confirmed spawn position would make the match distance grow with
  latency × velocity, defeating a fixed threshold. Fixed by giving
  `RemoteProjectile` an immutable `spawnX`/`spawnY` and matching those
  instead — two estimates of the same fire event's spawn point should
  only differ by ordinary prediction drift, never by latency.

An unmatched prediction (dropped UDP input packet, or a hit destroying
the real projectile before it's ever broadcast) expires after 0.5s
(`PREDICTED_PROJECTILE_MAX_UNMATCHED_SECONDS`) rather than lingering as
a ghost — deliberately much shorter than the weapon's own multi-second
projectile lifetime, since an unmatched prediction past a handful of
ticks isn't coming. **`myWeapon`'s local mirror is best-effort, not
guaranteed-in-sync the way `myPowerDistribution` is** — fire input rides
UDP (unreliable), unlike power distribution's TCP; left this way
deliberately, the unmatched-expiry above already degrades gracefully and
the server remains sole authority over whether a shot actually fires.

**Wire-compatibility note:** neither the `ProjectileState` shape change
nor the `WeaponStats`/`WeaponSystem` constant move touches Kryo
registration order, so this build is wire-compatible bit-for-bit with
the previous one — but rebuild and restart both ends together anyway,
since `WeaponStats.BLASTER` now drives real firing decisions on both
ends, not just the server.

**Verification status: build/tests only, NOT live-tested yet.** Full
`mvn clean test` green, both jars build clean — but the entire point of
this feature is whether the handoff is visually invisible, which needs a
real play session to confirm, not a build log. Watch for: a shot popping/
jumping backward right after spawning (elapsed-seeding wrong), or a
stray extra shot flying alongside a confirmed one (spawn-to-spawn
matching failed to find its counterpart). Also worth re-confirming the
already-tested pieces (constant spawn point regardless of speed, no
residual westward drift) still hold with prediction layered on top.

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
- **jgitver (2026-09-07, design.md 3.10) computes `${project.version}`
  from git tags/history, as a Maven core extension (`.mvn/extensions.xml`)
  — every pom.xml's version is now the placeholder `0`, never the real
  one.** This interacts with the "install core first" workflow above: the
  installed `core` artifact's version is whatever jgitver computed *at
  that moment*, so a commit made afterwards (even to an unrelated module)
  changes the version the next build expects, and the old one silently
  isn't in `~/.m2` anymore under the new expected version. Re-run `mvn
  install -pl core -am -DskipTests` after every new commit, not just once
  per session — a `-am`-less exec step failing to resolve `core` is the
  symptom.
- **Maven silently drops one side of a duplicate same-`groupId:artifactId`
  `<plugin>` declaration within one `<plugins>` list** (2026-09-07,
  design.md 3.11) — it's only a build *warning* ("must be unique but
  found duplicate declaration of plugin..."), not an error, so it's easy
  to miss. Two separate `maven-resources-plugin` blocks for two unrelated
  `copy-resources` steps in `lwjgl3/pom.xml`'s `release-client` profile
  looked reasonable but only one of them ever actually ran (its
  `<executions>` silently discarded), which then made the *other* step
  look like it had failed for an unrelated reason (its expected input
  directory just never got created). Fix: merge same-GA plugins into one
  `<plugin>` block with multiple `<execution>`s, one per phase, rather
  than declaring the same plugin more than once in one list.
- **`jpackage`'s `app-image` output refuses to run if its destination app
  folder already exists** (2026-09-07, design.md 3.11) — this bit the
  same `release-client` profile from a different angle: a same-phase
  `copy-resources` execution declared *before* jpackage in the pom
  created that folder as a side effect of copying a file into it, so
  jpackage then refused to write there. General lesson for any multi-step
  packaging pipeline like this one: don't rely on same-phase
  plugin-declaration order when steps have real ordering dependencies —
  bind each step to its own distinct standard-lifecycle phase instead
  (this profile uses `pre-integration-test` → `integration-test` →
  `post-integration-test` → `verify`, all real phases in the default
  lifecycle with no default bindings of their own to collide with).

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
- **Standing permission to extend the embedded MCP interface (design.md
  3.13) whenever a better way of testing something in the game would
  help** — the user granted this outright (2026-09-07, right after the
  Connect-screen minimal version was verified) rather than wanting to
  be asked each time. Covers adding new tools, extending
  `RemoteControllable` to more screens (Ship Selection, gameplay, Death
  Screen), or richer `describeState()`/action surfaces — the same kind
  of judgment call already applied to `ConnectScreen`. Keep following
  the same conventions already established there (dev-only, `--mcp`-
  gated, stdout carries nothing but JSON-RPC, actions funnel through
  `RemoteControlQueue` onto the render thread) rather than inventing a
  new pattern per screen. Still worth a brief mention of *why* when it
  comes up, same as any other unprompted design choice — this isn't a
  license to go silent about it, just to not stall on permission first.
