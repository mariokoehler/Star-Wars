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
  red, every other player's shots draw blue (`red_dot.png`/`blue_dot.png`,
  provided directly by the user — tiny 7×7 tileable-atlas sprites, no
  license info attached, presumed original/custom art). Purely a
  client-side rendering choice — the server treats every projectile
  identically regardless of owner.
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

**First real usage (2026-09-05):** `ShipSpriteMetadata`/`PixelPoint` (2.5)
— none of the systems above (accounts, connection config, keybinds) are
built yet, so this was Jackson's first actual exercise in the codebase.
Confirmed the bean-style convention (public no-arg constructor + getters/
setters, as opposed to the immutable-final-field style used for Kryo
network message classes) round-trips cleanly, including a `Map<String,
List<PixelPoint>>` value.

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

**Ship Selection screen — implemented 2026-09-05 (first pass, no Connect
Dialog yet).** Since accounts/Connect Dialog (3.6/5.1 above) don't exist
yet, the app currently *starts* on Ship Selection rather than reaching it
via a successful connect — `StarWarsGame.create()` goes straight there.
Revisit once the Connect Dialog is built.

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
- [ ] **Combat-lock ESC logic** — server tracks last-fired/last-hit
      timestamps per player, gates ESC-triggered leave on the 20s rule,
      triggers the self-destruct/explode VFX + blocked-ESC warning
      message/sound on leave attempts.
- [x] **Weapons & projectiles (first pass, 2026-09-05; real capacitor
      added 2026-09-06)** — see 2.4/2.8 for the full writeup: one weapon
      (blaster), server-simulated projectiles (never predicted), Box2D-
      contact hit detection, health/death/respawn, real weapon capacitor
      (2.2) gating fire rate alongside the mechanical cooldown. No kill
      credit/XP, no ship roster/balance pass — a working first cut, not
      a finished combat system.
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
- [ ] **UI framework integration** — add VisUI on top of Scene2D (4.4);
      build out the Connect Dialog, Keybind Setup, and Ship Selection
      screens against it.
- [x] **HUD (hull/shield status widget, first pass, 2026-09-05)** — see
      2.6 for the full writeup: a corner widget showing the local player's
      current hull and shield as clipped "fuel gauge" overlays. Still
      open: target/radar or minimap, kill feed, scoreboard. (No
      power-distribution readout for *other* players — that's
      intentionally hidden, see 2.2.)
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
