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

**Implemented 2026-09-06.** New `CombatTimerComponent` (Ashley,
server-side) tracks how long it's been since a ship last fired
(`WeaponSystem` marks it, alongside consuming capacitor charge, 2.8) or
was last hit (`GameNetworkServer.resolvePendingHits` marks it, alongside
applying `ShipDamage`); a new `CombatTimerSystem` ticks both every server
tick, same shape as `ShieldRegenSystem`. `CombatTimerComponent.isInCombat
(thresholdSeconds)` is the pure "OR" check the 20-second rule above
describes, unit-tested directly (`CombatTimerComponentTest`) — per
CLAUDE.md's testing conventions, which called this out by name as a
candidate once it existed.

**Protocol:** two new payload-free messages, `LeaveMatchRequest` (client →
server, TCP) and `LeaveMatchDeniedMessage` (server → the requester only,
TCP) — both reliable/ordered, since either being dropped would be a
confusing silent no-op for the player, unlike per-tick UDP input.
`GameNetworkServer.handleLeaveMatchRequest` applies the 20s rule and either
denies (sends `LeaveMatchDeniedMessage`) or grants — reusing
`ShipDestroyedMessage`, the *same* message a combat death sends, for the
"visually indistinguishable" requirement above; there's no VFX for either
death path yet (a ship just disappears from the next snapshot either way),
so today that requirement holds trivially, not because of any special
effort — worth revisiting once a real explosion effect exists. **Granted
leaves deliberately skip the respawn timer** a combat death schedules —
`destroyShipEntity` was factored out of `handleShipDestroyed` specifically
so the new `selfDestructShip` could share the teardown without the respawn
side effect. No kill credit/XP either way, since no such system exists yet
to award it through.

**Client side:** reusing the exact same `ShipDestroyedMessage` for both
paths means the client has to tell them apart itself — `Client` sets
`leavingMatch` when it sends the request and clears it if
`LeaveMatchDeniedMessage` arrives; `onShipDestroyed` checks that flag for
its own player id to decide "combat death, wait for the automatic respawn"
(unchanged, no Death Screen yet) vs. "my leave was granted, switch back to
Ship Selection" (new `returnToShipSelection()`). That method disposes the
gameplay screen right after switching — same pattern, and the same
disposed-resources-crash risk, as `ShipSelectionScreen.startMatch()`
(3.5-ish) — hence `render()`'s `transitionedAway` guard, checked
immediately after draining `pendingUpdates`, before touching `batch`.
`Client` also needed a `Game` reference for the first time (to call
`setScreen`), so its constructor gained one, threaded through from
`ShipSelectionScreen.startMatch()`.

**Real banner art, replacing the `BitmapFont` placeholder, added
2026-09-06 (same day).** The warning was initially plain programmatic
text — flagged as a placeholder since every other bit of UI text in this
codebase is pre-rendered art — and got real art the same day: an
AI-generated (Python/Pillow, not the Design Components tool — see below)
holographic alert plate, `assets-raw/hud/HUD_Warning_EjectionLocked.png` /
`assets/textures/hud/hud_warning_ejection_locked.png`, standalone
`Texture` like the other HUD chrome (not atlas-packed). Reads "EJECTION
LOCKED / Combat systems engaged" rather than the full sentence — the
user explicitly OK'd shortening it as long as the intent lands. Styled
to match the hull/power-distribution HUD's material language (dark glass
panel, glow, thin metal border) but shifted to warm red/amber for an
alert instead of a status readout, and set in **SF Distant Galaxy** —
the same font as "SELECT YOUR SHIP!" and the logo — after the user
pointed out it was already installed and used elsewhere in the game's
art; the first draft had used a generic condensed sans and read
noticeably off-brand by comparison. `Client` draws it at a fixed
`WARNING_BANNER_WIDTH` (720px, height from the art's aspect ratio),
horizontally centered, anchored `WARNING_BANNER_TOP_MARGIN` (48px) below
the top of the screen — **deliberately away from screen-center**, at the
user's request: the player's own ship sits near screen-center via
camera-follow, and this warning fires precisely during tense
combat-adjacent moments, so it must never sit on top of the ship it's
warning about. Confirmed live, same server+client/SendKeys setup as the
rest of 2.3's verification: centered, clear of the ship, no exceptions.
**The "deliberately annoying sound effect" still isn't implemented** —
this remains the project's first-ever audio feature and no sound asset
exists yet; same "still awaiting from the user" status as the earlier
star-dot background asset. The banner alone is fully functional without
it.

**Aside — the `/design` skill (Claude Design's canvas editor) doesn't
work in this environment**: it requires Node.js or Bun to assemble its
canvas payload, and neither is installed on this machine. Tried once,
failed cleanly (no workaround attempted, per the skill's own
instructions), and the asset above was produced directly instead
(Python/Pillow, matched to the existing HUD art by eye/color-sampling).
Worth remembering if `/design` comes up again for this project — it
needs Node or Bun installed first.

**Verified for real:** full `mvn clean verify` green across every module;
a real server+client boot; and, via genuine held-key `keybd_event`
presses (plain `SendKeys` taps are too short to register as
`isKeyPressed` on any render frame, learned the hard way mid-session — a
first ESC-after-firing attempt via `SendKeys` was wrongly granted because
the preceding `SendKeys`-tapped SPACE never actually registered as a
fire): a fresh spawn's ESC is granted instantly, back to Ship Selection,
and the underlying connection actually closes and a later Start reconnects
cleanly with a fresh player id; firing a real shot and then pressing ESC
is denied, shows the red warning banner, and leaves the ship fully
flyable. **Not verified:** the 20-second window actually elapsing and
re-permitting a leave (would need a real 20s wait, not exercised this
session — trusted to the unit tests plus the identical code path already
proven for the "denied" case).

### 2.4 Weapons & combat (first pass, 2026-09-05)

**Weapon:** one type for v1, a simple blaster cannon (`WeaponStats.BLASTER`)
— fires a projectile in the direction the ship is currently facing while
the fire input (SPACE, 5.3) is held, limited by a fixed mechanical
cooldown between shots (0.25s, i.e. 4 shots/sec hard cap) **and** the
real weapon capacitor mechanic from 2.2, implemented alongside power
distribution itself — see 2.8 for the full writeup.

**Projectiles — server-simulated, never predicted:** each shot is a small,
fast Box2D body (bullet/CCD enabled to avoid tunneling through a ship in
one physics step) simulated authoritatively on the server exactly like
ships are, broadcast every tick via `WorldSnapshotMessage` alongside ship
states. Unlike ships, **projectiles are never predicted locally, not even
the shooter's own** — they're drawn purely from received snapshots
(eased toward the latest target, same technique as other players' ships),
accepting a small, barely-noticeable network-round-trip delay before a
shot visually appears. Deliberate simplification, consistent with how
ship movement itself started as snapshot-only before prediction was added
— revisit only if it ever feels laggy in practice.
- **Visual distinction — decided:** the local player's own shots draw
  red, every other player's shots draw blue. Purely a client-side
  rendering choice — the server treats every projectile identically
  regardless of owner. **Art updated 2026-09-06:** the original
  `red_dot.png`/`blue_dot.png` (7×7, circular) were replaced with
  `red_oval.png`/`blue_oval.png` (7×10, elongated) — user feedback that
  a plain dot was hard to see and didn't read as "moving." An oval
  authored nose-up (long axis vertical in the source image, same
  authoring convention as ship sprites) needs no new angle math: the
  renderer already rotated the sprite to `projectile.angle` (the
  travel direction, broadcast every snapshot, constant for a
  projectile's whole flight since it doesn't steer) — that rotation was
  simply invisible on a circle. The only actual code change was drawing
  a non-square region: width stays tied to the physical Box2D hit-
  diameter (`WeaponStats.BLASTER`'s radius), height is derived from the
  region's own pixel aspect ratio, so the art controls how elongated it
  looks with no second tuning constant to keep in sync — same
  visual-size-vs-physical-hitbox split already used for ship polygon
  hitboxes vs. sprites. Verified live: fired while the ship was rotated
  off-axis and confirmed (via a zoomed screenshot) the ovals point along
  the actual diagonal travel direction, not just "up."
- **No destroyed-notification** for projectiles — unlike ships (which get
  an explicit `ShipDestroyedMessage`), a projectile going away is only
  ever inferred by its id no longer appearing in the next snapshot
  (whether it hit something or simply expired). Simpler than adding a
  dedicated per-projectile lifecycle message for something this
  short-lived and high-churn.

**Hit detection — server-authoritative, via Box2D contacts:** a Box2D
`ContactListener` on the authoritative world detects projectile-vs-ship
contacts (projectiles are filtered to only collide with ships, not each
other — see `CollisionCategories`). A hit is ignored if the projectile's
owner is the ship it hit (no self-damage from your own shot — relevant
mainly because a shot spawns essentially at the shooter's own position).
Box2D forbids creating/destroying bodies from inside a contact callback,
so hits are collected into a pending list during the callback and
resolved right after that tick's physics stepping finishes, before the
snapshot broadcast.

**Health & death:** ships have a fixed max health (`ShipStats.XWING`,
currently 100) and take a fixed 10 damage per hit — both placeholder
numbers, not derived from any balancing pass, tune by feel later same as
thrust/torque. At zero health: the ship is removed from the simulation,
`ShipDestroyedMessage` is broadcast (so every client stops
rendering/controlling it immediately), and after a fixed 3-second
respawn delay the server spawns a fresh, full-health ship for that player
and sends them a `ShipSpawnedMessage` (the same message used for the
initial join — a respawn is handled identically client-side: (re)create
the local prediction body at the given position). **No kill credit/XP
yet** — that system doesn't exist (design.md 3.6/accounts aren't built),
so a kill currently has no recorded consequence beyond the target
respawning.

**Known simplification worth flagging:** both the initial spawn and every
respawn currently use the same single fixed point (0,0) — meaning two
players can spawn/respawn exactly overlapping each other, which now
matters more than it used to since a ship sitting there can be shot
immediately. Not fixed now — proper spawn point handling (multiple
points, spawn invulnerability, etc.) belongs to the still-open "map/arena
design" question (§7), not this milestone.

**Bug found and fixed (2026-09-05): self-collision at spawn was
redirecting shots.** Reported symptom: while continuously turning and
firing, shots tracked the ship's true facing correctly for the first
180°, then appeared to fly in the *opposite* rotational sense past that,
meeting up again after a full 360°. Root cause: a projectile spawned at
its shooter's exact position (the ship's center) — perfectly overlapping
that ship's own collision circle. The self-hit check only skipped
*damage* for that pair, not the *physical* collision — Box2D still tried
to resolve the overlap, and with zero separation between two exactly
coincident circles, the push-apart direction is undefined and Box2D
falls back to a fixed axis unrelated to the ship's actual facing,
silently redirecting the freshly-spawned projectile's velocity. Fixed
two ways: a `ContactFilter` on the world now stops a projectile from
ever colliding with its own shooter at all (the actual fix — no
overlap-resolution collision means nothing to redirect it), and
`WeaponSystem` now also spawns projectiles just ahead of the ship's hull
rather than at its exact center (belt-and-suspenders, and just more
correct — a shot should originate from the nose, not the center of
mass).

**Bug found and fixed (2026-09-06): a projectile didn't inherit its
shooter's velocity, so a fast enough ship could outrun its own shots.**
Reported symptom: the user saw a shot appear to fly "backwards" — the
ship, moving faster in roughly the same direction than the blaster's own
50 m/s muzzle speed, simply overtook it. `ProjectileFactory` set a fired
projectile's velocity to *only* `direction × muzzleSpeed`, with no
contribution from the firing ship's own current velocity — physically
wrong for this project's own Newtonian model (2.1): a shot fired from a
moving platform should keep that platform's velocity, the same way a
bullet fired from a moving plane does in reality. Fixed by having
`ProjectileFactory.createProjectile` add the shooter's velocity (read
from its Box2D body at the moment of firing) on top of the muzzle
velocity, for both `WeaponSystem` and `TurretSystem` — the same fix
point serves both, since they share one factory method. **Known
follow-on simplification, not fixed:** `TurretAiming.computeLeadAngle`'s
intercept solve still assumes the shot's speed *is* the muzzle speed,
not muzzle speed plus the turret's own ship's velocity — the turret's
own platform is normally moving far slower than its shots, so the
resulting aim error is usually negligible; revisit only if a fast-moving
turret platform ever makes it visible (see `TurretAiming`'s own Javadoc
for the full note).

### 2.5 Ship sprite metadata: polygon hitboxes & attachment points (2026-09-05)

Ships previously used a plain circle (`ShipStats.getRadiusMeters()`) as
their Box2D collision shape, and projectiles always spawned from a single
fixed offset along the ship's facing direction. Both were flagged as
placeholders; this milestone replaces them with data authored per-ship, so
hitboxes can fit each sprite's silhouette (mattering most for ship-vs-ship
collisions) and future visual effects (engine glow, blinking lights,
damage smoke) have precise, named attachment points to spawn from, not
guessed-at coordinates in code.

**Authoring tool: `dev-tools`' sprite metadata editor
(`SpriteMetadataEditor`/`SpriteCanvas`).** A small Swing app, not shipped
in the game jar (3.2) — load a ship's PNG, view it at 2x zoom (sprites are
small enough that precise pixel placement is hard at 1x), left-click to
add a hitbox polygon point, right-click to remove the last one, ENTER to
place a named attachment point at the mouse position (prompts for a name
via an editable combo box pre-populated with the suggested names below),
DELETE/BACKSPACE to remove the attachment point nearest the mouse. Saves
alongside the image as `<imagename>.meta.json` (auto-detected/loaded on
next open of the same image).

**Data model (`de.mkoehler.starwars.sim.metadata`, in `core` so both the
editor and the game share it):**
- `PixelPoint` — a single `(x, y)`. **Convention: sprite-local, origin at
  the image center, Y-up** (matching the game world's convention, not
  AWT/Swing's Y-down image-pixel-space) — chosen specifically so these
  coordinates can be used directly as Box2D body-local coordinates with no
  further axis flip, once divided by `PhysicsConstants.PIXELS_PER_METER`.
  The editor's `SpriteCoordinates` utility does the image-pixel ↔
  sprite-local conversion (unit-tested — see below, this exact class of
  axis-convention bug has bitten this project before, e.g. the parallax
  scroll direction).
- `ShipSpriteMetadata` — `hitboxPolygon: List<PixelPoint>` and
  `attachmentPoints: Map<String, List<PixelPoint>>` (a **list** per name,
  not a single point — deliberately supports e.g. an X-wing's four
  cannons all being named `PROJECTILE`). Plain Jackson bean (public
  no-arg constructor + getters/setters, per 3.9's convention for
  JSON-serialized classes, as opposed to the immutable-final-field style
  used for Kryo network messages).
- **Naming convention for attachment points:** `PROJECTILE` (weapon spawn
  point(s), consumed by `WeaponSystem`), `ENGINE`, `LIGHT`,
  `DAMAGE_SMOKE` — the latter three not consumed by any system yet
  (effects don't exist), reserved names for when they are.
- `ShipSpriteMetadataLoader` — reads/writes the JSON. Two read paths:
  `loadFromFile` (plain file, used by the editor) and `loadFromClasspath`
  (used by the game at runtime, returns `Optional.empty()` — not an
  error — for a ship with no metadata authored yet, so every consumer has
  an explicit, safe fallback). Deliberately not built on `Gdx.files`, so
  it works identically for the editor (no libGDX context at all), the
  windowed client, and the headless server.

**Runtime asset convention:** `assets/shipdata/<shipname>.meta.json` — a
single canonical file with no separate `assets-raw` copy, unlike textures,
since there's no transformation/packing step between what the editor
writes and what the game reads. `ShipStats` now loads its ship's file via
`ShipSpriteMetadataLoader.loadFromClasspath` in its constructor, exposing
it as `Optional<ShipSpriteMetadata> getSpriteMetadata()`. **`server`'s POM
gained a `<resources>` block bundling `assets/` onto its classpath**
(mirroring `lwjgl3`'s, 3.2) — it didn't need `assets/` before this, since
server-side code only used numeric stats, never sprite pixel data.

**Consumers, both server-side, both falling back to the pre-existing
behavior when no `.meta.json` exists for a ship (so ships without
authored metadata — i.e. all of them, as of this writing, since no
`.meta.json` has actually been authored yet — keep working exactly as
before):**
- `ShipFactory.createBody` builds a Box2D `PolygonShape` from
  `hitboxPolygon` (pixel points ÷ `PIXELS_PER_METER`) when at least 3
  points are present, else the original `CircleShape`. Box2D's
  `PolygonShape#set(Vector2[])` computes the convex hull of the given
  points itself and caps at 8 vertices — the editor enforces that same
  8-point cap while authoring, so a saved polygon is always valid. No
  concave hitboxes in v1; revisit only if a ship's silhouette really
  needs one (a Box2D concave shape has to be built from multiple fixture
  polygons, notably more complex).
- `WeaponSystem` fires one projectile per `PROJECTILE`-named attachment
  point (rotating each by the ship's current facing) when present, else
  the original single fixed-offset-from-center spawn.

**Authored (2026-09-05):** `assets/shipdata/xwing.meta.json` — the
X-wing's real hitbox (7-point convex polygon) and all four attachment
point types (two `PROJECTILE` points, one per wingtip cannon; two
`ENGINE`; two `LIGHT`; one `DAMAGE_SMOKE`), made with the editor.
**Editor feedback applied:** the sprite was still too small to place
points precisely at the original 2x zoom, so `SpriteCanvas.ZOOM` is now
4x.

**Bug found via play-testing the authored X-wing metadata, fixed same
day: projectiles visually spawned translated forward of their
attachment point.** Reported symptom: shots were aligned with the
`PROJECTILE` attachment points (correct direction) but appeared to
originate from a point noticeably ahead of them, not at them. Root
cause: `GameNetworkServer.tick()` called `weaponSystem.update(...)`
*before* `physicsSystem.update(...)`. A tick's `deltaTime` (~1/30s at
the 30Hz server rate) needs roughly two 1/60s physics steps to catch up,
and Box2D steps *every* body in the world each step — including one a
system created moments earlier in the very same tick. So a freshly-
spawned projectile was already swept forward by up to ~1/30s of travel
(≈1.7m/≈53px for the 50m/s blaster) before its position was ever
broadcast in that tick's `WorldSnapshotMessage` — visually identical to
"spawned too far forward," in a straight line along the correct facing,
which is exactly what made it look like a spawn-offset bug rather than
a movement-before-first-render one. **Fixed by reordering:** weapons
now fire *after* physics stepping each tick, so a projectile's first
broadcast position is its true, untouched spawn point; it only starts
advancing from the next tick onward. General shape of bug: the same
"moved before its first render" root cause as the earlier rendering-lag
hitbox-size misdiagnosis (2.4's "Second real bug"), but this time on the
server's own tick ordering rather than client-side interpolation.

**Testing:** `SpriteCoordinatesTest` (4 tests, `dev-tools`) verifies the
image-pixel ↔ sprite-local conversion, including a full round-trip grid.
`ShipSpriteMetadataLoaderTest` (2 tests, `core`) verifies a
save/load round trip preserves multiple points under the same attachment
name, and that a missing classpath resource comes back empty rather than
throwing. The Swing UI/mouse/keyboard wiring itself is not unit-tested
(3.9's convention: test logic-heavy code, not thin UI glue) — verified
instead by launching the editor and the actual game (server + client)
with no `.meta.json` present, confirming zero exceptions either way.

### 2.6 Shield & hull damage model, ship type config, and the status HUD (2026-09-05)

Ships previously had a single `HealthComponent` pool taking full damage per
hit. This milestone adds a regenerating **shield** in front of a
non-regenerating **hull** — the pairing the power-distribution section
(2.2) already anticipated ("Shields — more power = faster shield
regeneration") — plus a corner HUD widget showing both, using art the user
provided (`assets-raw/hud/`: a holographic panel background, a circular
shield-ring overlay, and a green-to-red hull-silhouette gradient overlay,
all sharing one 512x512 canvas so they align with no offset math).

**Damage split — as specified, implemented in `ShipDamage.apply(...)`:**
the shield absorbs a share of each hit equal to its *current fraction of
capacity* — 100% shield takes the full hit, 90% shield takes 90% (10%
bleeds to hull), 0% shield takes none (hull takes it all). One case the
spec didn't explicitly cover: if the shield's designated share exceeds
what's actually left of it (only possible once nearly depleted — damage
has to exceed the shield's *max* capacity for this to trigger at all,
regardless of current fraction), the excess isn't absorbed for free — it
bleeds through to the hull too, so a hit is never partially "lost." Pure
function, unit-tested (`ShipDamageTest`, 5 cases including the exact 90/10
example above and the overflow case) — a good instance of this project's
"logic-heavy code gets tests" convention (3.9/CLAUDE.md).

**Shield regen — flat per-second rate, scaled by power allocation.**
`ShieldComponent.regenerate(...)` adds a fixed points/second rate every
tick via `ShieldRegenSystem`, run after that tick's hits are resolved.
Originally a flat rate standing in for the eventual power-distribution-
driven rate; now that power distribution is implemented (2.8),
`ShieldRegenSystem` scales it by the ship's current Shields power
multiplier every tick. **No regen-delay-after-hit** (a common shooter
convention — shields pause regenerating for a few seconds after taking
damage) — deliberately not built until it's asked for; shields currently
start regenerating again the very next tick after a hit.

**`ShipType` enum, introduced ahead of a second ship type actually
existing.** A single `XWING` value so far, but every ship-type-keyed
resource now hangs off it by convention: `shipdata/<resourceName>.stats.json`
(balance numbers, see below), `shipdata/<resourceName>.meta.json`
(hitbox/attachment points, 2.5, unchanged), and
`textures/hud/<resourceName>_hull.png` (HUD hull silhouette art). The
background panel and shield ring are generic HUD chrome, shared by every
ship type — only the hull silhouette is ship-specific art.

**`ShipStats` now loads a required `ShipTypeConfig` per ship type**
(`shipdata/<name>.stats.json`, throws if missing — unlike the optional
sprite metadata, these numbers are load-bearing for basic simulation, not
a nice-to-have) alongside the existing optional sprite metadata. Radius/
thrust/torque/hull-max moved out of hardcoded Java constants into this
JSON with their exact existing values preserved (200N/150 N·m/2m/100 —
**still don't recalculate these toward a formula, they're tuned by
feel**) — plus four new numbers: shield max capacity, shield recharge
rate (both untuned placeholders, 100 and 5/sec, pending a real balancing
pass), and the HUD clip pixel range for each of the shield and hull
overlays (see below). `ShipSpriteMetadataLoader`'s classpath-loading logic
was extracted into a small generic `JsonResourceLoader` so `ShipTypeConfig`
doesn't duplicate it.

**HUD clipping — a bottom-anchored "fuel gauge."** Each overlay image's
*actually-visible* content only occupies part of the shared 512px canvas
(the rest is transparent padding kept for alignment) — the shield ring
between pixel rows 130–437, the X-wing hull silhouette between 175–377,
both counted from the top (`ShipTypeConfig`'s `hud*ClipTopPixel`/
`hud*ClipBottomPixel`, differing per ship type since each ship's art
occupies a different vertical extent). At 100% the full range renders; at
a lower fraction, only the *bottom* portion of that range renders — the
revealed slice's top edge moves down toward the fixed bottom edge as the
fraction drops, like a fuel gauge draining from the top. The actual pixel
math (which rows to reveal, where the resulting slice lands on screen) is
pulled into a pure `HudGaugeClip.compute(...)` specifically so it's
unit-testable without a GL context (a `Texture` can't be constructed
outside a running libGDX app, unlike this class's plain-float inputs) —
same split as `SpriteCoordinates` in the dev-tools editor.
`HudGaugeClipTest` (6 cases) checks full/zero/half fractions, that every
fraction's revealed slice shares the same bottom screen edge, and
fraction clamping. `ShipStatusHud` (in `core.render`, alongside
`ParallaxBackground`) does the actual texture loading/drawing on top of
that math — background panel, then shield ring, then hull silhouette, all
at the same position/size.

**Client rendering:** a second, screen-space `OrthographicCamera`
(`hudCamera`, un-zoomed/un-panned, updated on resize) is used for a
second `batch.begin()`/`end()` pass after the world-space one, rather
than swapping `SpriteBatch`'s projection matrix mid-batch — safer than
relying on an implicit flush. The widget sits at a fixed bottom-left
screen position (220px, 24px margin — untuned placeholders), showing only
the **local player's own** hull/shield (broadcast for every ship in
`ShipState`, so a future enemy-health readout needs no protocol change,
but only the local player's is rendered today). Defaults to full
hull/shield immediately on spawn/respawn (before the first
`WorldSnapshotMessage` arrives) so the widget doesn't flash empty for a
frame.

**Verified:** full `mvn clean verify` (28 tests) across all 4 modules; a
real server+client boot with zero exceptions; and a screenshot of the
actual running client confirming the widget renders correctly at full
health (panel, full blue shield ring, full green hull gradient, all
aligned). **Not verified: the partial-clip case under real damage** —
confirmed only via `HudGaugeClipTest`'s pure-math cases, not by watching
the gauge actually drain in a live dogfight (no way to simulate real
combat input from here) — needs the user to actually take a hit and
check it looks right.

### 2.7 Multi-ship-type spawning (2026-09-05)

The five non-X-wing ships (2.5/4.3) are now actually selectable and
flyable, not just displayed in the Ship Selection menu — the user
finished authoring all five `.meta.json` hitbox/attachment files, then
asked for the ships to become spawnable, explicitly reusing the X-wing's
performance numbers (thrust/torque/hull/shield) for all of them until a
real balancing pass happens.

**New `.stats.json` per non-X-wing ship**, thrust/torque/hull/shield/hud
clip numbers copied verbatim from the X-wing's — **`radiusMeters` and two
new fields, `spriteWidthMeters`/`spriteHeightMeters`, are per-ship**,
derived from each ship's actual sprite pixel dimensions at
`PIXELS_PER_METER`, not copied: Falcon/TIE Interceptor 8×8m, Snowspeeder/
TIE Fighter 4×4m, Star Destroyer 8×13.5m (its sprite is 256×432, not
square). The new width/height pair is what actually fixed the "square
bounding box" limitation flagged in 5.1 — `Client`'s ship rendering used
to force `heightPixels = widthPixels` from a single radius, which would
have squashed the Star Destroyer into a square; it now draws each ship at
its own width/height.

**Bug found via play-testing and fixed, 2026-09-06: `spriteWidthMeters`/
`spriteHeightMeters` derived from source-art pixel dimensions at the
single global `PIXELS_PER_METER` silently made a ship physically bigger
— and, since Box2D derives fixture mass from area at a uniform density,
heavier — purely because its source art happened to be authored at a
higher resolution.** Falcon, Star Destroyer and TIE Interceptor were all
imported at their 256px variant "purely a free quality choice" (see the
entry above this one) rather than the 128px X-wing/Snowspeeder/TIE
Fighter used; deriving their meters size from that 2x resolution at the
same 32px/m doubled their linear size (so ~4x the hitbox area/mass) with
no one having actually decided that. Reported by the user two ways: the
TIE Interceptor visibly rendering at roughly double an X-wing's size
when it should read as the same fighter class, and the ship generally
feeling too heavy/sluggish for its class — both symptoms of the same
root cause, correctly diagnosed by the user before a fix was even
proposed. The same bug independently affected `WeaponSystem`'s
attachment-point offsets (also converted at the global rate), which
would have desynced from the hull the moment the hitbox size was fixed
on its own.

**Fix — a per-ship `pixelsPerMeter` (`ShipTypeConfig`), replacing the
`spriteWidthMeters`/`spriteHeightMeters` fields entirely, not sitting
alongside them:** the conversion rate between a ship type's own
sprite-space pixel coordinates (hitbox polygon, attachment points — and
its source art's actual resolution) and Box2D meters. Used in exactly
two places, both previously hardcoded to the global
`PhysicsConstants.PIXELS_PER_METER`: `ShipFactory`'s hitbox polygon
conversion and `WeaponSystem`'s attachment-point conversion. Rendered
sprite size is no longer a separately-authored, hand-kept-in-sync value
at all — `Client` now derives it every frame from the actual loaded
`TextureRegion`'s real pixel dimensions divided by this same
`pixelsPerMeter`, so the visual size and the physical hitbox size are
mathematically tied to one authored number and cannot drift apart from
each other the way they just did. No density-scaling workaround needed;
once the hitbox is the physically-intended size, Box2D's existing
area × density mass calculation is simply correct.

Values: X-wing/Snowspeeder/TIE Fighter (128px source art) keep
`pixelsPerMeter = 32`, unchanged from the global rate — these were never
wrong. **TIE Interceptor → 64** (256px source ÷ 64 = 4×4m, explicitly
requested by the user: "roughly the same size as an X-wing," same
fighter class). **Falcon → 40** (256px ÷ 40 = 6.4×6.4m) and **Star
Destroyer → 32, unchanged** (256×432px ÷ 32 = 8×13.5m, same as before) —
both a judgment call rather than something the user specified a target
for; flagged as adjustable, chosen so the three non-fighter-class ships
read as an ascending scale (fighters 4m < Falcon 6.4m < Star Destroyer
8×13.5m) rather than Falcon and Star Destroyer sharing an identical
footprint as they did by accident before. `radiusMeters` (the
still-dormant circle-hitbox/default-spawn-offset fallback, design.md
2.4/2.7) updated to match each ship's corrected size too, keeping the
existing "radius = width / 2" convention every ship already followed.

Verified: full `mvn clean verify` green; a real server+client boot
flying both an X-wing and a TIE Interceptor, screenshotting each at the
same camera distance and comparing crops side by side — now genuinely
comparable on-screen size, not the roughly-2x difference from before;
zero exceptions. **Not independently re-verified:** the actual in-flight
*feel* (mass/agility) for the three corrected ships, or whether 6.4m
"feels" right for the Falcon relative to the (unchanged) 8m×13.5m Star
Destroyer — needs the user actually flying them.

**Follow-up, same day, once the user actually flew the corrected ships:**
confirmed the fix "feels much more consistent," with one more explicit
size request — **Snowspeeder scaled to 75% of its size** (it should read
as the smallest ship, per Star Wars scale intuition, but had been sharing
the same 128px/32ppm/4×4m as the X-wing and TIE Fighter). New
`pixelsPerMeter = 42.6667` (32 ÷ 0.75, so 128px source ÷ 42.6667 ≈ 3.0m,
exactly 75% of 4.0m) and `radiusMeters` scaled the same 0.75× (2.0 → 1.5),
keeping every ship's existing "radius = width / 2" convention.

**Also replaced the TIE Fighter's source art the same day**, at the
user's request, with a new custom-made 256×256 texture and matching
hand-authored `.meta.json` (hitbox polygon + attachment points traced
against the new canvas) — the original TIE Fighter only ever existed at
128px in the source sprite library, unlike Falcon/Star Destroyer/TIE
Interceptor which had a 256px option to begin with. Mechanically simple
given the `pixelsPerMeter` work above: bump `tiefighter.stats.json`'s
`pixelsPerMeter` 32→64 (same real-world 4×4m size, just sourced from
2x the pixels, exactly the TIE Interceptor's own case), replace
`assets-raw/ships/tiefighter/tie_fighter128_0020.png` with the new
`tie_fighter256_0020.png` (old one deleted outright, not kept
alongside — this ship never had both variants the way the others did),
overwrite `tiefighter.meta.json` with the new hitbox/attachment data,
repoint `Client.hullRegionName`'s `TIEFIGHTER` case at
`"tiefighter/tie_fighter256"`, and re-run `AtlasPacker` to repack
`ships.atlas` with the new source file. No `radiusMeters` change needed —
its real-world size didn't change, only its source resolution and
authored hitbox detail did.

**Verified:** full `mvn clean verify` green; a real server+client boot
flying the new-textured TIE Fighter (renders crisply, no exceptions) and
the resized Snowspeeder, with a side-by-side pixel-cropped comparison
against the X-wing confirming it now reads as visibly smaller, not just
numerically smaller. The two new files
(`tie_fighter256_0020.png`/`tie_fighter256.meta.json`) had appeared as
untracked, very-recently-modified files mid-session, before the user
asked for them by name — evidently authored in the `dev-tools` sprite
editor in parallel with this same session; flagged to the user rather
than silently touched, then integrated once they confirmed what they
were.

**Protocol change: ship type now travels end-to-end.**
`HandshakeRequest` gained a `ShipType` field (sent from the selected
Ship Selection screen entry); `ShipSpawnedMessage` and `ShipState` each
gained one too, so the owning client (from the spawn message) and every
*other* client (from snapshot state, since other clients never receive
a `ShipSpawnedMessage` for someone else's ship) both know what to
render/simulate. `ShipType` itself is now `kryo.register(...)`ed in
`MessageRegistry` like every other wire type. `GameNetworkServer` tracks
each player's requested type (`shipTypeByPlayerId`, populated at
handshake, read again at respawn — a player's ship type doesn't change
mid-match) and spawns/respawns with `ShipStats.forType(...)` instead of
the old hardcoded `ShipStats.XWING`.

**New `ShipTypeComponent`** (Ashley component, added by `ShipFactory`)
lets server-side systems look up an entity's *own* stats instead of
assuming X-wing — `WeaponSystem` now does this for its attachment-point/
default-offset lookups, the last remaining hardcoded-X-wing spot outside
`GameNetworkServer` itself.

**Client rendering** now keys a `Map<ShipType, TextureRegion>` (hull
sprite, loaded once in `show()`) and reads each ship's own
`spriteWidthMeters`/`spriteHeightMeters` — for the local player (tracked
via a new `myShipType`, set from the server's `ShipSpawnedMessage`) and
for every remote `RemoteShip` (now carries its own `shipType`, set once
at creation from `ShipState`). Hull sprite region names don't follow one
naming convention (`falcon256_0020.png` vs `tie_fighter128_0020.png`
etc.), so `Client.hullRegionName(ShipType)` is an explicit table, same
shape as `ShipSelectionScreen.descriptionRegionName`.

**HUD gracefully falls back to the X-wing's hull art** for any ship type
without its own `textures/hud/<name>_hull.png` — checked via
`FileHandle.exists()` in `ShipStatusHud`, falling back to
`xwing_hull.png` (and its clip range, when a ship's own hud clip numbers
haven't been set yet either). Confirmed visually the moment it mattered:
flying a Star Destroyer, before its own HUD art existed, showed the
X-wing silhouette in the hull gauge rather than crashing or stretching.
**All six ships have their own real hull HUD art as of the same day** —
the user provided `HUD_Status_Background_<Ship>.png` for the remaining
five (matching the X-wing's), so the fallback is currently dormant for
every existing ship type, kept only as a safety net for a future one
added before its own art exists. Each one's visible-pixel clip range
(`hudHullClipTopPixel`/`BottomPixel`) was computed, not eyeballed: an
alpha-channel bounding box at a ≥50/255 threshold, validated first
against the X-wing's already-known-correct user-supplied values (130–437
shield, 175–377 hull) before trusting it for the other five — it
reproduced the shield range almost exactly and the hull bottom edge
exactly, confirming the method. Falcon 154–416, Snowspeeder 188–384,
Star Destroyer 157–418, TIE Fighter 187–382, TIE Interceptor 191–380.

**Real bug found via an actual end-to-end test, fixed same day:**
`ShipSelectionScreen.startMatch()` disposes the screen's own textures/
batch (switching to `Client`) — but it was called from partway through
`handleInput()`, itself called partway through `render()`, so the *same*
`render()` call kept executing afterward and tried to draw with the
now-disposed `logoTexture`, crashing with a GL `"No buffer allocated!"`
error the very first time Start/ENTER was actually pressed. Fixed by
having `handleInput()` report whether a transition happened, and
`render()` returning immediately if so, before touching any batch/texture
calls for that frame. **General rule for later:** disposing `this`
mid-method is fine, but every subsequent line in that same call (and its
caller, up the stack, for the rest of that frame) must not touch what
was just disposed — return immediately, don't fall through.

**Verified — a real end-to-end test, not just unit tests:** built a real
server + client, used simulated keyboard input (`SendKeys`, via
PowerShell — the only way found to drive an actual LWJGL window's input
from here) to cycle the Ship Selection screen to the Star Destroyer and
press ENTER, confirmed via screenshot that it renders in-game at its
correct non-square size (not squashed into a square) with the expected
HUD hull-art fallback, and confirmed via server/client logs that the
connect → spawn → snapshot round trip produced no exceptions on either
side. Full `mvn clean verify` (34 tests) across all 4 modules also green.
**Not verified:** actually taking damage/firing/dying as a non-X-wing
ship (needs a real dogfight, same as every other combat-adjacent
milestone this session) — should behave identically to the X-wing since
every ship currently shares its stats, but hasn't been watched happen.

**Player observation, same day, worth remembering for the eventual
balancing pass:** even with identical thrust/torque for every ship, they
already *feel* meaningfully different to fly — the TIE Fighter noticeably
more agile, the Falcon and Star Destroyer noticeably heavier/slower.
This is Box2D's own mass/inertia model doing exactly what it should:
every hitbox fixture uses the same density (1, `ShipFactory.createBody`),
so a physically larger authored hitbox polygon has more area, hence more
mass, hence less acceleration from the same force (and less angular
acceleration from the same torque, since rotational inertia scales with
size too) — bigger ships are naturally sluggish, smaller ones naturally
nimble, with no per-ship tuning at all. Realistic and exactly the feel
wanted, purely as a side effect of the ship-type-config work above.
**Implication for the future balancing pass:** thrust/torque probably
don't need much (if any) per-ship adjustment for the *size-driven* part
of that feel — it's already emergent. A real pass should focus on
things Box2D's mass model *doesn't* give for free: hull/shield pools,
weapon loadout/damage, and top speed/turn-rate ceilings if the emergent
values ever feel wrong at the extremes (e.g. the Star Destroyer becoming
*too* sluggish to be fun) — not on refighting what already works.

### 2.8 Power distribution, implemented — and the real weapon capacitor (2026-09-06)

Implements 2.2's design in full: the three-keybind adjustment algorithm,
the reset keybind, server-authoritative per-ship state not synced to other
clients, and — since it was the natural next step once power distribution
existed (2.4/2.5 both explicitly deferred to this point) — the real
weapon capacitor mechanic too, replacing the placeholder fixed cooldown.
User provided the HUD art (`assets-raw/hud/HUD_Distribution_*.png`: a
background panel plus three separate vertical glow-bar overlays, Shields/
Weapons/Engines, each already authored in its own horizontal position on
one shared 512x512 canvas) and the exact clip range (Y=170 top/100% to
Y=423 bottom/0%, counted from the top, shared by all three bars and every
ship type — unlike the hull/shield HUD, there's only one panel design, no
per-ship variation needed).

**Two open questions resolved before implementing** (see CLAUDE.md for
the full exchange): (1) build the real capacitor now rather than defer it
further, since its prerequisite just landed; (2) design.md doesn't specify
a formula for how a system's power fraction becomes an actual effect
multiplier — agreed on the simplest option, a linear multiplier relative
to the even baseline (`multiplier = fraction / (1/3)`), applied
identically to all three systems. At the baseline this is 1.0x (no
change from today's numbers); at the 10% floor, 0.3x; at the ~78.3%
practical ceiling, ~2.35x.

**New pure/testable class, `PowerDistribution`** (`core.sim`, plus a
`PowerSystem` enum): immutable, three fractions summing to ~1.0,
`adjust(target)` implementing the exact normal/redirect/no-op clamping
algorithm from 2.2, `reset()`, `multiplierFor(system)`. Fully unit-tested
(`PowerDistributionTest`, 7 cases) including the ~78.3% practical ceiling
example from 2.2's own text (verified by simulating 9 repeated presses of
the same system from baseline) and a manufactured redirect-case scenario.

**Server-authoritative, but never actually synced — by construction, not
by filtering it out of a broadcast.** A new `PowerDistributionComponent`
(Ashley) holds each ship's authoritative split, adjusted by
`GameNetworkServer` when a new `PowerAdjustMessage` arrives (sent over
the *reliable* TCP channel, unlike movement/firing input's per-tick UDP —
this is a discrete one-shot keypress event, not continuously-resent held
state, so a dropped packet would silently change the resulting split
rather than being harmlessly superseded next tick). The owning client
keeps its own local copy in sync purely by applying the identical
deterministic `PowerDistribution.adjust`/`reset` to every keypress it
sends — since both ends run the same pure function over the same
in-order event stream, the two can't actually diverge, so there's no
reconciliation logic for this the way there is for physics (whose drift
comes from continuous floating-point/timing sources this discrete state
doesn't have). This also means opponents' power splits never need
filtering out of `WorldSnapshotMessage` — they were never going to be in
it in the first place, satisfying 2.2's "not synced to other clients"
requirement structurally rather than by remembering to omit a field.

**Where each multiplier actually gets applied:** `ShipControlSystem`
(server) and `Client.predictLocalShip` (local prediction) both
pre-multiply thrust/torque by the Engines multiplier before calling the
shared `applyInput` — kept as a plain static method with no
component/entity coupling, so the caller supplies already-scaled values;
`ShieldRegenSystem` scales `ShieldComponent.regenerate`'s rate by the
Shields multiplier; the new weapon capacitor (below) is scaled by the
Weapons multiplier.

**The real weapon capacitor**, replacing `WeaponStats`' old fixed-
cooldown-only placeholder: `WeaponComponent` now also tracks a capacitor
charge, trickle-recharging every tick (`WeaponSystem`, scaled by the
Weapons multiplier) up to a max sized for 5.5 shots, and draining one
shot's energy cost per volley (still just once per volley even when
multiple `PROJECTILE` attachment points fire together, same as the old
cooldown reset). The mechanical 0.25s/4-shots-per-second cooldown from
2.4 stays as a hard cap "on top of" the capacitor, per 2.2's own wording
— untuned placeholder numbers chosen so the two caps interact
meaningfully: at the even baseline the capacitor sustains only 2
shots/sec indefinitely (half the mechanical cap, so bursting above that
briefly and then throttling is the normal feel), while at the practical
~78.3% Weapons ceiling the capacitor's sustained rate (~4.7/sec) exceeds
the mechanical cap entirely, so investing there removes the throttle
altogether and the cooldown alone governs. No capacitor-charge HUD
element yet (not asked for, and 2.2 doesn't call for one) — only the
three-bar allocation gauge described above.

**HUD widget:** new `render.PowerDistributionHud`, structurally identical
to `ShipStatusHud`'s bottom-anchored "fuel gauge" clipping technique
(reuses the same package-private `HudGaugeClip` pure-math class) but
simpler — one shared clip range for all three bars and every ship type,
so no per-ship config is threaded through it. Each bar's fraction is the
raw power fraction itself (not renormalized against the ~78.3% ceiling),
so a bar never reads completely full even at max allocation — flagged in
the class Javadoc as intentional, not a bug, since the alternative
(silently renormalizing) would make the gauge lie about the actual
percentage allocated. Placed directly right of the existing hull/shield
widget, same bottom margin — an untuned placeholder position, like that
widget's own margin/size were when first added.

**Verified end-to-end, for real:** full `mvn clean verify` (43 tests, all
green) across every module; a real server + client boot with zero
exceptions; and — via the same PowerShell `SendKeys` technique used for
previous milestones — actually pressing the power-distribution keybinds
in a live running client and screenshotting the result: repeated presses
of **L** visibly redirected power into the Engines bar (climbing toward
the ~78% ceiling) while the Shields/Weapons bars visibly dropped toward
the floor, and **K** visibly reset all three bars back to equal. **Not
verified this way:** the actual gameplay feel of scaled thrust/shield-
regen/fire-rate in a real dogfight (needs the user actually flying with
power reallocated, not just watching the gauge respond) — the numbers
above are all untuned placeholders pending that pass, same status as
every other balance number in this project so far.

**Keybind remap + hold-to-maximize, same day, right after first
play-testing the above.** Two pieces of feedback: the original I/J/L →
Shields/Weapons/Engines mapping (5.3) didn't visually line up with the
HUD's left-to-right Shields/Weapons/Engines bar order, which read as
confusing; and holding a key should jump straight to that system's
maximum instead of only ever incrementing by 5% per press. **Remap:**
`J` = Shields, `I` = Weapons, `L` = Engines — `J`/`L` are adjacent on the
home row either side of `K` (the reset key), so their physical left-right
order now matches the bars' left-right order; `I` (the one key not on
that row, reached by moving the index finger up from `J`) takes the
remaining middle system, Weapons. **Hold-to-maximize:** a new
`PowerDistribution.maximize(target)` — unlike `adjust`, an unconditional
jump to the theoretical extreme (`target` at `1 - 2*FLOOR_FRACTION`
= 80%, both others at the 10% floor) regardless of the current split, the
same "always succeeds" character as `reset()`. `Client` tracks each of
the three keys' continuously-held duration independently
(`shieldsHold`/`weaponsHold`/`enginesHold`, a small per-key
heldSeconds+alreadyMaximized pair) and fires `maximize` exactly once per
hold, `HOLD_TO_MAXIMIZE_SECONDS` = 0.4s after the key goes down —
untuned, first value that felt reasonable. A tap (released before the
threshold) still just increments, unchanged. `PowerAdjustMessage` gained
a `Kind` (`ADJUST`/`MAXIMIZE`/`RESET`) instead of inferring the action
from a nullable target, since there are now two non-reset actions to
distinguish. Verified the same way as the initial implementation —
`SendKeys`-driven taps confirmed via exact pixel measurement (the ~5%
fraction differences involved are only a few screen pixels tall at this
widget size, not reliably visible by eye in a screenshot thumbnail), and
`keybd_event`-driven genuine key-holds (`SendKeys` alone can't simulate
a held key, only rapid down/up) confirmed visually and unambiguously:
holding **L** for 700ms snapped Engines to the ~80% ceiling and
Shields/Weapons to the 10% floor in one jump, no exceptions either side.

### 2.9 Turret weapons (Falcon/Star Destroyer only, 2026-09-06)

A second, independently-autonomous weapon system layered on top of 2.4's
player-aimed main gun: the player toggles it on/off (**T**), but once
enabled each turret mount scans for, tracks, leads, and fires at targets
entirely on its own — no aiming input from the player at all. Only the
Falcon (1 mount) and Star Destroyer (4 mounts) have any; every other ship
type is unaffected (no `TurretComponent`, no rendering cost).

**Server-authoritative, same category as projectiles/physics** — all
scanning/tracking/firing logic runs exclusively in a new `TurretSystem`
(server), never predicted client-side. The client only ever renders
whatever aim angle the last snapshot reported.

**Per-mount independence, shared config.** A ship's turret mounts come
from its `"TURRET"` attachment points (authored via the `dev-tools`
sprite editor, same convention as `PROJECTILE`/`ENGINE`/etc.) — the
Star Destroyer's 4 turrets each track their own target completely
independently, but all read the same ship-type-level tuning, a new
`TurretConfig` (`sim.metadata`, sibling to `ShipSpriteMetadata`, parsed
from a `"turretConfig"` object in each ship's `.meta.json`): scan range
(30m, the user's explicit spec), cooldown (rate of fire), and turn rate
(degrees/second). Falcon and Star Destroyer currently share identical
values (0.6s cooldown, 90°/s turn) — untuned placeholders, only the
30m scan range came from the user directly; flagged for a balance pass
once the user actually flies with it.

**Behavior loop, per mount, per tick:** if disabled, do nothing. If
enabled: drop the current target if it's no longer a live ship (queried
against a fresh `Family` result each tick, not a stale
`getComponent()` check — Ashley's plain, non-pooled
`Engine.removeEntity()` does **not** clear a removed entity's
components, so nullness alone can't detect "destroyed") or has left scan
range; if there's still no target, scan for the closest live enemy ship
within range; if still no target, idle. Otherwise, compute a lead angle,
rotate the mount's aim toward it at the configured turn rate, and fire
once aligned (within a small tolerance) and off cooldown — draining the
**same shared capacitor** as the main gun (`WeaponComponent`, 2.8):
"shares power with the normal weapon system" was read as literally one
capacitor pool, drained by whichever system fires, recharged only once
per tick by `WeaponSystem` (not duplicated in `TurretSystem`, which would
double the recharge rate for any ship with both). A shared
`AtomicInteger` projectile-id counter, threaded into both systems'
constructors from `GameNetworkServer`, keeps the two systems'
independently-fired projectiles from colliding on id.

**Shot leading (`sim.TurretAiming`, new, pure/static, no Ashley/Box2D
dependency):** classic firing-solution intercept — solve
`|relativePosition + relativeVelocity·t| = projectileSpeed·t` for the
smallest positive `t` (a quadratic, falling back to the linear case when
the target's speed is degenerate, and to "aim straight at the target's
current position" when no positive-time intercept exists at all, e.g. a
target outrunning the projectile), then aim at
`relativePosition + relativeVelocity·t` — i.e., where the target *will
be*, assuming it holds its current velocity, not where it *is*. Verified
by `TurretAimingTest` (9 cases) including a full independent geometric
reconstruction (computing intercept time a second, different way and
confirming the aimed angle actually lands on the target's projected
position), not just isolated formula checks — this project's angle
convention (0 rad = facing "north"/+Y, counter-clockwise positive,
matching `Vector2(0,1).rotateRad(angle)`) required deriving the
direction→angle inverse (`MathUtils.atan2(-dx, dy)`) fresh, so the cross-
check mattered.

**Wire/rendering:** `ShipState` gained `turretAimAngles` (a `float[]`,
one entry per mount in authored order, empty for turret-less ships) —
broadcast every snapshot like every other ship field, never predicted.
Toggling is a new one-shot reliable message, `TurretToggleMessage` (TCP,
empty payload, mirrors `LeaveMatchRequest`'s shape), applied server-side
via the same queued-action pattern as every other network-callback-
originated mutation. `Client` draws each mount's turret sprite at its
attachment point rotated to the *absolute* broadcast angle (not
combined with hull rotation — a turret keeps aiming at its target
regardless of which way the hull points), sized via that ship type's own
`pixelsPerMeter` fix (3.x) so the sprite lands at a consistent real-world
size regardless of its own source resolution.

**Turret art:** `R:\StarWars\sprites\turret`'s 40px variant for the
Falcon, 32px for the Star Destroyer (matching each ship's own
`pixelsPerMeter` — read as deliberate on the user's part). Imported the
same way as ship art (`assets-raw/ships/turrets/` → packed into the
existing `ships.atlas`, no `AtlasPacker` code changes needed —
`combineSubdirectories` already covers a new subfolder).

**Verified end-to-end, for real:** full `mvn clean verify` (62 tests,
all green) across every module; a real server + two real client
processes (Falcon and Star Destroyer, via the same PowerShell `SendKeys`/
`keybd_event` + window-focus technique used for every previous
milestone) — confirmed via server-side debug logging (since removed)
that both ships' `TurretComponent`s are created with the correct mount
counts (1 and 4) and that `TurretToggleMessage` correctly flips
`enabled`; confirmed the whole scan→track→lead→fire→hit→kill loop is
actually lethal by observing repeated, real kills/respawns once both
turrets were enabled, including at a real, deliberately-created distance
(thrusted the Falcon away first) rather than only at point-blank range;
zero server exceptions across the whole session. **Found, not fixed
(pre-existing, out of scope):** because respawn still always uses the
same fixed origin point (2.4's already-documented spawn-position
simplification), an enabled turret makes that limitation considerably
more consequential than it was for the player-aimed gun — the two ships
spawn overlapping, so a turret with any target in range gets an
immediate, unavoidable hit, which can spiral into a rapid re-spawn-and-
re-kill loop. Belongs to the still-open "map/arena design" question
(§7), not to this feature. **Not verified:** actual turn-rate/cooldown
*feel* in a real multi-ship dogfight at varied ranges (needs the user
actually flying against a turret-equipped ship, not just automated
toggling) — same status as every other untuned balance number so far.

### 2.10 Kill XP (2026-09-07)

**Decision: `XP = BASE_XP * tierMultiplier * rankDisparityMultiplier`.**
Every `ShipType` carries a **progression tier**, 1-4: the
faction-neutral Snowspeeder is tier 1, then both faction branches climb
tiers 2-4 in lockstep — TIE Fighter/A-Wing at tier 2, X-wing/TIE
Interceptor at tier 3, Star Destroyer/Falcon at tier 4. This is the exact
tier structure the "Ship Tree" idea (§7/6) already sketched — the two
branches climbing together — repurposed here as a balance input rather
than an unlock gate (the unlock gate itself still doesn't exist).

- `BASE_XP` = 10.
- `tierMultiplier` = the **killed** ship's tier — a bigger ship is worth
  more, regardless of who killed it.
- `rankDisparityMultiplier` = killed ship's tier ÷ killer's ship's tier —
  rewards an underdog kill, penalizes a lopsided one. A tier-1 Snowspeeder
  downing a tier-4 Star Destroyer earns `10 * 4 * (4/1) = 160` XP; the
  reverse earns `10 * 1 * (1/4) = 2.5` XP, rounded to the nearest whole
  number (3). Only the killer is rewarded — no XP penalty for the victim,
  that was never proposed.

**Implemented 2026-09-07.** `KillXp` (new, `core/.../sim/`) is the pure
formula, unit-tested (`KillXpTest`) the same way as `ShipDamage` — same
"standalone, testable piece of logic" convention. `ShipType` gained the
tier as a new enum constructor parameter (single source of truth,
alongside `resourceName`, rather than a parallel lookup table that could
drift out of sync). `AccountStore` gained `addXp(login, amount)`
(`AccountStoreTest` covers it, including a reload-from-disk round trip).

**Killer attribution, in `GameNetworkServer#resolvePendingHits`:** a tick
can land more than one hit on the same ship (e.g. two players' shots
connecting the same tick), so credit goes specifically to whichever hit
*first* tips the hull from "not destroyed" to "destroyed" — checked
per-hit, immediately after that hit's own damage is applied — not
whichever hit happens to be the last one processed for that ship this
tick. A new `loginByPlayerId` map (populated on handshake, cleared on
disconnect, alongside the existing `connectionsByPlayerId`/
`shipTypeByPlayerId`) is what actually lets a `playerId` translate into
an `AccountStore` login to credit. Fails soft (no XP awarded, doesn't
throw) if the killer's login/ship type is somehow missing rather than
risk crashing the tick loop over a kill-XP edge case — not expected to
actually happen, since a ship can only be destroyed by a hit, and
disconnects are themselves queued through `pendingActions` so they can't
race a same-tick kill.

Leaving a match (design.md 2.3's ESC self-destruct) still awards nothing,
structurally — `selfDestructShip` never calls the kill-handling path at
all, self-destructing is not a kill.

**Verified:** the full test suite (`KillXpTest`, updated
`AccountStoreTest`) and a server boot smoke test. **Not yet verified:**
an actual live kill in a real multiplayer session — needs two players
actually fighting, not something scriptable from here. Also still
missing, called out explicitly by the user rather than forgotten: **no
way for a player to see their own XP yet** — accounts accumulate it
correctly now, there's just no UI surfacing it.

### 2.11 Scoreboard overlay (2026-09-07)

Answers 2.10's own "no way for a player to see their own XP yet" gap —
holding **TAB** on the gameplay screen shows a panel listing every
player currently connected to the server (not just those with a live
ship — someone still on Ship Selection counts too), each row's display
name, total account XP, and this-session-only kill/death counts. Rows
sort by kills descending, then XP descending, then name — not specified
by the user, a reasonable default. Session kill/death counts are never
persisted (unlike XP, design.md 2.10) — they reset to zero for a player
the moment they reconnect.

**Art:** the user supplied `assets-raw/hud/Scoreboard.png` /
`assets/textures/hud/scoreboard.png` (768x512, not atlas-packed — same
"one panel, drawn alone, never batched with other sprites" convention
tileable backgrounds and other full-screen HUD chrome already follow) —
a holographic panel with "PLAYER SCORES" and the "NAME"/"XP"/"KILLS"/
"DEATHS" column headers already baked in. The user specified each
column's X position and the first data row's Y in the panel image's own
pixel space (matching header positions exactly); row height (32px) is
an untuned placeholder the user didn't specify, comfortably fitting more
than the 8-player cap. The panel renders at its native pixel size,
centered on screen, rather than scaled — keeps the coordinate math
identical to what the user specified, no extra scale factor to keep in
sync.

**First real user of `gdx-freetype` (design.md 4.4's addendum) for
something other than `ConnectScreen`:** row text renders in the real
"SF Distant Galaxy" font at 14px, matching the baked headers' size.

**Protocol:** new `PlayerScoreEntry` (playerId, displayName, xp, kills,
deaths) and `ScoreboardMessage` (one entry per connected player),
broadcast over TCP on a flat 1-second cadence
(`GameNetworkServer.SCOREBOARD_BROADCAST_INTERVAL_SECONDS`) — much
slower than `WorldSnapshotMessage`'s per-tick UDP broadcast, deliberately:
this data isn't render-critical, a TAB-holding player only needs
roughly-current standings, not frame-perfect ones. `GameNetworkServer`
gained two new session-only maps, `killsByPlayerId`/`deathsByPlayerId`
(incremented in `handleShipDestroyed` — a self-destruct/ESC-leave never
touches them, since it doesn't go through that method at all), cleared
per player on disconnect same as its other per-connection maps.
Display name and XP are read live from `AccountStore` at broadcast time
(not cached), so a mid-match XP gain shows up within one broadcast
cycle.

**Verified live, end-to-end, not just build+tests:** full `mvn clean
test` green across every module; a real server + two real client
processes (`red_five`/`blue_two`, fresh accounts) — held TAB on one
client, confirmed both connected players listed, correctly sorted;
fired and landed a real kill, confirmed the killer's row updated to
`KILLS=1` and real kill-XP (`30`, matching design.md 2.10's formula for
a same-tier kill) in the very next broadcast; confirmed the victim
correctly dropped off the list the moment their client disconnected to
the Death Screen (design.md 2.3-adjacent — a death always disconnects,
so "currently connected" excludes them, as intended, not a bug). Also
confirms the font's zero glyph renders as a stylized hollow ring rather
than a struck-through/plain oval "0" — consistent across every "0" seen
(a bare `DEATHS` value and the `0` inside `30`), so that's this font's
actual design, not a rendering bug.

**Addendum (2026-09-08): kills/deaths moved from session-only to
persisted account totals, and the Death Screen gained its own
scoreboard.** User feedback, after actually living with the feature for
a session: kills/deaths reset to zero on every single death, which felt
wrong — because a combat death disconnects the client (2.3-adjacent,
confirmed above), and they were tracked as in-memory state keyed by the
ephemeral per-connection `playerId`, cleared on disconnect. User's own
framing: it'd be nicer if stats survived at least until the game is
closed, or — their own suggested simplification — "maybe it's just
easier to persist them in the account." Went with the latter: simpler
(reuses the exact `AccountStore.addXp` pattern that already existed),
more robust (survives death *and* reconnect *and* even a full game
restart, not just "until closed"), and removed code rather than adding
it — `GameNetworkServer`'s two in-memory maps
(`killsByPlayerId`/`deathsByPlayerId`) are gone entirely, replaced by
`PlayerAccount` gaining `kills`/`deaths` fields (mirroring `xp` exactly)
and `AccountStore.addKill`/`addDeath`. `broadcastScoreboard()` now reads
kills/deaths from the account the same way it already read XP, so it
got *simpler*, not more complex. Old `accounts.json` entries missing
the two new fields deserialize them as `0` for free (plain Jackson
bean, no special handling needed) — verified live by loading this
project's own real accounts file, which predates this change.

`GameNetworkServer.handleShipDestroyed` also now calls
`broadcastScoreboard()` once immediately (in addition to its existing
1-second periodic cadence) right before sending `ShipDestroyedMessage` —
both go over the same reliable/ordered TCP channel, so the victim's own
client is guaranteed to have already applied the fresh
`ScoreboardMessage` (updating `Client.scoreboardEntries`) by the time it
reacts to its own death. This closed a real timing gap: without it, the
death that just happened wouldn't yet be reflected by the time...

**...the Death Screen's own new TAB overlay reads it.** Second half of
the user's ask: "it would be nice if you could call up the scoreboard
while in the death screen as well" — their own stats only is fine,
since the screen has no live server connection of its own to ask about
anyone else's (correctly anticipated by the user as the likely
complication). `Client.goToDeathScreen()` hands `DeathScreen` a one-shot
`PlayerScoreEntry` snapshot (`Client.findMyScoreEntry()`, found by
`playerId` in `scoreboardEntries` — made reliably fresh by the
immediate-broadcast-on-death above), and `DeathScreen` reuses
`ScoreboardHud` exactly as `Client` does, just rendering a single-element
array instead of every connected player's.

**Verified live, fully end-to-end, real kill included — not just unit
tests of the account logic (`AccountStoreTest` gained
`addKill`/`addDeath` cases mirroring the existing `addXp` ones):** a
real server + two real client processes (`stats_a`/`stats_b`, fresh
accounts); `stats_a` landed a real kill; `stats_a`'s live TAB scoreboard
immediately showed `KILLS=1`/`XP=30`; `stats_b`'s Death Screen, held TAB,
showed its own row with `DEATHS=1` — a real player, mid-death, entirely
disconnected from the server, correctly seeing an up-to-date stat that
used to always read zero at this exact moment. Then `stats_b` continued
(ESC) back to Ship Selection and respawned on a **new** connection (a
new `playerId`, confirmed via the server's connection log) — its live
in-flight scoreboard still showed `DEATHS=1`, not reset, proving the fix
survives a real reconnect, not just the Death Screen's one-shot
snapshot. Full `mvn clean test` green throughout.

### 2.12 Ship unlocks (2026-09-08)

Now that XP is real and persists (2.10/2.11), the user asked to spend it
on something: unlocking ship types. User-specified rules, implemented
exactly as given:

- {@link ShipType#SNOWSPEEDER}/`SNOWSPEEDER` is unlocked for every
  account from the start — no cost, no padlock, ever.
- Every other ship type costs XP to unlock, by tier (2.10's tiers):
  **1000 for tier 2** (TIE Fighter, A-Wing), **1500 for tier 3**
  (X-wing, TIE Interceptor), **2000 for tier 4** (Star Destroyer,
  Falcon) — a new `unlockCostXp` field on each ship's
  `shipdata/<name>.stats.json` (`ShipTypeConfig`/`ShipStats`).
- **XP itself is never decremented when a ship is unlocked** — it stays
  a lifetime-earned total, same spirit as 2.11's kills/deaths.
  Affordability is instead computed as **available XP = total XP −
  the summed unlock cost of every already-unlocked ship** — so
  unlocking is really just "the (persisted) unlocked-ship set grows by
  one, and that same subtraction now has one more term." New pure,
  unit-tested `core.sim.ShipUnlocks` (`isUnlocked`/`availableXp`) is
  the one place this logic lives, used identically by the client (to
  decide which padlock to show) and the server (to actually validate a
  request) — the same "shared pure function, not duplicated logic on
  each end" convention as `ShipDamage`/`PowerDistribution`.
- Persisted on the account (design.md 3.6), a new `Set<ShipType>
  unlockedShips` field on `PlayerAccount`/`AccountStore` — mirrors how
  XP/kills/deaths already work there. Never contains `SNOWSPEEDER`
  itself (never needs to, since it's unconditionally unlocked).

**User-provided art:** `assets-raw/menu/Padlock_Green.png` /
`Padlock_White.png` (174×218, packed into `menu.atlas` like the rest
of that folder) — a locked-ship overlay, drawn centered over the ship
portrait (`DialogLayout.fitCentered`, same centering `ShipSelectionScreen`
already uses for the portrait itself). Green ("'SPACE' to unlock") shows
when the currently-viewed locked ship is affordable; white ("not enough
XP") when it isn't — both messages are baked directly into the art, so
no separate live text is needed over them. An already-unlocked ship
shows neither.

**Protocol:** `HandshakeResponse` gained `xp`/`unlockedShips` fields,
sent whenever a login succeeds — `ShipSelectionScreen` is the first
consumer, but any future screen needing the same account snapshot can
read it from its own handshake too, no new message type required. Two
new messages: `UnlockShipRequest` (ship type only) and
`UnlockShipResponse` (success + a message + the account's current
xp/unlockedShips — sent whether the ship ends up unlocked just now,
was already unlocked, or the request is denied, so the client can
just always replace its local copy with whatever comes back rather
than branching on success/failure).

**`ShipSelectionScreen` becomes the second screen (after `ConnectScreen`)
to hold a live server connection** — previously it held none at all,
picking a ship and browsing was fully offline until Start. Its own
fresh handshake (design.md 3.6 — logging in again is harmless) supplies
the account's XP/unlocked ships; the connection stays open for as long
as the player lingers here (unlike `ConnectScreen`, which disconnects
the moment login is validated) so an unlock request has somewhere to
send to, and is stopped in `dispose()`. Deliberately **asynchronous**,
unlike `ConnectScreen.attemptConnect()`'s blocking pattern — blocking
here would freeze this screen's first frame for up to the connection
timeout, acceptable for a screen whose entire purpose at that moment
*is* connecting, not for one whose primary purpose is browsing ships.
Until the handshake response arrives, every non-Snowspeeder ship simply
shows locked (the harmless zeroed default), self-correcting within a
frame or two on any real connection. A connection failure itself is
still treated as fatal, matching `Client.connectToServer()`'s existing
precedent for the same class of failure.

**Server-side enforcement, not just client-side UI gating:** `SpawnRequest`
handling now checks `ShipUnlocks.isUnlocked` against the requesting
player's account before spawning, dropping the request harmlessly
otherwise — the client's own padlock/Start-button gating is a UI
convenience, never trusted on its own, same "don't trust the client"
posture every other player action already gets here. `UnlockShipRequest`
handling re-validates affordability server-side too, for the same reason.

**A real, previously-latent gap found (and fixed) while unit-testing
this:** `core`'s own test classpath never had `assets/shipdata/*.json`
on it at all — only `lwjgl3`'s and `server`'s *main* resources bundle
`assets/`, and no earlier `core` test happened to transitively touch
`ShipStats.forType(...)` (which loads that JSON) to notice. The new
`ShipUnlocksTest` was the first one to. Fixed by adding a
`<testResources>` block to `core/pom.xml` (test-scoped only — `core`'s
own packaged jar must stay exactly as it was, `lwjgl3`/`server` already
bundle `assets/` into their own runtime classpath).

**Verified live, fully end-to-end, real server + client restarts
included:** a fresh account at 0 XP showed every non-Snowspeeder ship
white-padlocked (confirmed both the default X-wing selection and TIE
Fighter), Snowspeeder itself unpadlocked; seeded to 1000 XP (editing
`data/accounts.json` directly, then restarting the server so it
re-loads it — a legitimate way to reach a precise, deterministic XP
value for testing without needing a real kill), TIE Fighter (cost 1000)
showed the green padlock; pressed SPACE, padlock disappeared; A-Wing
(also cost 1000, tier 2) immediately re-checked as white afterward
(available XP now `1000 − 1000 = 0`), directly confirming the
summed-cost subtraction, not just a single-ship check; Start correctly
did nothing for a still-locked ship and correctly launched a real match
flying the newly-unlocked TIE Fighter; **stopped both the server and
client entirely, restarted both, logged in again, and confirmed the
unlock was still there** (no padlock on TIE Fighter) — real persistence
across a full restart, not just within one session. Full `mvn clean
test` green throughout.

### 2.13 Ship Tree (2026-09-08)

§7's old "Ship Tree" idea — floated 2026-09-06, never built — is now
real: unlocking isn't just gated by XP (2.12), it's gated by branch
order too. **Imperial:** TIE Fighter → TIE Interceptor → Star
Destroyer. **Rebel:** A-Wing → X-wing → Falcon. Snowspeeder is outside
both branches, always unlocked. This **finalizes/corrects §7's original
sketch**, which had the Rebel branch as X-wing → A-Wing → Falcon — the
user's actual instruction when building this reversed the first two,
and it turns out the code already agreed: `ShipType`'s tiers (2.10),
set the day the Ship Tree idea was first floated, already had A-Wing at
tier 2 and X-wing at tier 3, i.e. already encoded this exact order. Both
branches climb tiers 2→3→4 in lockstep, so the whole tree is just one
fixed prerequisite table, not separate per-branch bookkeeping: new pure,
unit-tested `core.sim.ShipTree` (`prerequisiteOf`/`prerequisiteMet`),
same "shared pure function" convention as `ShipUnlocks`/`ShipDamage`.

**A third padlock overlay**, user-provided
(`assets-raw/menu/Padlock_White_TierTooHigh.png`, "TIER TOO HIGH" baked
in) — shown instead of green/white whenever `ShipTree.prerequisiteMet`
fails, regardless of affordability (a ship can be both unaffordable
*and* tree-blocked; the tree check wins, since knowing which ship to
unlock first is more fundamental than the XP number). Same
`SPACE`-to-unlock gating and server-side `UnlockShipRequest`
re-validation (`GameNetworkServer.handleUnlockShipRequest`) as 2.12
extended with this same prerequisite check — the server never trusted
the client's own padlock choice before, and still doesn't now that
there's a second reason to reject.

**Tooltips — new, user-requested.** Hovering the mouse over a locked
ship's portrait/padlock now shows a small floating message explaining
*why*, since neither padlock's baked-in text says specifics: "You need
to unlock the A-Wing before you can unlock this." (tree-blocked) or
"You're lacking 1000 XP to unlock this ship." (affordable-tree-wise but
short on XP) — computed by a new `ShipSelectionScreen.tooltipTextFor`.
No tooltip for the green case or an already-unlocked ship — both are
already fully explained by what's on screen. New `core.render.Tooltip`:
a small floating text box with its own drawn (not pre-made-art)
background — a tinted, stretched 1×1-pixel `Texture`, the same "no atlas
needed for one solid rectangle" trick a `ShapeRenderer` would otherwise
be reached for — using `GameFonts`' live "SF Distant Galaxy" text (the
third consumer, after `ConnectScreen`/`ScoreboardHud`). Clamped to stay
inside the window edges rather than running off-screen near a corner.
New `ShipType.getDisplayName()` (e.g. `"TIE Fighter"`, `"A-Wing"`) feeds
the tree tooltip's ship name — the first place in this codebase that's
ever needed a ship's name as live text rather than baked into art.

**A real bug caught writing `Tooltip`, before it ever ran live:**
`SpriteBatch.getColor()` returns the batch's own live, mutable `Color`
field, not a snapshot — saving that reference and later restoring
"the previous color" from it after calling `setColor(...)` would have
actually restored the *just-set* color (since `setColor` mutates that
very object in place), silently leaving the batch permanently tinted
for every draw call after the first tooltip. Fixed by `.cpy()`-ing the
color before mutating it. Caught by re-reading the draw call before
running it, not by seeing the bug happen — worth remembering:
`SpriteBatch.getColor()` needs an explicit copy before any "save/change/
restore" tint pattern, same class of gotcha as any other getter that
returns a live mutable field rather than a value.

**Verified live, fully end-to-end, with a real server + client and a
real unlock:** a fresh account (0 XP, nothing unlocked) showed the
X-wing (tier 3, Rebel) with the tier-too-high padlock — correctly
overriding the white "not enough XP" padlock that would otherwise apply
— and hovering it showed "You need to unlock the A-Wing before you can
unlock this."; TIE Fighter (tier 2, no prerequisite) correctly showed
plain white "not enough XP" with a hover tooltip reading "You're lacking
1000 XP to unlock this ship."; seeded to 6000 XP with TIE Fighter
already unlocked, TIE Interceptor (prerequisite met, affordable) showed
green, SPACE unlocked it (padlock gone); **Star Destroyer, previously
tier-too-high despite being easily affordable, immediately re-checked as
green the moment TIE Interceptor's unlock landed** — confirmed live,
no restart needed, directly proving the prerequisite check re-evaluates
against the account's just-updated unlocked set rather than a stale
snapshot. `mvn clean test` green throughout.

### 2.14 Radar / minimap — server infrastructure only, no rendering yet (2026-09-08)

The play arena is large enough that players need a way to find each
other beyond direct sight. User's spec, three independent detection
mechanisms per ship, each individually enable-able per ship type
(design.md 7's Ship Tree progression can gate them in later — not done
this session, every ship gets all three for now, uniform baseline, same
"same numbers for everyone now, differentiate later" convention this
project already applies to thrust/torque/hull/shield):

1. **Base radar** — omnidirectional, 60m range, always-on detection.
2. **Cone radar** — forward-facing arc, ±30° half-angle, 120m range.
3. **Active pulse** — "R" keybind, omnidirectional, 200m range, 30s
   cooldown. Using it also makes the *pulsing ship itself* visible to
   every other player, regardless of range or their own radar
   equipment, for the same duration the pulse's own detection lasts
   (see "One simplification" below).

**This session is infrastructure only, explicitly per the user's own
framing** — no minimap art exists yet, so nothing renders. The goal was
making sure the client receives exactly the data a minimap will need,
nothing about drawing one.

**Architectural consequence: this is the first real "fog of war" in the
project, and it changes how `WorldSnapshotMessage` is built.**
Design.md 3.5 originally had the server broadcast one shared snapshot
(every ship's state) to every client via `sendToAllUDP`. Radar detection
being meaningful at all requires the server to decide, per observing
player, which *other* ships they currently know about — so
`GameNetworkServer.broadcastSnapshot()` now builds a **personalized**
`ShipState[]` per connected/spawned player (their own ship, always,
plus whichever enemies their radar currently detects) and sends each
one individually via that player's own `Connection.sendUDP(...)`,
rather than one shared broadcast. This is a real, deliberate departure
from 3.5's original model, chosen (not just defaulted to) because it's
the only version consistent with this project's standing "never trust
the client" rule (every unlock, spawn, and movement input is already
re-validated server-side) — a client-side-only fog-of-war would be
trivially bypassable by simply drawing every ship the client happens to
receive.

**Projectiles are deliberately NOT filtered by radar** — they stay
broadcast to everyone, unfiltered, exactly as before. The user's spec
was specifically about ships; gating projectile visibility too would be
a much larger, unrequested scope addition (a shot suddenly appearing
"from nowhere" once its shooter enters detection range, or vanishing
before impact if the shooter drops out, are real design questions on
their own) — left alone for now.

**One simplification, worth flagging as a default, not confirmed by the
user:** the spec describes the pulse's own detection ("ships within
200m are picked up") and its downside ("you're visible to everyone for
5 seconds") as two separate effects, only the second with an explicit
duration. Implemented as one shared, continuously-re-evaluated window
instead of a single instantaneous snapshot at the moment of the pulse:
while a ship's pulse is active (`RadarComponent.isPulseActive()`, true
for `radarPulseRevealDurationSeconds` after triggering), it (a) also
detects everything within `radarPulseRangeMeters` omnidirectionally,
continuously, for that whole window, and (b) is unconditionally visible
to every other player's radar for that same window. Simpler to
implement and reason about than a one-tick snapshot (which at 30Hz
would be visually meaningless anyway), and a natural reading of "sends
out a pulse... every other ship within range is picked up" as lasting
long enough to actually register, not literally one simulation tick.
Revisit if the user wants the pulse's own detection to persist for a
different duration than the "I'm now visible" downside.

**New/changed files:**

- **`ShipTypeConfig`/`ShipStats`** gained 9 fields, loaded from
  `.stats.json` like every other balance number: `radarBaseEnabled`/
  `radarBaseRangeMeters`, `radarConeEnabled`/`radarConeRangeMeters`/
  `radarConeHalfAngleDegrees`, `radarPulseEnabled`/`radarPulseRangeMeters`/
  `radarPulseCooldownSeconds`/`radarPulseRevealDurationSeconds`. Every
  ship type's `.stats.json` was given the same values (60m/120m±30°/
  200m·30s·5s, all three enabled) — the exact numbers from the user's
  own spec, applied uniformly.
- **New `sim.RadarDetection`** — a pure, static, unit-tested geometry
  function (no Ashley/Box2D dependency), same pattern as `TurretAiming`/
  `ShipDamage`/`PowerDistribution`: given an observer's position/facing
  and a target's position plus each mechanism's enabled/range/angle
  values, returns whether the target is detected. Reuses
  `TurretAiming.angularDifference` for the cone's wraparound-safe
  bearing check rather than duplicating that math a third time.
- **New `sim.components.RadarComponent`** (added to *every* ship,
  unconditionally, unlike the optional `TurretComponent`) — pure
  runtime state only, no stats reference (matches `NetworkInputComponent`'s
  shape more than `WeaponComponent`'s): `pulseCooldownRemaining`,
  `pulseActiveRemaining` (both ticked down every server tick), and the
  `Set<Integer>` of currently-detected enemy player ids, recomputed from
  scratch every tick.
- **New `sim.systems.RadarSystem`** (server-side only) — each tick, for
  every live ship, checks every *other* live ship against
  `RadarDetection` (using that ship type's own config, read fresh via
  `ShipTypeComponent`+`ShipStats.forType`, same lookup pattern
  `WeaponSystem`/`TurretSystem` already use) OR'd with "is that other
  ship currently pulsing" (unconditional visibility, independent of the
  observer's own radar).
- **New `net.messages.RadarPulseRequest`** — empty payload, reliable
  (TCP) channel, same shape as `TurretToggleMessage`: the server
  identifies the requester from the connection, checks
  `radarPulseEnabled` and `!isPulseOnCooldown()`, and either triggers it
  or drops the request harmlessly (same "don't trust the client, just
  don't let a bad request do anything" pattern as every other
  player-triggered action here).
- **`ShipState`** gained `radarPulseCooldownRemaining` — broadcast for
  every ship (not just the local player's), same low-cost-now,
  no-protocol-change-later reasoning as the existing hull/shield/turret
  fields — a future HUD readout for "can I pulse again yet" needs it,
  even though nothing reads it yet.
- **`Client`** sends `RadarPulseRequest` on **R**, no local
  cooldown-tracking needed (the server already drops it harmlessly if
  premature, same as every other server-validated action). Also gained
  ship-presence pruning in `onWorldSnapshot` — `ships.keySet().removeIf(id
  -> !presentShipIds.contains(id))`, the exact same pattern already used
  for `projectiles` — a ship that drops out of radar range now actually
  disappears from the client's world instead of freezing in its last
  known position forever, which is new and necessary now that a ship
  can legitimately stop appearing in snapshots without dying or
  disconnecting.

**Explicitly out of scope this session, per the user's own framing:**
any minimap rendering/HUD widget at all (no art exists yet); tiering
which ships get which radar sub-systems (§7's Ship Tree idea — every
ship gets all three uniformly for now); any client-side visual feedback
for the pulse (a ping animation, a cooldown indicator) or for being
pulse-detected. The client-side data plumbing above (radar-filtered
`ships` map, `radarPulseCooldownRemaining`) is deliberately already
sufficient to build a minimap against once art exists — that's the
actual goal of this session's work.

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
  **Updated (2026-09-05): `sim` is mostly server-only, with one
  exception.** `GameNetworkServer` (in `server`) is the only place using
  the full Ashley `Engine` — ships, weapons, and projectiles are all
  simulated there, server-side only. The client uses exactly one small
  slice of `sim` directly, with no Ashley involved at all: `ShipFactory.createBody`
  + `PhysicsSystem` + `ShipControlSystem.applyInput`, for its own
  client-side-predicted ship body (3.5) — proving out the "client will
  likely need this again" note this bullet used to carry before
  prediction existed.
- `lwjgl3` — desktop client (rendering, input, audio, UI). Uses Box2D
  directly now too (see above, for prediction) — no longer avoiding it
  entirely the way it briefly did between the plain-sync and
  client-prediction milestones.
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

- `dev-tools` — **new (2026-09-05).** Home for offline authoring tools used
  during development but never shipped in the game jar itself — starting
  with the sprite metadata editor (2.5). Depends on `core` (for
  `ShipSpriteMetadata`/`PixelPoint`/`ShipSpriteMetadataLoader`, shared so
  the editor and the game read/write the exact same JSON shape) plus plain
  Swing/AWT — no libGDX dependency, so it has no application lifecycle to
  bootstrap and starts instantly. Run with `mvn -pl dev-tools compile
  exec:java` (`core` must already be `mvn install`ed locally first, same
  gotcha as `server`/`lwjgl3` — see CLAUDE.md). Explicitly expected to
  grow more tools over time as similar needs come up, not a one-off.

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

**Superseded, then reinstated the same day (2026-09-05):** once ship
physics moved server-only (3.5's "networked ship movement" milestone),
this exact interpolation code was removed from `PhysicsSystem`/
`PhysicsBodyComponent` — the client no longer stepped Box2D locally at
all, so there was nothing left to interpolate between on that side. It
came back, predictably, the moment client-side prediction (3.5) was
implemented: the client now steps a *local* Box2D body for its own ship
again, and needs the exact same fixed-timestep-interpolation technique
to draw that body's position smoothly. The implementation isn't
identical to the original, though — no `PhysicsBodyComponent`/Ashley
involved this time, since there's exactly one predicted body to track
client-side, not an arbitrary entity family; `Client` just keeps a plain
`myPreviousX/Y/Angle` alongside the body and reads `PhysicsSystem.getAlpha()`
directly (`PhysicsSystem` itself is unchanged from its server-side form —
it never needed Ashley/entity awareness even for the original
single-player version, only a generic "step this Box2D world" role).

**A subtler version of the same bug, found via a user report + phone
photo (2026-09-06) — this project's own "previous" snapshot had never
actually been taken *per fixed step*, just per render frame, in either
implementation above (re-read the "found and fixed" paragraph above:
"taken once per render frame" is exactly the bug).** Taking the
snapshot once per frame is fine as long as each frame runs exactly one
step, but a frame occasionally batching two steps together (the
accumulator carrying over slightly, entirely normal — the same
condition `PhysicsSystem#update(float, Runnable)`'s `beforeEachStep`
callback exists for) left `myPrevious*` stale by a whole step-pair for
that frame; since the leftover interpolation `alpha` right after
consuming two exact steps is small, the render for that frame snapped
almost exactly back to the *stale* previous position instead of
advancing — then forward again next frame. Net effect: the ship visibly
alternating between two positions a step apart, occasionally
converging when the batching lined up differently — worse the faster
the ship moves, imperceptible at low speed, which is exactly why this
was shrugged off earlier as "didn't eliminate [jitter] completely at
top speed... could even pass as an intentional near-max-speed
camera-shake effect" rather than actually root-caused. Also exactly why
a screenshot never caught it (each one just freezes a single,
individually-crisp already-composited frame) while the user's phone
camera did (its shutter caught an actual mid-transition moment). **Real
fix:** move the snapshot into the *same* per-step `beforeEachStep`
callback already used for reapplying thrust/turn input in
`Client.predictLocalShip` — capturing `myPrevious*` immediately before
*every* individual `world.step()` rather than once before the whole
batch, the identical "once per call vs. once per step" mistake already
named and fixed once for force application, just never spotted in the
interpolation snapshot too. Verified: full `mvn clean verify` green
(unaffected — this is thin render/interpolation glue with no dedicated
test, matching this project's usual convention for that kind of code);
a real server+client boot, holding thrust for several seconds to reach
speed, zero exceptions. **Not independently re-confirmed visually** —
the whole reason this bug was hard to catch is that neither a
screenshot nor (most likely) a casual glance at a code-reviewed fix can
prove the *absence* of an intermittent, camera/shutter-timing-dependent
artifact; the fix is a direct, structurally-sound port of an
already-validated pattern, but real confirmation needs the user
actually flying it.

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

### 3.5 Netcode approach (movement networked and predicted, 2026-09-05)

- **Reliable channel (TCP):** login/account handshake, join/leave, ship
  selection, spawn/despawn, death/kill events, chat, match state changes.
- **Unreliable channel (UDP):** per-tick position/velocity/rotation
  snapshots — frequent, latest-value-wins, fine to drop a packet since the
  next one supersedes it.
- **Not networked at all:** a player's own power allocation (2.2) — it's
  intentionally hidden from other players, so it never needs to leave the
  owning client/server pair beyond what's needed for the server to apply
  its gameplay effects.
- **Client-side prediction — implemented (2026-09-05).** The client runs
  its own local Box2D body for its own ship, applying held input to it
  immediately every frame via `ShipControlSystem.applyInput(...)` — the
  exact same static method the server calls, so predicted and
  authoritative physics apply identical rules and only ever diverge for
  reasons the reconciliation step below is meant to catch (packet loss,
  minor timing drift), not because the two sides disagree on the rules.
  This is what actually removes the round-trip input lag confirmed
  "pretty noticeable" in the previous milestone's play-test.
- **Reconciliation — decided, simplified (not full input-replay):** each
  `WorldSnapshotMessage` now carries velocity as well as position/angle
  per ship (`ShipState`). For the local player's own entry, the client
  compares its predicted position against the server's:
  - **Small error** (≤ 3m): blend 20% of the way toward the server's
    position/angle each snapshot — smooths out normal drift without a
    visible pop.
  - **Large error** (> 3m): hard-snap position, angle, *and velocity* to
    the server's exact values — a real desync (e.g. a burst of dropped
    UDP packets) shouldn't be allowed to leave the local prediction
    permanently wrong just to avoid a visible correction.
  **Explicitly not implemented:** sequence-numbered input buffering +
  exact replay (the "textbook" competitive-shooter approach). Considered
  and rejected for now as more complexity than this project's actual
  needs justify — small friend group, not a competitive shooter needing
  frame-perfect fairness. **Revisit if the simplified blend/snap approach
  visibly misbehaves** once tested over real (non-loopback) internet
  latency between the UK/Belgium/Norway players, rather than only LAN/
  localhost.
- **Other players' ships and all projectiles — dead-reckoned, not eased
  (changed 2026-09-05).** Originally eased toward the latest received
  snapshot every frame (the same technique used for camera-follow) — but
  that lags behind anything moving at real speed, by an amount
  proportional to speed: at the ease rate originally used, a 50m/s
  projectile would trail its true position by roughly **5 meters** at
  steady state — more than double a ship's 2m radius. Found via
  play-testing: reported as "the enemy's hitbox looks ~4× its visual
  area, shots vanish well before reaching it" — actually a rendering lag
  on both the target ship and the projectile, not a hitbox size problem
  at all (the true, server-authoritative hit was registering correctly;
  the client just hadn't visually caught up to either object's true
  position yet by the time it did). **Fixed by dead reckoning instead:**
  `WorldSnapshotMessage` already carries each ship's velocity (added for
  reconciliation); other ships now extrapolate `lastKnownPosition +
  velocity × timeSinceLastSnapshot` every frame instead of easing toward
  a stale target. Projectiles fly in a fixed direction at a fixed known
  speed for their whole flight (no thrust, no prediction), so their
  velocity doesn't need to be sent at all — it's derived client-side from
  the angle already in `ProjectileState` plus `WeaponStats.BLASTER`'s
  known speed. Neither is predicted in the client-side-prediction sense
  (2.4) — this is pure extrapolation from the last known true state, not
  locally simulating physics ahead of the server.
- **Critical bug found and fixed (2026-09-05): input force was being
  halved by the fixed-timestep loop, not a tuning problem.** The
  server ticks at `NetworkConstants.SIMULATION_TICK_RATE_HZ` = 30Hz,
  but physics steps at a fixed 60Hz (`PhysicsConstants.TIME_STEP`) — so
  almost every tick needs **two** physics steps to keep up with real
  time. Input force/torque was being applied once per tick (before
  `PhysicsSystem` stepped), but Box2D **clears a body's applied forces
  after every individual `world.step()` call** — so only the *first* of
  the two steps that tick actually got the force; the second stepped
  with none. Net effect: roughly half the intended thrust/turn torque,
  essentially every tick, which play-tested as "way below the
  unnetworked prototype, like flying in slow motion" — not a case for
  retuning `ShipStats.XWING`'s numbers, the numbers were never actually
  being applied at full strength. This also explained a second reported
  symptom (local ship visibly jittery while the same ship looked buttery
  smooth on another player's screen): the client's prediction applied
  full-strength force, so it was constantly correct-speed, while the
  server's authoritative ship was moving at roughly half that — every
  reconciliation was fighting a large, systematic gap instead of
  smoothing small noise, which is what actually caused the jitter (other
  players never saw that tug-of-war, just the — slow — authoritative
  result). **Fix:** `PhysicsSystem.update(float, Runnable)` now takes an
  optional callback invoked before *every* individual step, not once per
  outer call; both the server (`GameNetworkServer.tick`) and the
  client's local prediction (`Client.predictLocalShip`) now reapply
  their held input via that callback, so it survives every step that
  actually runs. Fixes both the speed and the jitter — they were the
  same root cause, not two separate bugs.
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

**Implemented 2026-09-06.** New `server.accounts` package: `PlayerAccount`
(Jackson bean, exactly the four fields above), `PasswordHasher` (pure/
static — SHA-256 over password+salt, hex-encoded, `MessageDigest.isEqual`
for a constant-time compare) and `AccountStore` (the file-backed map
above — `synchronized` against concurrent logins racing on the same new
login, since KryoNet's handshake handler runs on the network thread).
Deliberately has **no libGDX dependency** (plain `java.nio.file.Path`),
so it's directly unit-tested (`AccountStoreTest`/`PasswordHasherTest`, 22
cases together as of the persistence redesign below, including a real
reload-from-disk round trip) without a running application;
`GameNetworkServer` resolves the actual runtime path via
`Gdx.files.local("data/accounts.json")` and passes it in.

**One interpretation decision the spec above didn't cover, made and
flagged here:** what happens if an *existing* login's display name field
is filled in differently than what's stored? Decided: the account's
`displayName` is overwritten on every successful login, not fixed at
creation — a player can rename how they present in-game just by typing a
different one next time, with no separate "edit profile" flow needed.

**Split from the ship-spawn moment, not part of it.** The original plan
folded login into the same `HandshakeRequest` that also carried a ship
type and immediately spawned a ship (design.md 5.1's original Connect
Dialog sketch implied as much). Building it for real surfaced a problem
with that: validating a password would spawn a real ship into the world
just to check it, before the player has even reached Ship Selection.
Fixed by splitting the two moments into separate messages —
`HandshakeRequest` (login/password/displayName only, authenticates via
`AccountStore`, never touches the simulation) and a new `SpawnRequest`
(ship type only, sent once the player actually presses Start on Ship
Selection) — with `SpawnRequest` only ever following a `HandshakeResponse`
that came back accepted. See 5.1 for how this plays out across screens,
including why it's a *second*, fresh handshake on Client's own
connection rather than one kept alive from the Connect Dialog.

**Persistence redesigned from write-through to periodic async flush
(2026-09-07).** Originally `save()` ran synchronously, inline, on every
single mutation — a full rewrite of `accounts.json` on every login,
display-name change, and (once kill XP, 2.10, landed) every kill,
**on the calling thread** — for XP specifically, that's the game's own
30Hz tick thread, meaning a kill during a busy fight did a blocking disk
write as part of that tick's work. Flagged by the user as a real concern
once XP made writes far more frequent, and likely to get more frequent
still as more account metadata gets added later. Redesigned:

- `login()`/`addXp()` now only ever mutate the in-memory map, under
  `synchronized` — no disk I/O on the calling thread at all anymore.
- A background `ScheduledExecutorService` (single daemon thread) flushes
  every 60 seconds — skipped entirely if nothing changed since the last
  flush (a `dirty` flag), so an idle server doesn't keep rewriting an
  unchanged file forever.
- The **only** part that needs the lock is copying the current state —
  and it has to be a **deep** copy, not just a shallow map copy:
  `PlayerAccount` is a mutable bean, so the live objects still being
  referenced by a shallow copy could be mutated by a concurrent
  `login`/`addXp` while the background thread serializes them. Deep-copied
  via a new `PlayerAccount#copy()`. The actual disk write happens
  entirely off the lock, on the background thread, so it never blocks a
  login or an XP award.
- **Rolling backups**, a natural extension of periodic flushing: every
  30th flush (~30 minutes) also writes a timestamped snapshot to a
  `backups/` folder alongside `accounts.json` — same bind-mounted NAS
  folder (3.12), so backups show up as normal browsable/snapshot-able
  files there too — pruned to the newest 48 (~a day's worth).
- **A JVM shutdown hook** (`Runtime.addShutdownHook`, not a libGDX
  lifecycle callback — this class has no libGDX dependency and a plain
  hook is a stronger guarantee, independent of whether the surrounding
  app's own shutdown path reliably reaches it) does one final synchronous
  flush on exit. This is what keeps an ordinary restart/redeploy
  (`docker stop` → SIGTERM → the JVM's default shutdown-hook handling)
  from losing whatever changed since the last scheduled flush — verified
  for real, not just assumed: a throwaway standalone program logged in
  (dirty, unflushed) and called `System.exit(0)`, and the login was
  present on disk afterward. **Accepted trade-off, stated plainly:** only
  a hard kill (`SIGKILL`/crash) can still lose up to ~60 seconds of
  changes — the whole point of moving off synchronous write-through was
  accepting that window in exchange for never blocking the tick thread.
- A public `flush()` forces an immediate synchronous write outside the
  normal cadence — used by the shutdown hook, and directly by tests so
  they don't need to wait on real wall-clock time to observe a write.
  `close()` (idempotent — safe to call twice) stops the scheduler and
  flushes once more; it's what the shutdown hook actually calls.

`AccountStoreTest` grew from 9 to 16 cases covering the new behavior
specifically — including that a mutation genuinely isn't on disk until
`flush()` is called (the core behavior change, worth locking in with a
test rather than trusting the description), that a no-op flush doesn't
rewrite an unchanged file, and backup retention/pruning (seeded with
fake seed files rather than waiting on real wall-clock time to
accumulate 30+ real backups).

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

**Implemented 2026-09-06.** `net.ConnectionConfig` (Jackson bean, the
four fields above) + `net.ConnectionConfigStore` (load/save), landing at
`Gdx.files.local("connection-config.json")` — the "implementation
detail for later" above, settled. Saved only after a *successful* login,
overwriting whatever was there; a failed attempt leaves the last-known-
good config untouched (so a mistyped password doesn't clobber a working
saved login). `ConnectScreen` loads it in `show()` to pre-fill the four
fields, falling back to just `serverHost = "localhost"` on a first
launch (no saved file yet).

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

**First real usage (2026-09-05):** `ShipSpriteMetadata`/`PixelPoint` (2.5)
— none of the systems above (accounts, connection config, keybinds) are
built yet, so this was Jackson's first actual exercise in the codebase.
Confirmed the bean-style convention (public no-arg constructor + getters/
setters, as opposed to the immutable-final-field style used for Kryo
network message classes) round-trips cleanly, including a `Map<String,
List<PixelPoint>>` value.

### 3.10 Build/release versioning & client-server version check (2026-09-07)

Client and server are separate downloads that can drift out of sync — a
player on an old client build talking to an updated server (or vice
versa) is a real failure mode for a KryoNet-based protocol (3.4/CLAUDE.md
"Kryo network compatibility depends on registration order"). Wanted an
automatic version number to check at connect time, without a manually-
maintained constant to remember to bump.

**Decision: [jgitver-maven-plugin](https://github.com/jgitver/jgitver-maven-plugin)
computes `${project.version}` for the whole reactor from git tags — no
version number is ever hand-edited in a pom.xml.** Wired in as a Maven
*core* extension via `.mvn/extensions.xml` (loaded before the build
starts, so it can override the version before anything else reads it).
Every `pom.xml` in the reactor now carries a placeholder `<version>0</version>`
(the parent's own version, and each child's `<parent><version>`) that
jgitver overwrites in memory at build time — the `0` is never the real
version, just satisfies Maven's own consistency check between a child and
its declared parent version before the extension runs.

Considered and rejected:
- A hand-maintained `PROTOCOL_VERSION` constant, bumped only when the wire
  format actually changes — this is the "correct" engineering answer, but
  for a "play with friends" hobby project, reliably judging *which*
  changes are wire-breaking is more mental bookkeeping than it saves, and
  forgetting a bump (silently shipping an incompatible protocol as
  "compatible") is a worse failure than being occasionally too strict.
  **Explicitly decided against, in favor of laziness:** the version check
  (once built, see below) will compare the full computed version string,
  so *any* new release forces old clients to update, whether or not the
  protocol actually changed.
- `versions-maven-plugin`-driven bumps from CI — rejected, adds a bump
  commit to history for every release, more moving parts than tags alone.
- `git-commit-id-maven-plugin` — would add commit-provenance metadata
  (hash, dirty flag, build time) but doesn't compute the version itself;
  jgitver was the more direct fit for "increase the version automatically
  every time I package for release."

**How the computed version behaves, with jgitver's defaults (nothing
configured beyond the extension itself):**
- HEAD exactly on a tag `vX.Y.Z`, working tree clean → version is exactly
  `X.Y.Z`.
- Any commit after that tag with no new tag yet → `X.Y.(Z+1)-SNAPSHOT`
  (patch auto-incremented, `-SNAPSHOT` appended) — every ordinary dev
  build already looks like a pre-release of the next patch.
- **A dirty working tree also triggers this, even with HEAD exactly on a
  tag** (found 2026-09-07 testing 3.12's Docker build locally, uncommitted
  server/deploy files in the tree at the time): jgitver's default
  `useDirty=false` only controls whether a `-dirty` *qualifier* gets
  appended to the string, not whether uncommitted changes count as "not
  the clean tagged release" in the first place — they do, and bump to the
  next-patch `-SNAPSHOT` exactly like an actual commit past the tag
  would. A `git describe --tags` showing HEAD is literally on the tag is
  **not** the same question jgitver answers; don't use one to predict the
  other. Commit everything before expecting a clean `X.Y.Z` build.
- No tag reachable at all → `0.0.0-SNAPSHOT` (confirmed by running `mvn
  -N validate` before `v0.0.1`, the first tag, existed).
- Cutting a release is therefore just `git tag vX.Y.Z` (or drafting a
  GitHub Release through the UI, which creates the tag for you) — nothing
  in a file to edit, nowhere to forget a bump.

**Bug found and fixed (2026-09-07): jgitver miscomputes a `-SNAPSHOT`
version inside GitHub Actions' checkout, even exactly on a tag.** Found
when `v0.0.3`'s client (built locally, clean) and server (CI-built)
failed the version handshake against each other despite both nominally
being "the v0.0.3 release." Traced with three separate local
reproductions - a plain `git checkout v0.0.3` on the existing clone, a
fresh `git clone` + checkout, and a manual simulation of
`actions/checkout`'s minimal-fetch strategy - and **all three correctly
computed clean `0.0.3`**, while the *actual* `release-client.yml` (`v0.0.2`
and `v0.0.3`) and `release-server.yml` (`v0.0.3`) CI logs both showed
`[INFO] version 'X.Y.(Z+1)-SNAPSHOT' computed`. So this has silently
affected **every CI-built artifact since the very first tagged release** -
not a regression from anything added in 3.11/3.12, just never surfaced
before because client and server had always been built by the same CI
mechanism and were at least internally *consistent* with each other,
until this session cross-checked a locally-built client against a
CI-built server. Root cause not fully pinned down (jgitver has a
[documented issue](https://github.com/jgitver/jgitver-maven-plugin/issues/12)
where ambiguous/duplicate tag refs on one commit force a SNAPSHOT
result, which fits the shape of the symptom, but wasn't confirmed as
*the* mechanism here) - rather than keep chasing GitHub Actions'
internals, both release workflows now pass `-Djgitver.use-version=X.Y.Z`
explicitly (stripped from `$GITHUB_REF_NAME`, the tag that triggered the
run) instead of relying on jgitver's auto-detection for CI builds at
all: `release-client.yml` passes it straight to `mvn`, `release-server.yml`
threads it through `server/Dockerfile`'s `ARG APP_VERSION` (empty by
default, so a local `docker build` with no `--build-arg` still falls
back to correct auto-detection). Verified locally with `podman build
--build-arg APP_VERSION=9.9.9` before trusting this in CI again. Local/
dev builds (`mvn -N validate`, `mvn -pl lwjgl3 -am -Prelease-client
verify` without the profile's own CI wrapping) are unaffected and keep
using plain auto-detection, since they were never the ones misbehaving.

**`v0.0.1` created (2026-09-07)** as the first tag, purely to see the
mechanism work end to end — not a real release, no GitHub Release/build
artifacts published for it (see the still-open items below).

**Version check — implemented 2026-09-07:**
1. `core`'s `starwars-version.properties` (`version=${project.version}`,
   resource-filtered — `core/pom.xml`'s `<build><resources>`) is read by
   the new `AppVersion` (`core/.../net/`), a small eager-loading wrapper
   around the classpath resource. Since both `server` and `lwjgl3` depend
   on `core` and bundle its resources into their shaded jars, they
   automatically carry the same baked-in version without duplicating
   anything.
2. `HandshakeRequest` (3.6) gained a `version` field, stamped by
   `NetworkClient#sendHandshake` from `AppVersion.getVersion()` —
   deliberately *not* read by `HandshakeRequest` itself, to keep it a
   plain data holder; the network layer is what's responsible for knowing
   where the version comes from. `HandshakeResponse` did **not** need a
   matching field: on rejection its existing free-text `message` already
   names both versions, and the existing "wrong password" error display
   in `ConnectScreen` (5.1) shows it verbatim — no UI changes needed at
   all for this feature.
3. The comparison itself lives in `NetworkServer#resolveHandshakeResponse`
   (new, private), called from `onReceived` *before* `handleHandshake` —
   so a version mismatch is rejected before `GameNetworkServer`'s account
   lookup (3.6) ever runs, and every current/future `NetworkServer`
   subclass gets the check for free rather than having to remember to add
   it. Covered by `NetworkServerClientIntegrationTest` (real loopback
   sockets, a deliberately wrong version) and `AppVersionTest`/
   `MessageRegistryTest`'s round-trip test (updated for the new field).

4. **Client packaging and `update.cmd` — implemented 2026-09-07, see 3.11.**
5. A tag-triggered GitHub Actions workflow (`push: tags: v*`) that runs
   the `release-client` Maven profile (3.11) and publishes
   `StarWars-Client.zip` to a GitHub Release for that tag — deliberately
   **not built yet** (holding off until closer to an actual first
   release, see CLAUDE.md status). Because jgitver ties the jar's
   embedded version to the same tag that would trigger this workflow, the
   two are automatically in sync with no manual step to keep them that
   way.

**Gotcha to remember:** the "install core first" local workflow
(CLAUDE.md, Build system) — `mvn install -pl core -am -DskipTests` — bakes
in whatever version jgitver computes *at that moment*. Any commit made
afterwards changes the computed version for the *next* build (even of an
unrelated module), so a stale locally-installed `core` artifact can go
missing from `~/.m2` under the version `lwjgl3`/`server` now expect. Rerun
that install after any new commit, not just once per session.

### 3.11 Client packaging: a self-contained zip via jpackage (2026-09-07)

**Decision: `jpackage --type app-image`, not a hand-built/committed `jre/`
folder.** The original idea was to author a trimmed JRE once ourselves and
commit it to the repo; instead, `jpackage` (built into the JDK) runs
`jlink` internally and produces a native `StarWars.exe` launcher plus its
own trimmed runtime, built fresh from the build machine's JDK 25 at
package time. **Nothing is committed to git for this** — no `jre/` tree in
source control at all, and no separate `start.cmd` either (the generated
`.exe` *is* the launcher, a deliberate simplification over the original
sketch).

Server distribution (a Docker container on the user's NAS) and the actual
GitHub Actions release workflow are both explicitly **out of scope** —
this only covers turning a local build into a zip a player can unzip and
run.

**Layout**, `jpackage --type app-image` on Windows:
```
StarWars/
  StarWars.exe        <- native launcher (auto-generated)
  app/
    StarWars-<version>.jar
    StarWars.cfg
  runtime/             <- the jlinked, trimmed JRE
    bin/ conf/ lib/ ...
  update.cmd            <- added by the build, not by jpackage itself
```
`ConnectionConfigStore` (3.7) writes `connection-config.json` via
`Gdx.files.local(...)`, which resolves against the process's CWD — for a
double-clicked exe that's the exe's own folder, so it lands at
`StarWars/connection-config.json`, sibling to `app/`/`runtime/`/
`update.cmd`. This is the line `update.cmd` must never cross: it only
ever replaces `StarWars.exe`, `app/`, `runtime/`, and itself.

**Maven wiring** — all in `lwjgl3/pom.xml`'s new `release-client` profile
(opt-in: `mvn -pl lwjgl3 -am -Prelease-client verify`, so an ordinary
`mvn clean package` is completely unaffected):
- `build-helper-maven-plugin`'s `parse-version` goal splits
  `${project.version}` so jpackage's `appVersion` can be composed from
  just the numeric `major.minor.incremental` part — jpackage's
  `appVersion` rejects jgitver's `-SNAPSHOT`-qualified versions outright.
  This is purely cosmetic (Explorer file properties, Add/Remove
  Programs); `AppVersion.getVersion()` (3.10) keeps reading the full,
  untouched version for the actual handshake check.
- Four packaging steps — stage a clean jar-only input dir, run jpackage,
  copy `update.cmd` in, zip the result as `StarWars-Client.zip` — are
  each bound to their **own distinct standard-lifecycle phase**
  (`pre-integration-test` → `integration-test` → `post-integration-test`
  → `verify`), not crammed into one shared phase relying on
  plugin-declaration order. **Learned the hard way**: jpackage refuses to
  run if its destination app folder already exists, and an earlier
  same-phase `copy-resources` execution had created exactly that folder
  as a side effect of copying `update.cmd` into it before jpackage ran.
  Separately: **Maven silently drops one side of a duplicate same-GA
  `<plugin>` declaration** within one `<plugins>` list (with only a
  build warning, not an error) — two separate `maven-resources-plugin`
  blocks for two unrelated copy steps looked reasonable but only one
  ever actually ran; the fix was merging both `copy-resources`
  executions into a single plugin block with two differently-phased
  `<execution>`s.

**Verified 2026-09-07** by actually building and running the packaged
output (not just a successful `mvn` exit code, same discipline as the
native-library gotchas in CLAUDE.md): `StarWars-Client.zip` extracts to
the layout above; the generated `StarWars.exe` launches and stays running
with no missing-module errors — the jlink/jdeps auto-detection risk
(reflection-heavy Kryo/Jackson/VisUI) did **not** materialize, so the
`addModules` escape hatch left in the pom's config comment wasn't needed;
`update.cmd`'s self-relaunch-from-`%TEMP%` step and its download-failure
path both exercised for real (no release exists yet, so the download
genuinely 404s) — correct banner output, correct error message, correct
`pause`, correct non-zero exit, and critically nothing in the install
folder was touched.

**`v0.0.2` (2026-09-07) was the first real release-workflow run** — tag
pushed, `.github/workflows/release-client.yml` fired, built, and
published a real non-prerelease `StarWars-Client.zip` (~87 MB) in under
two minutes, confirmed via `gh run watch`/`gh release view`. The user
then downloaded that actual release asset, unzipped it, and ran the real
`StarWars.exe` outside any scratch/CI environment — the whole tag → CI →
GitHub Release → manual download → working game chain confirmed end to
end on the first try. **Still not exercised**: `update.cmd`'s actual
successful download/extract/staged-swap/rollback path specifically (as
opposed to a plain manual browser download) — that needs an *existing*
install to run `update.cmd` against, which hasn't been set up yet.

**`update.cmd`** (`lwjgl3/src/main/dist/update.cmd`) design, in order:
self-relaunch a copy of itself from `%TEMP%` first (it's one of the files
about to be replaced, so the running instance can't safely be the one
doing the replacing); refuse to proceed if `StarWars.exe` is currently
running (a live JVM holds `app/*.jar` and `runtime/bin/*.dll` open, so
nothing could be replaced anyway); download + extract via **`powershell.exe`
only — Windows PowerShell 5.1, bundled with Windows 11, never `pwsh.exe`**
(a separate, not-guaranteed-installed download) — every PowerShell
snippet in the script is deliberately 5.1-compatible syntax; verify the
extracted exe actually exists before touching anything; stage the swap by
renaming the existing `app/`/`runtime/`/`StarWars.exe` aside with an
`.old` suffix, moving the new ones in, and only deleting the `.old`
folders once every move succeeded — any failed move rolls back rather
than leaving a half-replaced install. The release asset is always named
`StarWars-Client.zip` (never version-embedded), so `update.cmd` can hit
GitHub's fixed `releases/latest/download/StarWars-Client.zip` URL
forever with no API/JSON parsing needed — but this only resolves against
the newest **non-prerelease** release, a constraint the eventual release
workflow needs to respect.

**Release-process note (not code, but easy to get wrong later):** because
the version check (3.10) is an exact-string comparison, a client zip only
makes sense once a server built from the *same tag* is already running
wherever players connect. The eventual release sequence needs to be: tag
→ rebuild/redeploy the NAS server container from that tag → *then*
publish the client zip. Publishing the client first means players who
update immediately start failing the handshake against a server that
hasn't caught up yet.

**Icon and release workflow — implemented 2026-09-07.** The app icon
lives at `assets-raw/icon.ico` (alongside this project's other
pre-processed source art, design.md's `assets-raw/` → `assets/`
convention) rather than under `lwjgl3/src/main/dist/` as originally
sketched — jpackage's `icon` parameter just needs a build-time file path,
never the classpath, so no copy into `assets/` is needed either.
`.github/workflows/release-client.yml` runs on `push: tags: v*`, checks
out full history (`fetch-depth: 0`, `fetch-tags: true` — jgitver needs
real tag history, not a shallow clone), builds with the exact same `mvn
-pl lwjgl3 -am -Prelease-client verify` a local release build uses, and
publishes `StarWars-Client.zip` to a GitHub Release via `gh release
create` (not a third-party Action, since `gh` is pre-authenticated on
GitHub-hosted runners via `GH_TOKEN`/`secrets.GITHUB_TOKEN`).

**Known limitation, deliberately accepted for now (2026-09-07): this repo
is currently private.** GitHub release assets on a private repo require
authentication to download, so `update.cmd`'s anonymous
`Invoke-WebRequest` — and a friend just clicking the releases page — get
a 404 today. Only the repo owner (authenticated) can actually exercise
the full download/update flow right now; decided to keep it this way
until closer to the first real playtest with people outside this GitHub
account, at which point the repo needs to go public for any of this
distribution mechanism to work for anyone else. Worth remembering before
assuming "the release pipeline works" means "a friend could use it
today" - it doesn't yet.

**Still open:** `update.cmd`'s actual swap path (see the `v0.0.2` note
above); no code-signing certificate is planned, so first run will
trigger Windows SmartScreen's "protected your PC" warning (accepted
trade-off, not a bug); the icon is a single 32×32 image, not a
multi-resolution `.ico` (fine for now, could look sharper at other
sizes - e.g. the taskbar - later).

### 3.12 Dedicated server deployment: Docker via QNAP Container Station (2026-09-07)

The user runs the dedicated server on a QNAP TS-451+ NAS (Intel Celeron
J1900, 4 cores @ 1.9GHz, 8GB RAM) behind a FritzBox router, using QNAP's
Container Station (a GUI over a real Docker engine) — confirmed on QTS
5.2.10.3577 / Container Station 3.1.2.1742, comfortably above Container
Station 3's published minimum (QTS 5.1.0+ on an x86 NAS).

**Decision: GitHub Actions builds and pushes a Docker image to GHCR on
every `v*` tag push; Container Station pulls it directly and runs it as a
single-service Docker Compose "Application"** — chosen over QNAP's manual
`docker save` → transfer → Import-tar path after researching how Container
Station actually works. Since the NAS is an *authenticated puller*, not an
anonymous public downloader (unlike the client zip's problem, 3.11),
keeping the image private alongside the private repo is completely
workable — this half of distribution doesn't force anything public.

**Container Station's four sidebar sections**, for future reference: they
map directly onto Docker's own concepts — Images/Containers/Applications/
Volumes = Image/Container/Compose-stack/Volume. "Applications" is
Compose-based, but a compose file with exactly **one** service is
perfectly valid — not exclusively for multi-container stacks, contrary to
first impression. Chosen over the quicker "Create Container" wizard
specifically because the whole config (ports, volume, restart policy)
lives in one YAML file worth keeping in this repo (`deploy/docker-
compose.yml`), matching this project's habit of writing decisions down
rather than leaving them as one-off GUI clicks nobody can reconstruct
later.

**`server/Dockerfile`** — multi-stage:
1. `maven:3.9-eclipse-temurin-25` build stage, `mvn -pl server -am
   package` (tests run, not skipped — a broken build shouldn't ship as an
   image). Build context is the **whole repo root**, not a hand-picked
   subset of directories — Maven has to parse *every* module the root
   `pom.xml` declares (`lwjgl3`/`dev-tools` included) to build its reactor
   graph before `-pl`/`-am` can even apply; a context missing either
   directory fails at that parse step, not the build step `-pl` would
   otherwise restrict to. Found this the hard way on the first build
   attempt (`Child module .../lwjgl3 ... does not exist`) — fixed by
   `COPY . .` instead of copying `core`/`server`/`assets` individually,
   relying on `.dockerignore` (repo root) to keep `target/` etc. out.
2. `eclipse-temurin:25-jre` runtime stage — deliberately **not** the
   `-alpine` variant: libGDX's native libraries (its own core native plus
   Box2D, both pulled in via `natives-desktop`) are built against glibc,
   not Alpine's musl.
3. `ENTRYPOINT` passes `--enable-native-access=ALL-UNNAMED` explicitly
   (the README's documented flag for running this jar directly) — unlike
   `lwjgl3`'s shade config, `server/pom.xml`'s doesn't bake this into the
   manifest.

**`.github/workflows/release-server.yml`** — a separate workflow from
`release-client.yml` (different runner: `ubuntu-latest` for a plain
Docker build vs. `windows-latest` for jpackage), same `push: tags: v*`
trigger, running independently/in parallel. Pushes both the exact tag and
a floating `latest` to `ghcr.io/mariokoehler/starwars-server`;
authenticates to GHCR via the workflow's own `GITHUB_TOKEN`
(`permissions: packages: write`) — no separate secret needed for the
*push* side, since that's GitHub Actions pushing to GitHub's own registry
from inside a run.

**`deploy/docker-compose.yml`** — the actual Container Station
"Application" YAML. Deliberately pins an **exact version tag**, never
`latest` — the version check (3.10) is an exact-string match, so which
server build is actually running has to be a conscious edit-and-redeploy
action, not whatever `latest` happened to resolve to at pull time.
Bind-mounts a real NAS folder (`/share/Container/starwars-server/data`,
the QNAP-idiomatic convention over a Docker-managed named volume) onto
`/app/data`, since `GameNetworkServer` resolves the account store as
`Gdx.files.local("data/accounts.json")` relative to the container's
working directory (`server/GameNetworkServer.java`) — without this
mount, every redeploy would silently wipe every player's account.

**Verified locally 2026-09-07** (via `podman build`/`podman run` — no
NAS/Container Station access from here): the image builds successfully
end to end and the container starts, binds both published ports, and
reaches `[GameServer] Listening on TCP 45625 / UDP 45626` with no
missing-native-library errors — same "did libGDX/Box2D's natives
actually load on Linux" risk class as the earlier CLAUDE.md gotchas,
confirmed by actually running it, not just a successful `docker build`
exit code. One real build bug found and fixed this way: the first attempt
copied only `core`/`server`/`assets` into the build stage and failed
Maven's reactor parsing (see the Dockerfile note above) - only surfaced
by actually building the image, `docker build`'s own success/failure on
a syntactically-fine Dockerfile wouldn't have caught it any earlier.

**Not yet done or verified** (tracked here so it isn't lost): actually
deploying to the real NAS (Container Station registry credentials,
creating the Application, creating the bind-mount folder first); FritzBox
port forwarding (TCP 45625 + UDP 45626
→ the NAS's LAN IP); whether Container Station's restart policy actually
survives a crash and not just a clean reboot — community reports found
during research were inconsistent on this, don't trust either mechanism
(the GUI's "Auto Start" toggle vs. a real `restart:` policy) without
testing it by deliberately killing the process inside a running
container; the exact GitHub PAT scope Container Station needs for
registry login (a **classic** PAT with `read:packages` is the
well-documented, reliable choice — fine-grained PAT support for GHCR
looked mixed/uncertain across current sources, not confidently
recommendable).

**Release-process reminder** (3.11 already flagged the coupling; this is
the concrete mechanism for it): redeploying the server for a new tag
means editing `deploy/docker-compose.yml`'s image tag and using Container
Station's Update/Recreate — do this *before or alongside* publishing the
matching client release, never after, or players who update immediately
start failing the handshake against a server that hasn't caught up yet.

### 3.13 Embedded dev-only MCP server for remote-controlling the client (2026-09-07)

As screens have gotten more numerous, verifying a change by driving the
real client from here has meant OS-level automation — `SendKeys`/
`keybd_event` + window-focus juggling + `PrintWindow` screenshots (see
CLAUDE.md's many "verification gotcha" notes) — which is slow, fragile,
and only ever lets me *observe* pixels, never assert on real state. MCP
(Model Context Protocol) is a standard way for an external process to
expose typed "tools" that I can call directly with structured arguments
and get structured results back, instead of guessing screen coordinates.
User's ask: build a minimal version, starting with just the Connect
screen (the screen this pain showed up on most).

**Only ever runs in a dev build, on request** — started from
`Lwjgl3Launcher.main` only when launched with a `--mcp` argument;
completely absent from a normal player-facing launch. Uses the official
`io.modelcontextprotocol.sdk` Java SDK (`mcp-core` + `mcp-json-jackson2`
— the SDK's own default `mcp` bundle pulls in Jackson 3.x, this project
already standardized on Jackson 2.x, design.md 3.9) over the SDK's
built-in **stdio** transport: newline-delimited JSON-RPC over stdin/
stdout, the same shape Claude Code's own MCP client speaks, so the
running client process itself becomes an MCP server Claude Code can
spawn/attach to directly.

**Architecture — three pieces:**

1. **`core.remote`** (plain Java, no MCP dependency — the SDK dependency
   is `lwjgl3`-only): `RemoteControllable` is the interface a screen
   implements to be driven this way (`screenName()` + `describeState()`);
   `RemoteControlRegistry` tracks whichever one is currently showing (at
   most one, same as `Game`'s own single active `Screen`) — a screen
   registers itself in `show()`, clears itself in `dispose()`.
   `RemoteControlQueue` is the cross-thread bridge: the MCP server runs
   on its own thread(s), but only the render thread may touch live
   Scene2D/libGDX state — the exact same "enqueue a Runnable, drain once
   per frame" pattern this codebase already uses for KryoNet callbacks
   (`Client`/`GameNetworkServer`), except a tool call needs the actual
   *result*, so `submit(Callable)` returns a `CompletableFuture` the
   calling MCP thread blocks on (with a timeout) instead of firing and
   forgetting. `StarWarsGame.render()` drains it every frame, first,
   before delegating to the current screen.
2. **`ConnectScreen`** is the first (and so far only) screen wired up:
   implements `RemoteControllable`, and gained `remoteLogin(host,
   displayName, login, password)` — sets the four fields' text, then
   calls the *existing* `attemptConnect()` unchanged, so a remote-driven
   login exercises the exact same validation/blocking-connect/
   transition-or-error logic a real ENTER press does, not a parallel
   reimplementation. `describeState()` deliberately omits the password
   field.
3. **`lwjgl3.mcp.McpBridge`**: builds the actual `McpSyncServer` and
   registers two tools, each just a thin wrapper that submits an action
   to `RemoteControlQueue` and blocks for the result:
   - `get_active_screen` — no arguments; returns which screen is active
     (or `"NONE"`) and its `describeState()`.
   - `connect_screen_login` — `host`/`displayName`/`login`/`password`;
     fails with an explanation if the Connect screen isn't active,
     otherwise logs in and reports either `{"loggedIn": true}` (the
     registry no longer points at that screen instance — it moved on to
     Ship Selection) or the screen's post-attempt state including
     whatever error it's showing.

**stdout hygiene:** the stdio transport requires stdout to carry
*nothing but* JSON-RPC — libGDX's/KryoNet's own logging would corrupt
it. `McpBridge.start()` captures the real stdout first, then redirects
`System.out` to `System.err` for everything else, and hands the SDK the
captured stream explicitly (`StdioServerTransportProvider`'s
3-argument constructor takes explicit streams, confirmed straight from
the SDK's own test fixtures — its docs page's prose actually undersells
this, only mentioning the no-argument constructor).

**Real gotchas hit verifying this (worth remembering — none were bugs
in the SDK or this code, all were either research or test-harness
issues):**

- **Don't trust a web-summarized code example for exact API/Maven
  coordinates on a fast-moving SDK.** An initial `WebFetch`-summarized
  "minimal example" invented a plausible-looking but wrong artifact id
  (`mcp-core` vs. the real `mcp-core`+`mcp-json-jackson2` pairing) and a
  wrong version. Cross-checked against Maven Central's actual
  `maven-metadata.xml` (ground truth for what's really published) and
  the SDK's own `docs/quickstart.md`/`docs/server.md` plus real
  compiling test fixtures (`StdioUtf8TestServer.java`,
  `SyncToolSpecificationBuilderTest.java`) fetched straight from its
  GitHub repo via `gh api` — every class/method name used here was
  confirmed against actual source, not a summary of it, before writing
  any code, and it compiled correctly on the first try.
- **With no SLF4J binding present, the SDK's internal error logging
  silently no-ops** — a malformed inbound message doesn't error back to
  the caller at all, it just logs (to nowhere) and closes the session,
  which looks exactly like "the server never responds to anything."
  Added `slf4j-simple` (logs to stderr, keeping stdout clean) as a real
  dependency, not just a debugging aid — genuinely useful for anyone
  hitting a silent failure here later.
- **The actual silent failure, once visible via slf4j-simple:** a
  stray UTF-8 BOM (`EF BB BF`) at the very start of stdin broke the
  *first* message's JSON parse (Java's `InputStreamReader` doesn't
  auto-strip a BOM, unlike some other language runtimes) — sent by this
  session's own PowerShell test harness (`Process.StandardInput`
  reliably prepending one, confirmed via a `cmd.exe < file` redirection
  test that had none). Fixed defensively on the Java side regardless
  (`McpBridge.stripLeadingUtf8Bom`, a 3-byte `PushbackInputStream`
  check) rather than only in the test script — other real MCP clients
  on Windows could plausibly do the same thing, and tolerating one
  costs nothing.
- **A one-shot "feed a file to stdin" test can't validate a *slow* tool
  call.** `connect_screen_login` blocks for a real network round trip;
  reading from a plain file hits genuine EOF the instant the file's
  been fully read (regardless of how long the process then runs), which
  the transport treats as "the client disconnected" and starts closing
  down — so the tool's eventual response fails to send
  (`Failed to enqueue message`), even though the actual login already
  completed correctly server-side. Not a bug — a real stdin *pipe*
  (kept open) never hits this, and that's exactly what Claude Code's
  own MCP client is. Confirmed by switching the test harness to a live
  `System.Diagnostics.Process` with the pipe kept open for the whole
  exchange.

**Verified live, fully end-to-end, both fast and slow tool calls, no
keyboard/mouse/screenshot automation involved:** spoke raw MCP JSON-RPC
directly over a real client process's stdin/stdout (`initialize` →
`notifications/initialized` → `tools/list` → `tools/call`), confirmed
correct protocol negotiation and tool schemas; then, against a real
running dedicated server, called `get_active_screen` (confirmed
`"CONNECT"` plus its field state), `connect_screen_login` with real
credentials (confirmed `{"loggedIn": true}`), and `get_active_screen`
again (confirmed `"NONE"` — `ConnectScreen` had unregistered itself,
Ship Selection isn't remote-controllable yet) — then screenshotted the
actual window and visually confirmed it had genuinely reached Ship
Selection. Full `mvn clean test` green throughout, unaffected (no new
JUnit tests added for this — the two-thread queue/registry classes are
thin, and the real risk surface is the protocol wiring, which the live
test above already exercises end-to-end).

**Registered as a project-scoped MCP server, committed to the repo:**
`.mcp.json` (repo root) points Claude Code at `cmd.exe /c
"<absolute path>\start_mcp_client.cmd"` — that wrapper script always
reinstalls `core` and repackages `lwjgl3` first (same "no safe way to
skip it" reasoning as `start_client.cmd`/`start_server.cmd`, jgitver),
then launches the freshly-built jar with `--mcp`. Every line the
wrapper (and Maven itself, via `-q` plus explicit `1>&2` redirects)
prints goes to stderr, never stdout — verified by running the *exact*
configured command end-to-end (not just the jar directly) and
confirming stdout carried nothing but the two expected JSON-RPC
responses. **Known limitation:** `.mcp.json` hardcodes this machine's
absolute path (`C:\Users\mario\StarWars\...`) rather than a portable
variable — a relative path failed unreliably through this exact spawn
path (`cmd.exe /c <relative-name>` did not consistently search the
working directory despite `ProcessStartInfo.WorkingDirectory` being set
correctly; absolute path sidesteps it), and no verified
`.mcp.json`-variable-substitution syntax was confirmed to fall back on.
Fine for a single-developer machine; would need revisiting if this
project is ever worked on from a second machine/path.

**Deliberately out of scope for this minimal pass** (see how this
feels before extending it, per the user's own framing): only
`ConnectScreen` is remote-controllable — Ship Selection, the gameplay
screen, and the Death Screen aren't yet, and there's no generic
"press this key"/"click this point" escape hatch, only screen-specific
typed actions. Extend the same `RemoteControllable` pattern to more
screens once this one has proven useful in practice, rather than
building the rest speculatively now.

### 3.14 Shared `AssetManager` + splash screen (2026-09-08)

The user noticed occasional longer-than-expected pauses switching
screens/scenes while playing, and correctly suspected the cause: this
codebase had no `AssetManager` at all. Every screen (`ConnectScreen`,
`ShipSelectionScreen`, `Client`, `DeathScreen`) constructed its own
`Texture`/`TextureAtlas` instances directly from disk in `show()` and
`dispose()`d them on the way out — so `ships.atlas`/`menu.atlas` in
particular were reloaded from scratch on almost every transition (e.g.
Ship Selection loads both, then `Client` reloads `ships.atlas` again a
moment later), and — worse, once actually audited — every HUD widget
(`ShipStatusHud`, `PowerDistributionHud`, `ScoreboardHud`) was
constructing (and disposing) its *own* fresh copies of its background/
gauge/panel textures every single time `Client` or `DeathScreen` was
shown — meaning every single combat death (not just a rarer screen
transition) reloaded and re-uploaded a stack of HUD textures too.

**Fix — the same shape the user described from past projects:** a small
splash screen loads every shared texture/atlas once, up front, into an
`AssetManager` held for the whole run of the app; every other screen
just reads already-resident assets from then on.

- **`StarWarsGame`** now owns the `AssetManager` (`getAssets()`) and
  starts on the new `SplashScreen` instead of `ConnectScreen` directly;
  overrides `dispose()` to dispose the manager (frees every texture/
  atlas) after the inherited screen-hide behavior, once, at real app
  shutdown.
- **New `render.GameAssets`**: the single source of truth for every
  shared asset's classpath path (3 atlases, ~15 standalone HUD/menu/
  background textures, all 7 ship types' HUD hull textures, the Death
  Screen's dialog background + all 23 quote images), plus
  `queueAll(AssetManager)` — called exactly once, by `SplashScreen`.
- **New `SplashScreen`**: logo on a black background (`ScreenUtils.clear`),
  plus a simple progress bar (a tinted/stretched 1x1 white pixel texture,
  same technique `Tooltip` already used for its background box — no
  `ShapeRenderer` needed for one rectangle) driven by
  `AssetManager.getProgress()`. The logo itself is loaded and
  `finishLoadingAsset`-forced synchronously first (a single small
  texture, negligible blocking cost) so it's actually visible on this
  very screen, before `GameAssets.queueAll` queues everything else for
  the asynchronous loading this screen's `render()` drives one step at a
  time via `AssetManager.update()`. Hands off to `ConnectScreen` the
  frame `update()` returns `true`.
- **Every consumer screen** (`ConnectScreen`, `ShipSelectionScreen`,
  `Client`, `DeathScreen`) now calls `game.getAssets().get(GameAssets.X,
  ...)` instead of `new Texture(...)`/`new TextureAtlas(...)`, and no
  longer disposes those specific fields itself (the asset manager owns
  them now) — each dispose() left a one-line comment explaining why,
  rather than silently dropping the call.
- **`ShipStatusHud`/`PowerDistributionHud`/`ScoreboardHud`** all gained an
  `AssetManager` constructor parameter and now resolve their textures
  from it instead of loading their own; `ShipStatusHud`'s per-ship-type
  hull texture fallback (design.md 2.7) is preserved, just re-expressed
  as `assets.isLoaded(path, Texture.class)` instead of a `FileHandle
  .exists()` check (`GameAssets.queueAll` only queues a hull texture for
  a ship type when the file actually exists, same condition, checked
  once up front instead of lazily). `ShipStatusHud`/`PowerDistributionHud`
  own nothing anymore and dropped `Disposable` entirely rather than keep
  a meaningless empty `dispose()`; `ScoreboardHud` keeps it, since it
  still owns one per-instance `GameFonts`-generated `BitmapFont` (see
  below).
- **`ScrollingBackground`** dropped `Disposable`/`dispose()` outright — 100%
  of its call sites now pass in the same `AssetManager`-owned
  `menu_starfield.png`, so it never owned a texture to free in the first
  place after this migration. **`ParallaxBackground.Layer`** gained a
  `boolean ownsTexture` constructor parameter instead, since it has two
  genuinely different use cases side by side in `Client`: `blue_nebula.png`
  (now asset-managed, `ownsTexture = false`) and
  `PlaceholderStarfield.generate(...)`'s procedurally-generated texture
  (still built fresh, and therefore still disposed, per `Client`
  instance — see below).

**Deliberately left outside the `AssetManager`, on purpose, not by
oversight:**

- **`GameFonts`-generated `BitmapFont`s** (the live "SF Distant Galaxy"
  rendering, design.md 4.4) — still generated fresh per call site
  (`ConnectScreen`'s UI font, `Tooltip`'s and `ScoreboardHud`'s own
  fonts). Rasterizing a small font from a `.ttf` is fast (single-digit
  milliseconds), nowhere near the cost of a full texture atlas reload,
  and routing it through the `AssetManager` would need real extra
  plumbing (`FreetypeFontLoader` + a distinct `AssetDescriptor` per
  pixel size actually used) for a cost that isn't the problem being
  fixed. Revisit only if font generation itself is ever measured as a
  real contributor.
- **`PlaceholderStarfield`'s generated texture** — procedural, not a
  file on disk, and explicitly a stand-in for real star-dot art the
  project is still waiting on (design.md 4.2) anyway; still generated
  (and disposed) fresh per `Client` instance.
- **`audio/StarWarsTheme.mp3`** — streamed `Music`, loaded exactly once
  by `ConnectScreen`, not a repeated-load concern this migration needed
  to touch.

**Verification status:** full `mvn clean install` (all 4 modules,
packaging included) and `mvn test` (core + server) both green,
confirming the refactor compiles and every existing unit test still
passes. **Not yet verified live** — the user asked this session not to
launch the client/server at all today (working from the office, and
`ConnectScreen`'s background music would otherwise play at an
inopportune moment), so the actual splash screen (logo + progress bar
rendering correctly, then handing off cleanly to `ConnectScreen`) and
the actual pause-reduction switching between Ship Selection ↔ gameplay ↔
Death Screen still need a real play-test before this is considered
fully confirmed, the same as every other milestone in this project.

### 3.15 Network diagnostics: absolute timestamps + connect/stop/tick timing (2026-09-08)

Added while investigating a still-open screen-transition-pause report
(CLAUDE.md has the full investigation trail) — kept here because it's
permanent architecture, not throwaway debugging code.

- **`core.net.NetworkLogging`** installs a custom minlog `Log.Logger`
  that prefixes every KryoNet log line with an absolute wall-clock
  timestamp (`HH:mm:ss.SSS`) instead of minlog's default
  time-since-process-start elapsed counter, so a client-process log line
  and a server-process log line (two independent processes, started at
  different times) can actually be lined up against each other.
  Installed once, idempotently, from both `NetworkClient`'s and
  `NetworkServer`'s constructors — either alone covers the whole
  process, including KryoNet's own internal connect/disconnect lines.
- **`NetworkClient.connect()`/`stop()`** log how long the call actually
  took. Both are otherwise unremarkable wrappers around the underlying
  KryoNet fork's own methods — the timing exists purely because those
  two specific calls turned out to have highly variable, real-world
  multi-second-to-20+-second durations on at least one development
  machine, confirmed by a from-scratch, game-free JUnit test
  (`NetworkServerClientIntegrationTest`), not by anything in this
  project's own code.
- **`Client.show()`/`dispose()` and `ShipSelectionScreen.show()`/`dispose()`**
  log their own total elapsed time, to localize a stall to a specific
  screen-transition step relative to the `NetworkClient`-level timing
  above.
- **`Client.render()`** and **`GameNetworkServer.tick()`** both log a
  warning if called with a `deltaTime` beyond 0.5s — a direct signal
  that the calling thread (render thread client-side, tick thread
  server-side) was blocked/stalled since the previous call, independent
  of knowing *why*.
- **`GameNetworkServer.handleHandshake`** logs its own elapsed time, to
  rule in/out `AccountStore.login()` (already synchronous-in-memory-only,
  background-flushed — design.md 3.6) as a contributor.

None of this is behind a flag — it's cheap (a handful of
`System.currentTimeMillis()` calls and log lines per screen transition/
connect/stop, not per-tick or per-frame) and directly useful for any
future networking-timing question, not just the specific investigation
that motivated it.

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
a turret overlay rotated independently of the hull (built 2.9 — not
static after all, once the turret weapon system itself existed);
deployable mines are **out of scope for v1** entirely (revisit alongside
other pickup ideas, e.g. the capacitor pickup mentioned in 2.2).

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
- **Second atlas added (2026-09-05): `projectiles.atlas`**, packed from
  `assets-raw/projectiles/` (`red_dot.png`/`blue_dot.png`, 7×7 each,
  provided by the user — see 2.4). Kept as its own atlas/category rather
  than folded into `ships.atlas`, matching the one-atlas-per-content-
  category convention; `AtlasPacker.main()` just has a second `pack(...)`
  call now. Region names have no frame index (no numeric filename
  suffix), so they're looked up with plain `atlas.findRegion("red_dot")`.
- **Five more ships' neutral-bank frames imported (2026-09-05):** Falcon,
  Snowspeeder, Star Destroyer, TIE Fighter, TIE Interceptor — same
  `_0020.png` neutral-bank frame convention as the X-wing, copied
  verbatim from `R:\StarWars\sprites\<ship>/` into
  `assets-raw/ships/<ship>/`. **Resolution choice for the two ships that
  had both:** Falcon and TIE Interceptor exist at both 128px and 256px;
  picked **256px** for extra headroom (display size is driven by each
  ship's `radiusMeters` stat, not the source resolution, so this is a
  free quality choice, not a gameplay one) — flagging since design.md's
  original inventory note (above) didn't specify which to use. Star
  Destroyer's frame is non-square (256×432 — it's a capital ship), which
  the dev-tools editor already handles fine (no square-canvas
  assumption anywhere in it). **Portraits not imported** — the user is
  preparing Ship Selection screen assets separately (6) and didn't ask
  for these yet.
- **Bug found and fixed while packing the above (2026-09-05):**
  `TexturePacker.Settings.combineSubdirectories` defaults to `false`,
  so with 6 ship subfolders under `assets-raw/ships/` the packer treated
  each as an entirely separate pack, producing `ships.png` through
  `ships6.png` — one dedicated page per ship type even though most are
  far smaller than the 1024×1024 page limit, wasting texture memory and
  adding an extra texture bind per ship type at render time. Fixed by
  setting `combineSubdirectories = true` in `AtlasPacker`; all 6 ships
  now share one `1024×512` page. Region naming is unaffected either way
  (still subfolder-prefixed, e.g. `falcon/falcon256`).
- **Not yet done:** no `ShipType` enum entries (2.6), `.stats.json`, or
  `.meta.json` for these five yet — deliberately out of scope for this
  step. The user is authoring each one's hitbox polygon and attachment
  points next via the `dev-tools` sprite metadata editor (2.5), which
  gained a **`TURRET`** suggested attachment name for this
  (`SpriteCanvas.SUGGESTED_ATTACHMENT_NAMES`) — for the Falcon's/Star
  Destroyer's turret mount point(s); the turret weapon system itself
  isn't built yet (design.md 4.3's turret-overlay plan), just the
  attachment point convention. Wiring these ships up as actually
  spawnable/playable (`ShipType` entries, stats, ship selection) is
  explicitly the next milestone after that.

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

**First real usage: `ConnectScreen` (2026-09-06).** VisUI 1.5.9 added to
`core`'s POM. Rather than reskin every widget by hand, only what actually
needed a custom look got one: a Pillow-generated dialog background
(baked header text + "well" rectangles, matching `Select_Ship_Dialog`'s
navy/gold palette) and a two-state Connect button (up/hover, same
convention as the Start button), with the four `VisTextField`s given a
**fully transparent** custom style (background, focused background, *and*
VisUI's own `backgroundOver` — a hover-only field VisUI adds beyond the
base `TextField.TextFieldStyle`, easy to miss and left at the default
skin's light box otherwise) so they sit invisibly on top of the baked
"well" art, contributing only their live cursor/typed/masked text.
The static field labels ("Server", "Display Name", etc.) are baked
directly into the dialog panel art (chrome that never changes, same
principle as the Ship Selection dialog's own baked "Previous/Next:"
label) — only the field text itself, the error label, and the button's
label are live Scene2D text. See 5.1 for the full screen writeup.

**Live TTF text via `gdx-freetype` (2026-09-07).** Until now, every
piece of in-game text using "SF Distant Galaxy" (the logo, "SELECT YOUR
SHIP!", the combat-lock banner, etc.) was pre-baked into art with
Python/Pillow, because libGDX's built-in `BitmapFont` can only load
already-baked bitmap fonts, not raw `.ttf` files — there was no live
rendering path for text whose content isn't known ahead of time (typed
form input, error messages). `gdx-freetype` (an official libGDX
extension, not a third-party plugin) closes that gap:
`FreeTypeFontGenerator` rasterizes a real `.ttf` into a `BitmapFont` at
a given pixel size at runtime. The actual font file
(`assets/fonts/sf_distant_galaxy.ttf`, sourced from the same "SF
Distant Galaxy.ttf" already installed locally and used for the baked
art, source copy at `assets-raw/fonts/sf-distant-galaxy/`) is now
bundled with the game rather than assumed to be present on the machine
it runs on. New `core.render.GameFonts.generateSfDistantGalaxy(sizePx)`
wraps the generate/dispose dance (the generator itself is only needed
to bake the glyph texture and is disposed immediately after; the
returned `BitmapFont` owns that texture from then on and is the
caller's own responsibility to dispose).

`ConnectScreen` is the first (and so far only) consumer: one
`BitmapFont` generated at 24px and assigned directly to the three
custom `Style` copies it already builds (`VisTextFieldStyle`,
`Label.LabelStyle` for the error label, `VisTextButtonStyle`) — a
narrower, per-screen swap rather than replacing VisUI's global
`default-font`, since this is currently the only screen with live
text. Field text, the error message, and the Connect button's label
all render in the real game font now, not VisUI's stock one.

Needs its own native library, same class of dependency as
`gdx-box2d-platform` (CLAUDE.md "Maven + libGDX gotchas"):
`gdx-freetype` in `core`'s POM (the Java API,
`FreeTypeFontGenerator`), `gdx-freetype-platform` classifier
`natives-desktop` in `lwjgl3`'s POM (the actual FreeType native lib).
Not added to `server` — the server never renders text.

**Licensing note, not yet resolved:** "SF Distant Galaxy" was already
being used to bake art (a use the font's original license may or may
not actually cover), but bundling the raw `.ttf` file itself in the
repo and redistributing it inside the game's assets is a further step
— its exact license/redistribution terms haven't been checked. Revisit
before this project is ever shared or distributed beyond the current
players.

**Known, deliberately accepted mismatch:** VisUI 1.5.9 (latest on Maven
Central as of this writing) is itself pinned to gdx 1.14.1 in its own
POM, one patch version behind ours (1.14.2) — it logs a startup warning
about this that `ConnectScreen` silences via VisUI's own
`setSkipGdxVersionCheck(true)` escape hatch, since only long-stable
Scene2D/Skin APIs are involved and this exact combination has been
exercised live with zero issues (see CLAUDE.md for the full note).
Revisit (drop the skip call) once a VisUI release targets 1.14.2+.

### 4.5 Audio

**First real audio in this codebase, added 2026-09-06:** the Star Wars
theme plays on `ConnectScreen` (5.1), for as long as the player
stays on it. Uses libGDX's `Music` (a streamed track, not `Sound`'s
fully-loaded-into-memory short-clip model — the right tool once a track
runs more than a few seconds), `setLooping(false)` since it should just
end naturally if the player lingers past its runtime, and no separate
volume/mute setting yet (not asked for).

**Fade-out, not a hard cut, on leaving the screen:** a fade takes real
time (`StarWarsGame.MUSIC_FADE_OUT_SECONDS`, 1.5s, untuned) that outlives
whichever screen started it — `ConnectScreen` is already disposed by the
time a fade finishes. Handled by `StarWarsGame` instead (the one object
alive for the whole app run, already home to `QuoteDeck` for the same
"outlives individual screen instances" reason): `ConnectScreen` hands its
`Music` off to `StarWarsGame#fadeOutAndDisposeMusic` right before
disposing itself, and `StarWarsGame` overrides the top-level
`Game#render()` (not any one screen's `render(delta)`) to tick the fade
down every frame regardless of which screen is now showing, disposing
the track once it reaches silence.

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
  chasing the sub in *The Phantom Menace*). Pressing **ESC** returns to
  Ship Selection (corrected from this section's original ENTER sketch once
  the user's delivered art turned out to bake in its own "Press 'ESC' to
  continue" — see the implementation note below). This screen is reached
  **only** by dying in combat, not by a voluntary ESC leave.

**Connect Dialog — implemented 2026-09-06.** See 3.6 (accounts),
3.7 (local config) and 4.4 (VisUI) for the pieces this screen wires
together; this entry is the screen itself. `StarWarsGame.create()` now
starts on `ConnectScreen`, not Ship Selection — the very first thing
the app does is log in.

- **Keyboard-only usable, not just mouse-clickable — an explicit
  requirement, not an accessibility afterthought:** **TAB**/**Shift+TAB**
  cycle keyboard focus across the four fields (wrapping both ways),
  **ENTER** submits from any of them, matching the Connect button's own
  click handler. Needed for this project's own remote-control
  verification technique (CLAUDE.md) as much as for a mouse-free player.
  **Real bug found building this:** libGDX's `TextField` defaults
  `focusTraversal` to `true` — it already handles TAB itself (jumping
  focus in *Stage actor-tree order*, not the intended field order) via
  its own internal listener, which fires *before* a stage-level listener
  ever sees the key event. With both handlers active, one TAB press
  advanced focus **twice** — confirmed live (typed text landing in the
  wrong field, two fields ahead of the one just tabbed from). Fixed by
  calling `setFocusTraversal(false)` on all four fields, leaving exactly
  one thing driving focus order.
- **Second bug found the same way: leftover input can bleed into the
  next screen's first frame.** Pressing ENTER to submit a login
  occasionally also read as "press ENTER to start a match" on Ship
  Selection's very next `render()` call, skipping ship selection
  entirely and launching straight into gameplay flying the default
  X-wing — caught by an automated end-to-end run that filled the form
  and watched the actual result rather than assuming success. libGDX's
  "just pressed" flag lives for exactly one frame, so it can still read
  true on a screen that's switched to mid-frame. **General rule for any
  future screen transition that reuses a key across screens:** absorb
  one frame of input on the new screen after `show()` rather than
  assuming a clean slate — `ShipSelectionScreen` now ignores input on
  its first `render()` call specifically for this.
- **Validation:** all four fields required (client-side, before any
  network round trip) — empty fields show "All fields are required."
  immediately, no connection attempt. A rejected handshake (wrong
  password) shows the server's own message and leaves every field as
  typed, so fixing just the password doesn't mean retyping everything.
- **No separate "connecting..." UI state** — `attemptConnect()` runs the
  connect + handshake round trip synchronously (blocking up to
  `NetworkConstants.CONNECTION_TIMEOUT_MILLIS` on an unreachable host),
  same simplification `Client.connectToServer` already accepted for the
  same reason. Revisit both together if it ever feels bad in practice.
- **Verified live, end-to-end, for real:** the same PowerShell
  `SendKeys`/window-focus technique used for every previous milestone —
  typed all four fields via TAB navigation, submitted with ENTER,
  confirmed a real account appended to `accounts.json` with a proper
  salted hash; a wrong-password retry against that same account was
  rejected with the exact message shown on-screen, fields intact; fixing
  just the password and resubmitting succeeded; a fresh launch afterward
  showed all four fields correctly pre-filled from the saved local
  config; completing the flow through Ship Selection into a real
  Falcon spawn confirmed the fresh second handshake + `SpawnRequest`
  path works. Also confirmed Shift+TAB wraps backward correctly and
  that clearing a field and resubmitting shows the empty-fields error
  instead of attempting to connect.

**Ship Selection screen — implemented 2026-09-05, now reached from a
successful login instead of being the app's start screen (2026-09-06).**

- **Architecture shift: the app is now a `Game`, not a single
  `ApplicationAdapter`.** `Client` (gameplay) was, until now, the entire
  `ApplicationListener` — the only screen that existed. With a second
  screen needed, `StarWarsGame extends Game` is the new true entry point
  (`Lwjgl3Launcher` now constructs it, not `Client` directly); `Client`
  was converted to implement `Screen` instead (`create()`→`show()`,
  `render()` gained its `delta` parameter, plus no-op
  `pause()`/`resume()`/`hide()` — `dispose()` unchanged). `Game` doesn't
  auto-dispose the screen being switched away from, so whichever screen
  initiates a transition (`ShipSelectionScreen`'s Start handling) disposes
  itself explicitly right after calling `setScreen(...)`.
- **Cycles every {@code ShipType}** (`ShipType.values()` — see 2.6's
  enum) via Previous/Next, by mouse click on the arrow buttons or the
  **left/right arrow keys**; **Start** button or **ENTER** starts a match.
  User-provided art (`assets-raw/menu/`): a tileable starfield background,
  a pre-rendered dialog box (background art already contains the
  "Select Your Ship!"/"Previous/Next:" text), two arrow buttons with
  mouse-over variants, a start button with a mouse-over variant, and one
  description image per ship type.
- **No Scene2D/VisUI for this screen — decided.** Despite 4.4's decision
  to use VisUI for menu-style screens in general, this one is entirely
  pre-rendered dialog art plus a handful of rectangular hit-test regions
  (two arrows, one button) — exactly what this codebase's existing raw-
  `SpriteBatch` style (`ShipStatusHud`, `ParallaxBackground`) already
  handles well, with no skinning/widget work to save by reaching for
  VisUI. VisUI remains reserved for a screen that actually needs form
  widgets — the Connect Dialog and Keybind Setup, once built.
- **Pixel-perfect placement, given as exact coordinates in the dialog
  image's own pixel space** (top-down, top-left origin — matching
  `TextureRegion`'s convention, not `PixelPoint`'s sprite-local Y-up
  one): arrows at Y=124, X positions derived from "equidistant between
  X=594 and X=755" (three equal 21px gaps around the two 49px-wide
  arrows → X=615 and X=685); the ship portrait scaled down to fit a
  384×384 area at (370, 172); each description image's top-left fixed at
  (37, 171). The top-down-to-screen-space conversion, and the
  scale-to-fit-and-center math for the portrait, are pulled into a small
  pure `DialogLayout` utility specifically so this pixel math is
  unit-tested (`DialogLayoutTest`, 6 cases) rather than eyeballed —
  same split as `SpriteCoordinates`/`HudGaugeClip`, but `public` since
  its one consumer (`ShipSelectionScreen`) isn't co-located in `render`.
- **Logo** (`assets-raw/Logo.png`, user-provided, 5847×1784) drawn
  horizontally centered, sized to a fraction of and vertically centered
  within the gap between the screen's top edge and the dialog's top edge
  — a placeholder-tuned fraction (70%), not pixel-specified.
- **Background drift** — `ScrollingBackground` (new, `core.render`):
  like `ParallaxBackground.Layer`, it slides a repeat-wrapped tileable
  texture's sampled UV coordinates for the scroll illusion, but
  autonomously over elapsed time in a fixed direction rather than tied to
  a world camera's position (there's no camera/player on this screen) —
  and unlike `ParallaxBackground.Layer`, the drawn quad always exactly
  fills the screen and never itself moves, only the sampled UV offset
  advances. Direction/speed are untuned placeholders.
- **Portraits imported** (2026-09-05): `portrait.png` for all six ship
  types, from `R:\StarWars\sprites\<ship>/`, into
  `assets-raw/ships/<ship>/` alongside each type's hull frame — picked up
  automatically by `ships.atlas` (region name `<resourceName>/portrait`).
  `Menu_Background.png` (tileable, needs `Repeat` wrap) was moved from
  `assets-raw/menu/` into `assets-raw/backgrounds/menu-starfield/`,
  matching `blue_nebula.png`'s precedent — tileable art doesn't get
  atlas-packed (bleeds at tile edges under `Repeat`), it's copied
  straight to `assets/textures/` and loaded as its own `Texture`. New
  `menu.atlas` packs everything else in `assets-raw/menu/` (dialog,
  arrows, buttons, descriptions).
- **Bug found and fixed while building this (2026-09-05):**
  `ScrollingBackground`'s `TextureRegion` field was constructed with the
  no-arg constructor (`new TextureRegion()`), which leaves its underlying
  `Texture` reference `null` — the UV-only `setRegion(u,v,u2,v2)`
  overload used every frame only adjusts sampling coordinates, it doesn't
  (and can't) attach a texture. Crashed with a `NullPointerException` on
  the very first frame. Fixed by constructing `new TextureRegion(texture)`
  in the constructor body (not a field initializer — the `texture` field
  isn't assigned yet when field initializers run, only once the
  constructor body executes). **General rule for later:** the no-arg
  `TextureRegion()` constructor is only useful if `setRegion(Texture)` (or
  the full-texture-plus-UV overload) is called before first use — the
  UV-only overload alone is not enough.
- **Bug found while packing the new art (2026-09-05):** same
  `combineSubdirectories` gotcha as 4.3's ship import, this time for
  `assets-raw/menu/` — fixed by the same setting (already flipped on for
  all packs). Also bumped `AtlasPacker`'s max page size from 1024 to 2048
  — six ship portraits (512×512 each) on top of the existing hull frames
  no longer fit one 1024×1024 page.
- **Known limitation, deliberate:** starting a match currently always
  hands off to a plain `new Client()` regardless of which ship is
  selected — see `ShipSelectionScreen`'s class Javadoc and `ShipType`'s
  Javadoc (2.6) for why (only the X-wing has a `.stats.json`; the Star
  Destroyer's non-square sprite doesn't fit `Client`'s current
  square-bounding-box rendering assumption either). The selection itself
  is fully real; only "actually fly the selected ship" is pending a
  follow-up milestone.
- **Verified:** full `mvn clean verify` (30 tests) across all 4 modules;
  a real client boot with a screenshot confirming correct layout (logo,
  dialog, arrows, portrait, description all positioned as specified); a
  real server + client boot confirming the Start transition into
  `Client`'s gameplay screen doesn't crash when a server is actually
  reachable (it does throw — pre-existing, documented, unrelated to this
  screen — if no server is running, since there's still no Connect
  Dialog/error screen to catch that, see `Client.connectToServer`'s
  existing Javadoc note).
- **Not yet done:** hover-state visuals (arrow/button mouse-over texture
  swap) are implemented but not separately screenshotted (a static
  screenshot can't show hover); the user should try moving the mouse over
  the arrows/Start button themselves.

**Addendum (2026-09-08): this screen now actually enforces the "ships
available here are gated by the account's current XP" line from this
section's own screen-flow bullet above** — previously every ship was
freely selectable/flyable regardless of XP, since the flow diagram
described the intent before the mechanism existed. Full writeup in the
new 2.12 ("Ship unlocks"). Short version: the screen now holds its own
live server connection (its handshake response carries the account's
XP and unlocked-ship set) so a locked ship's portrait gets a padlock
overlay — green ("'SPACE' to unlock") or white ("not enough XP")
depending on affordability — and Start is disabled for a still-locked
ship. Unlocking is a live round trip (`UnlockShipRequest`/
`UnlockShipResponse`) over that same connection, not a purely local
UI state change.

**Second addendum (2026-09-08): a third padlock (branch prerequisite)
and hover tooltips.** Full writeup in the new 2.13 ("Ship Tree"). Short
version: a locked ship whose branch prerequisite isn't unlocked yet
(design.md's Imperial/Rebel unlock order) now shows a third
"tier too high" padlock instead of green/white, regardless of
affordability; hovering any locked ship's portrait now shows a small
tooltip explaining exactly why — the missing XP amount, or which ship
to unlock first — since neither padlock's baked-in text alone gives
that specific.

**Death Screen — implemented 2026-09-06.** User provided all the art:
`Dialog_Background.png` (a plain bordered panel) and 23
`Quote_<n>.png` variants, all 818×618 - the same dialog size Ship
Selection uses. Each quote image is a complete "death card" (character
art + the quote text + its own border) with fully transparent corners,
so `Dialog_Background` drawn underneath at the same position provides a
consistent frame around whichever quote is showing. Each quote image
also has its own baked-in "Press 'ESC' to continue", which is why this
screen's real continue key is **ESC**, not the ENTER this section
originally sketched before the art existed - the delivered art wins.

New `render.QuoteDeck`: a pure, unit-tested (`QuoteDeckTest`, 4 cases)
shuffle-bag over the 23 quote indices - deals every index once before
any repeat, reshuffling a fresh deck once exhausted, per the user's
explicit request. Session-local only (in-memory, never persisted, per
the same request) by construction: it's a plain field, not backed by
any file. Owned by `StarWarsGame` (the one object that lives for the
whole app run) rather than by `DeathScreen` itself, since a fresh
`DeathScreen` instance is created for every death and would otherwise
forget what had already been shown. This is also why every screen's
constructor now takes the concrete `StarWarsGame` instead of the
`Game` interface - `ConnectScreen`, `ShipSelectionScreen`, and `Client`
all only ever needed `Game` for `setScreen(...)`, and there's only one
`Game` implementation in this project, so nothing is lost by depending
on the concrete class where it actually needs to reach
`getQuoteDeck()`.

**Combat death now really leaves the match**, rather than the
pre-Death-Screen placeholder behavior of silently waiting for the
server's automatic mid-match respawn (`RESPAWN_DELAY_SECONDS`, 2.4):
`Client#onShipDestroyed`'s non-`leavingMatch` branch now disposes and
switches to `DeathScreen` immediately, the same connection-teardown
shape as a voluntary ESC leave (2.3). The server-side respawn timer
itself wasn't touched - it's simply never exercised by this client
anymore, since it always disconnects well inside the 3-second window,
and an unclaimed timer for an already-disconnected player is already
handled harmlessly by `GameNetworkServer#onDisconnected`'s existing
cleanup.

**Real bug found live, by the user actually playing it, not by
review: a multi-second, whole-window freeze between dying and the
Death Screen appearing.** Root cause: the first version of this screen
atlas-packed all 23 quote images (`AtlasPacker`, same pipeline as every
other art category) - but they're never drawn together, only one per
death, so packing them just forced whichever ~4 full 2048×2048 pages
(~67MB decoded) the atlas split into into GPU memory on every single
death, synchronously, on the render thread. **General rule this
confirms rather than newly discovers: atlas-pack art that gets *batched*
together in the same draw call (many ship frames, many HUD icons);
load-on-demand as plain `Texture`s art where only one of many variants
is ever shown at once** - already this project's own convention for
tileable backgrounds (`blue_nebula.png`, `menu_starfield.png`), just
not one that had been articulated as a *general* rule before, and
initially missed when this screen reused the "always atlas-pack new
art" habit without checking whether it actually applied. Fixed by
dropping the `after_death` atlas-pack entirely (`AtlasPacker` now just
documents why it's skipped) and copying the raw files loose into
`assets/textures/after_death/`; `DeathScreen` loads only
`Dialog_Background.png` and whichever single `Quote_<n>.png` was dealt,
as plain `Texture`s - at most ~2MB decoded per death instead of ~67MB.

**Addendum (2026-09-08): holding TAB here now shows the local player's
own scoreboard row.** Full writeup (including the related kills/deaths-
persistence fix that made the numbers trustworthy at this exact moment)
is in 2.11's own addendum. Short version: `Client` hands `DeathScreen` a
one-shot `PlayerScoreEntry` snapshot of the player's own stats at the
constructor, and `DeathScreen` reuses `ScoreboardHud` (2.11) to draw it
- just the one row, since this screen has no live server connection of
its own to ask about anyone else's.

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
- **R** — trigger the active radar pulse (2.14), if this ship type has one
  and it's off cooldown
- **Power distribution (2.2):** **I / J / L**, chosen because they form a
  triangle under the right hand (resting comfortably while the left hand
  stays on WASD), with **K** (the natural center of the triangle) as the
  reset key. Final mapping: `J` = Shields, `I` = Weapons, `L` = Engines —
  chosen so the physical left-to-right order of `J`/`L` on the home row
  matches the HUD's left-to-right Shields/.../Engines bar order (2.8),
  with `I` (the one key not on that row) taking the remaining middle bar,
  Weapons. A **tap** shifts the split by one increment; **holding** a key
  for a moment instead jumps that system straight to its maximum (2.8).

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
      `WorldSnapshotMessage`s; clients send `PlayerInputMessage`s. Other
      players' ships render from received snapshots (interpolated); the
      local player's own ship is now client-predicted (see the next
      item) rather than snapshot-only. Verified with a real server + two
      real client processes on one machine. Still open: tick/snapshot
      rate tuning (3.5).
- [x] **Account system (server, 2026-09-06)** — see 3.6: JSON-file-backed
      `PlayerAccount` store, auto-register-or-validate-on-connect flow,
      salted SHA-256 password hashing, split from ship spawning into a
      separate `SpawnRequest`. XP is now actually earned via kills — see
      2.10 — though there's still no UI surfacing it to the player.
- [x] **Client local config, connection half (2026-09-06)** — see 3.7:
      load/save the four connect fields to a local JSON file. Keybinds
      (3.8) remain unbuilt - no Keybind Setup screen yet, so there's
      nothing to persist there yet.
- [x] **Connect Dialog screen (2026-09-06)** — see 5.1: host/display
      name/login/password fields, prefilled from local config, error
      display on failed auth, full keyboard navigation (TAB/Shift+TAB/
      ENTER). First real use of VisUI (4.4).
- [ ] **Keybind Setup screen** — press-to-bind capture, localized key-label
      display (see 3.8 implementation note), persists to local config.
- [x] **jgitver wired in (2026-09-07)** — `.mvn/extensions.xml` +
      placeholder `<version>0</version>` in every pom.xml; `v0.0.1` tagged
      to see it compute a real version end to end. See 3.10 for the full
      plan.
- [x] **Client/server version check (2026-09-07)** — `AppVersion` bakes
      the jgitver-computed version into both jars; `HandshakeRequest`
      carries it; `NetworkServer` rejects a mismatch before the account
      lookup runs, reusing the Connect Dialog's existing error display.
      See 3.10.
- [x] **Client packaging + `update.cmd` (2026-09-07)** — `jpackage
      --type app-image` (native `StarWars.exe` + jlink runtime, no
      committed `jre/`) via `lwjgl3`'s new `release-client` Maven profile,
      zipped as `StarWars-Client.zip`; `update.cmd` self-updates a local
      install without touching `connection-config.json`. `update.cmd`'s
      self-relaunch and download-failure paths both exercised for real;
      its actual successful-swap path remains unverified (needs an
      existing install to update). See 3.11.
- [x] **Tag-triggered GitHub Actions release workflow (2026-09-07)** —
      `.github/workflows/release-client.yml` runs the `release-client`
      profile and publishes `StarWars-Client.zip` to a GitHub Release on
      `v*` tag push. **Confirmed working end to end with a real release,
      `v0.0.2` (2026-09-07)**: tagged, pushed, workflow built and
      published a real non-prerelease asset in under two minutes, and the
      user downloaded, unzipped, and ran it successfully. Repo is still
      private, so this only worked for the authenticated repo owner —
      not yet reachable by anyone else (3.11's "known limitation").
- [x] **Server Docker image + QNAP deployment plumbing (2026-09-07)** —
      `server/Dockerfile` (multi-stage, `eclipse-temurin:25-jre` runtime,
      not `-alpine`), `.github/workflows/release-server.yml` (pushes to
      GHCR on `v*` tag push), `deploy/docker-compose.yml` (the Container
      Station "Application" YAML, pinned image tag, bind-mounted account
      data). Built and run locally via Podman - starts cleanly, both
      ports bind, no missing-native-library errors. **Not yet deployed to
      the real NAS or verified over a real network** - see 3.12's "not
      yet done" list and the TODO given to the user directly for the
      NAS/router-side steps.
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
      too sluggish, then retuned again (2026-09-05) once the
      fixed-timestep force-halving bug (3.5) was fixed and the real
      (correctly-applied) speed/turn rate still felt too slow for the
      larger 1920×1080 view (4.1) — more world visible at once makes a
      given absolute speed read as slower. Settled, after some further
      hands-on tinkering by the user, at **thrust 200N / torque 150 N·m**
      (`ShipStats.XWING`) for an agile feel — not derived from any
      formula, just tuned by feel. Box2D's linear damping model means
      top speed/turn rate scale directly with thrust/torque at fixed
      damping, which made the earlier "just double it" step predictable,
      but the final numbers are empirical, not calculated.
- [x] **Power distribution system (2026-09-06)** — see 2.8 for the full
      writeup: server-authoritative allocation state per ship (not
      networked to other clients, by construction rather than by
      filtering), feeding shield regen rate / weapon fire rate / engine
      thrust & agility; the three-keybind +5/-2.5/-2.5 adjustment with the
      10% floor/redirect/no-op algorithm (2.2), and reset-to-even logic.
- [x] **Weapon capacitor (2026-09-06)** — see 2.8: per-ship energy buffer
      (2.2) that trickle-charges from the power core and drains per shot;
      recharge rate scales with Weapons power allocation. All untuned
      placeholder numbers pending a real balancing pass.
- [x] **Combat-lock ESC logic (2026-09-06)** — see 2.3 for the full
      writeup: server tracks last-fired/last-hit timestamps per player,
      gates ESC-triggered leave on the 20s rule, self-destructs (no
      respawn timer) on a granted leave, shows a real warning banner
      (real art, same day) centered near the top of the screen. **Not
      done:** a real explosion VFX (neither death path has one yet, so
      "visually indistinguishable" holds trivially for now) and the
      warning's sound effect (no audio asset exists yet) — the banner
      itself works, just silently.
- [x] **Weapons & projectiles (first pass, 2026-09-05; real capacitor
      added 2026-09-06)** — see 2.4/2.8 for the full writeup: one weapon
      (blaster), server-simulated projectiles (never predicted), Box2D-
      contact hit detection, health/death/respawn, real weapon capacitor
      (2.2) gating fire rate alongside the mechanical cooldown. No kill
      credit/XP, no ship roster/balance pass — a working first cut, not
      a finished combat system.
- [x] **Turret weapons (Falcon/Star Destroyer only, 2026-09-06)** — see
      2.9 for the full writeup: player-toggled (**T**) autonomous
      per-mount scan/track/lead/fire AI, server-authoritative, sharing
      the main gun's capacitor. All untuned placeholder numbers (except
      the user-specified 30m scan range) pending a real balancing pass.
- [ ] **Ship roster (data-driven)** — stats (mass, thrust, turn rate, hit
      points, weapon loadout) per ship, starting with a small roster (2–3
      ships) before expanding. **Groundwork laid 2026-09-05, all six ship
      types spawnable as of the same day (2.7):** `ShipType` now has all
      six values; each has a `.stats.json` and can actually be selected
      and flown. Still genuinely open: every ship currently shares the
      exact same performance numbers (copied from the X-wing) — no real
      per-ship balance pass has happened yet.
- [x] **Ship Selection screen (first pass, 2026-09-05; fully wired
      2026-09-05, same day)** — see 5.1/2.7 for the full writeup: cycle
      every ship type, Start to match, flying the ship actually selected
      (not always the X-wing as in the original first pass). No
      XP-gating yet (no XP system exists, design.md 6 below); shows all
      six known ship types unconditionally.
- [x] **Client-side prediction & reconciliation (2026-09-05)** — see 3.5
      for the full writeup (local Box2D body, `ShipControlSystem.applyInput`
      shared with the server, blend/snap reconciliation against
      `WorldSnapshotMessage`, no sequence-numbered replay).
- [ ] **Match/arena flow** — single continuous deathmatch arena for v1
      (join → spawn → fight → respawn on death); no lobby/matchmaking yet.
- [ ] **Camera system** — speed-linked zoom and inertia/lag-behind
      follow behavior (4.1).
- [ ] **Parallax starfield background** — at least 2 layers (4.2).
- [ ] **Ship sprite rendering** — import sprite sets from
      `R:\StarWars\sprites` into `assets/`; render the neutral-bank
      (`_0020`) frame per ship for v1, plus the static turret overlay
      for turret-equipped ships (4.3).
- [x] **UI framework integration, partial (2026-09-06)** — VisUI added
      on top of Scene2D (4.4), Connect Dialog built against it (5.1).
      Ship Selection deliberately stays plain `SpriteBatch` (no form
      widgets needed there, see its own class Javadoc); Keybind Setup
      isn't built yet.
- [x] **HUD (hull/shield status widget, first pass, 2026-09-05)** — see
      2.6 for the full writeup: a corner widget showing the local player's
      current hull and shield as clipped "fuel gauge" overlays. Still
      open: target/radar or minimap, kill feed, scoreboard. (No
      power-distribution readout for *other* players — that's
      intentionally hidden, see 2.2.)
- [x] **Death Screen (2026-09-06)** — see 5.1: 23 user-authored quote
      images (6.1's content pool, now filled), `QuoteDeck` shuffle-bag so
      no repeat until all 23 have been shown, ESC-to-continue (the
      delivered art's own baked-in instruction, superseding this
      checklist's original ENTER assumption).
- [x] **Kill XP (2026-09-07)** — see 2.10: tier-weighted
      `base * tierMultiplier * rankDisparityMultiplier` formula
      (`KillXp`), awarded via `AccountStore#addXp` on a confirmed kill.
- [x] **Ship unlocks + Ship Tree (2026-09-08)** — see 2.12/2.13: ships
      cost tiered XP to unlock (Snowspeeder always free), affordability
      is derived XP minus already-spent cost, and unlocking is further
      gated by a fixed Imperial/Rebel branch order — all enforced
      server-side, not just client UI. **Still open:** nothing on Ship
      Selection prints the player's raw XP number or exactly how much
      more a locked ship needs — only the TAB scoreboard (2.11) shows a
      live XP figure, and only mid-match, not on this screen.

### 6.1 Content TODO: death screen quotes

**Filled 2026-09-06** — the user authored 23 complete (quote + matching
image) cards directly as finished art (`assets-raw/after_death/Quote_1.png`
through `Quote_23.png`), rather than this doc tracking quote text/image
pairings separately. See 5.1 for the Death Screen implementation that
consumes them.

## 7. Open design questions

Track unresolved decisions here so they don't get lost. Move an item into
the relevant section above once decided.

- **Ship roster balance**: every ship still shares the X-wing's exact
  thrust/torque/hull/shield numbers (2.7) — no real per-ship balance
  pass has happened yet, just the tiered unlock costs (2.12) and the
  branch order (2.13).
- **~~Ship Tree~~ — built, see 2.13.** (Was: idea floated 2026-09-06,
  not implemented — a branching Imperial/Rebel unlock order. Now real:
  TIE Fighter → TIE Interceptor → Star Destroyer, A-Wing → X-wing →
  Falcon, enforced both client- and server-side.) **Still genuinely
  open, not addressed by 2.13:** a Snowspeeder pilot unlocking ships on
  *both* branches is explicitly allowed (nothing stops it, nor was it
  asked to) — no faction-exclusivity decision has actually been made,
  this is the current behavior by default rather than a deliberate
  choice either way.
- **Map/arena design**: single arena to start — size, obstacles (asteroid
  fields? capital ship hulls?), boundary handling (do you die if you fly off
  the edge, or is it wrapped/bounded?).
- **Tick rate / snapshot rate** for the netcode.
- **Lag compensation** for hit detection (rewind-time hit registration vs.
  simple current-state checks) — matters more as ping increases.
