# Star Wars — Design Document

This is a living document describing what the game is, the architecture,
and current/open work. It describes the game **as it currently works** —
build narrative, bug-hunt stories, and verification logs belong in git
history/commit messages, not here. Update it the same session a decision is
made. Section numbers are stable cross-reference anchors (including from
`CLAUDE.md`) — don't renumber when editing.

## 1. Concept

An online multiplayer, top-down deathmatch shooter set in the Star Wars
universe. Players fly iconic starships against each other with a Newtonian
flight model (momentum/inertia-based, not arcade instant-turn-and-stop). Up
to 8 players per match. XP-based progression unlocks additional starships.

- **Genre:** top-down arena shooter / deathmatch. **Players:** 2–8/match.
- **Flight model:** Newtonian (thrust, inertia, angular momentum).
- **Progression:** earn XP from kills → unlock more starships. No other
  meta-progression (no skill trees, no cosmetics).
- **Session shape:** dedicated server on a public IP/hostname; players
  download a client and connect directly. No matchmaking/server browser.
- **Players are international** (UK, Belgium, Norway) — non-US/UK keyboard
  layouts (e.g. Belgian AZERTY) are a real consideration, not an edge case
  (see 3.8/5.2).
- **Fan project note:** uses Star Wars ships/characters/quotes/likenesses
  purely as a non-commercial personal project.

## 2. Core gameplay mechanics

### 2.1 Newtonian flight

Box2D-driven (3.3): apply force/torque for thrusters, let Box2D integrate
velocity/angular velocity and handle collisions. Default controls in 5.4.

### 2.2 Power distribution

Each ship has a power core split between three systems — **Shields**,
**Weapons**, **Engines** — as a continuous throughput split (percentages of
current output, not a stored battery), reallocated in real time by the
player:

- **Shields** — faster shield regen. **Weapons** — faster capacitor
  recharge (higher sustainable fire rate). **Engines** — more thrust and
  turn rate.
- **Baseline:** even split, ~33.3% each. **Floor:** no system below 10%.
- **Adjustment** (one keybind per system, default I/J/L, see 5.4): +5% to
  the target, −2.5% from each of the other two (net zero). Clamping
  algorithm per keypress, target `A`, others `B`/`C`:
  1. **Normal:** if both `B−2.5%` and `C−2.5%` stay ≥10%, apply that.
  2. **Redirect:** if exactly one of `B`/`C` would drop below 10% but the
     other can absorb the full −5% and stay ≥10%, take the whole 5% from
     that one instead.
  3. **No-op:** if neither split keeps every system ≥10%, nothing happens.
  Repeated presses from baseline converge the dominant system to ~78.3%
  (comfortably under an implicit "never above 80%" ballpark).
- **Reset keybind** (default K) returns to the even baseline.
- **Multiplier formula:** `multiplier = fraction / (1/3)` — linear relative
  to the even baseline, identical for all three systems. 1.0× at baseline,
  ~0.3× at the 10% floor, ~2.35× at the ~78.3% ceiling.
- **Hold-to-maximize:** holding a key ≥0.4s jumps that system straight to
  80%/others to 10% (`PowerDistribution.maximize`), instead of only
  incrementing per tap.
- **Non-linear engine→torque curve (Snowspeeder only):**
  `torqueMultiplier = enginesMultiplier ^ engineTurnResponseExponent`
  (per-ship-type `.stats.json` field, `1.0` = plain linear for every ship
  except the Snowspeeder at `0.5`) — compresses both extremes symmetrically
  while staying exactly 1.0 at baseline, so no other ship's handling
  changed. Thrust is unaffected, still plain linear.
- **Weapon capacitor:** a per-ship energy buffer (~5.5 shots at full
  charge) trickle-charged from the power core (rate scaled by the Weapons
  multiplier), drained one shot's cost per firing volley. The mechanical
  cooldown (2.4) is a hard cap "on top of" the capacitor, not replaced by
  it.
- **Server-authoritative, never networked to other clients — by
  construction, not filtering.** The owning client mirrors it locally by
  applying the identical deterministic `adjust`/`maximize`/`reset`
  transition to every keypress it also sends (over reliable TCP) — both
  sides running the same pure function over the same in-order event stream
  means the two can't diverge, no reconciliation needed. An opponent's
  power split is never visible to anyone but themselves.

`core.sim.PowerDistribution` (immutable) + `PowerSystem` enum implement
this; pure, unit-tested.

### 2.3 Leaving a match (ESC) and the combat lock

**ESC** returns to Ship Selection — but only while **not in combat**:
`CombatTimerComponent` (server) tracks seconds-since-last-fired/last-hit
per ship; blocked if either is <20s ago. A blocked attempt shows a warning
banner ("EJECTION LOCKED / Combat systems engaged", red/amber HUD art,
centered near the *top* of the screen — deliberately clear of the player's
own screen-centered ship) — **still no accompanying sound effect** (no
audio asset for this exists).

A granted leave makes the ship explode (visually indistinguishable from a
combat death — both send the same `ShipDestroyedMessage`) but skips the
respawn timer and awards no kill credit/XP to anyone. Server-authoritative
(`GameNetworkServer.handleLeaveMatchRequest`), via reliable
`LeaveMatchRequest`/`LeaveMatchDeniedMessage`.

### 2.4 Weapons & combat

**Weapon:** one type, a blaster cannon (`WeaponStats.BLASTER`) — fires
along the ship's current facing while SPACE is held, gated by a fixed
0.25s mechanical cooldown (4 shots/sec hard cap) **and** the weapon
capacitor (2.2/2.8).

**Projectiles** are small, fast (bullet/CCD) Box2D bodies, server-simulated
and broadcast every tick, inheriting the firing ship's own current velocity
on top of muzzle speed (a shot fired from a moving platform keeps that
platform's velocity, matching the Newtonian model). **The local player's
own shots are additionally client-predicted** (`Client#predictLocalWeapon`/
`spawnPredictedProjectile`, mirroring `WeaponSystem`'s exact
cooldown/capacitor/attachment-point logic) and reconciled with the
confirmed server projectile once it arrives (matched by spawn position, not
current extrapolated position, to stay latency-independent) — closes the
otherwise-visible round-trip lag between the ship (always predicted, "now")
and its own shots (previously snapshot-only). Other players' shots are
still pure snapshot dead-reckoning, never predicted. Local shots draw red,
everyone else's blue (7×10 oval sprites, authored nose-up, rotated to true
*travel* direction via velocity, not fired angle — the two can differ for a
moving shooter). No destroyed-notification for a projectile; a client
infers it's gone by absence from the next snapshot.

**Hit detection:** server-authoritative Box2D `ContactListener`
(projectile↔ship only, filtered via `CollisionCategories`); a shot never
damages its own owner (also excluded from physical collision entirely via
`ContactFilter`, not just a damage skip). Fixed 10 damage/hit
(`WeaponStats.BLASTER`), split via `ShipDamage.apply` (2.6). At zero
health: `ShipDestroyedMessage` broadcasts, a 3s server timer respawns the
ship at a real spawn point (2.16) with a fresh `ShipSpawnedMessage`.

### 2.5 Ship sprite metadata: polygon hitboxes & attachment points

Per-ship authored data (`dev-tools`' Swing sprite metadata editor,
`assets/shipdata/<name>.meta.json`): a convex hitbox polygon (≤8 points,
Box2D `PolygonShape`, falls back to a plain circle if unauthored) and named
attachment points (`Map<String, List<PixelPoint>>` — a list per name, so
e.g. an X-wing's 2 wingtip cannons are both `PROJECTILE`). Point convention:
sprite-local, origin at image center, **Y-up** (matches the game world, not
AWT/Swing) — divides directly by `PhysicsConstants.PIXELS_PER_METER`
(or a ship's own `pixelsPerMeter`, 2.7) into Box2D body-local coordinates.

Standard attachment names: `PROJECTILE` (weapon spawn points, `WeaponSystem`
fires one shot per point), `ENGINE`, `LIGHT_RED`/`LIGHT_GREEN`,
`DAMAGE_SMOKE`, `TURRET` (2.9) — all consumed generically by iterating the
map, no per-name special-casing except where noted. `core.sim.metadata`
(`PixelPoint`, `ShipSpriteMetadata`, `ShipSpriteMetadataLoader`) is shared
by the editor and the game; loading is classpath-based, works identically
for the editor (no libGDX), the client, and the headless server.

### 2.6 Shield & hull damage model, ship type config, and the status HUD

A regenerating **shield** in front of a non-regenerating **hull**. Damage
split (`ShipDamage.apply`, pure/tested): the shield absorbs a share of each
hit equal to its *current fraction of capacity* (100% shield → full hit
absorbed; 90% shield → 90/10 split; 0% shield → hull takes it all). If the
shield's designated share exceeds what's actually left of it, the excess
bleeds through to hull too (never silently absorbed for free).
`ShipDamage.applyChunked(shield, hull, totalDamage, chunkCount)` splits a
hit into `chunkCount` equal sub-hits applied in sequence, each re-reading
the shield's current fraction — used for missiles (2.15) specifically so a
single lump-sum hit can't be entirely swallowed by a full shield; a
lump-sum 100 damage into 100/100 leaves hull untouched, the same 100 as 10
chunks leaves ~65 hull damage through.

Shield regen: flat per-second rate (`ShieldComponent`), scaled by the
Shields power multiplier (2.2), no regen-delay-after-hit.

Every `ShipType` has a required `shipdata/<name>.stats.json`
(`ShipTypeConfig`) — radius, thrust, torque, hull/shield max, shield
recharge rate, HUD clip pixel range — loaded via `ShipStats`. HUD widget
(`ShipStatusHud`): background panel, then a clipped shield ring, then a
clipped hull silhouette (a bottom-anchored "fuel gauge" — at 100% the full
authored pixel range renders, below that only the bottom portion, shrinking
from the top down). Pure clip math in `HudGaugeClip`, tested. Bottom-left
HUD row, local player's own hull/shield only (broadcast for every ship,
rendered for none but the local one). Falls back to the X-wing's hull art
for any ship type without its own `textures/hud/<name>_hull.png`.

### 2.7 Multi-ship-type spawning

All 7 `ShipType`s are spawnable/flyable, each with real thrust/torque/hull/
shield (currently identical numbers across all 7, copied from the X-wing —
no per-ship balance pass has happened). Each has its own
`pixelsPerMeter` (`ShipTypeConfig`), the conversion rate between that
ship's sprite-space pixel coordinates (hitbox, attachment points) *and*
its source art's resolution — this is also what derives its rendered size
(`Client` computes it every frame from the loaded `TextureRegion`'s real
pixel dimensions ÷ `pixelsPerMeter`), so visual size and physical hitbox
size can't drift apart. Real-world sizes: X-wing/TIE Fighter/A-Wing/TIE
Interceptor 4×4m, Snowspeeder 3×3m (75% scale, deliberately the smallest),
Falcon 6.4×6.4m, Star Destroyer 8×13.5m (non-square, 256×432 source art).
Box2D derives mass from fixture area at a uniform density, so bigger ships
are naturally more sluggish with zero per-ship tuning — a deliberate,
accepted side effect, not something a balance pass needs to fight.

### 2.8 Power distribution & weapon capacitor — art/HUD

See 2.2 for the algorithm/multiplier formula. HUD: `PowerDistributionHud`,
one shared background panel + three vertical glow bars (Shields/Weapons/
Engines, left-to-right, matching the `J`/`I`/`L` keybind layout, 5.4), same
bottom-anchored clip technique as 2.6's hull/shield widget but one shared
clip range for every ship type. Each bar shows the raw power fraction
(never renormalized against the ~78.3% practical ceiling — a bar never
reads fully "full" even at max allocation, deliberately, so it doesn't lie
about the actual percentage).

### 2.9 Turret weapons (Falcon/Star Destroyer only)

A second, fully autonomous weapon system layered on the main gun, toggled
by the player (**T**) but scanning/tracking/leading/firing entirely on its
own once enabled — server-only, never client-predicted. Only ships with
`"TURRET"` attachment points have any (Falcon: 1 mount, Star Destroyer: 4,
each independently tracking). Per-mount behavior each tick: drop the
current target if it's no longer live or has left scan range (30m); if
none, scan for the closest live enemy in range; rotate toward a lead-solved
aim angle (`sim.TurretAiming`, a classic intercept quadratic — pure/tested)
at the configured turn rate; fire once aligned and off cooldown, draining
the **same shared capacitor** as the main gun (only `WeaponSystem`
recharges it, so a ship with both doesn't double-recharge). `ShipState`
broadcasts `turretAimAngles` (one per mount); the client draws each mount's
sprite at the *absolute* broadcast angle, independent of hull rotation.

### 2.10 Kill XP

`XP = BASE_XP(10) * tierMultiplier * rankDisparityMultiplier`. Every
`ShipType` has a tier 1–4 (Snowspeeder=1, then both faction branches climb
2→3→4 in lockstep — TIE Fighter/A-Wing=2, X-wing/TIE Interceptor=3, Star
Destroyer/Falcon=4). `tierMultiplier` = the **killed** ship's tier;
`rankDisparityMultiplier` = killed tier ÷ killer tier (rewards an underdog
kill, penalizes a lopsided one — a tier-1 downing a tier-4 earns 160 XP,
the reverse earns ~3). Only the killer is rewarded, no victim penalty.
Credited to whichever hit first tips a ship's hull to destroyed, even if
multiple hits land the same tick. A self-destruct leave (2.3) or an
environmental death (wall/asteroid/mine) awards nothing to anyone.
`core.sim.KillXp`, pure/tested.

### 2.11 Scoreboard overlay

Holding **TAB** shows every currently-connected player (not just those with
a live ship): display name, total account XP, lifetime kills/deaths — all
three **persisted on the account** (`PlayerAccount`), not session-only, so
they survive a death/reconnect/server restart. Broadcast via
`ScoreboardMessage`/`PlayerScoreEntry` over TCP once per second, plus once
immediately whenever a kill/death happens. Sorted by kills desc, then XP
desc, then name. The Death Screen (5.1) also shows the local player's own
row via a one-shot snapshot handed to it at death time.

### 2.12 Ship unlocks

Snowspeeder is free/unlocked for every account from creation. Every other
ship costs tiered XP to unlock: 1000 (tier 2), 1500 (tier 3), 2000 (tier
4) — `unlockCostXp` on `ShipTypeConfig`. **XP is never decremented** —
affordability is computed on the fly as `availableXp = totalXp − sum of
already-unlocked ships' costs` (`core.sim.ShipUnlocks`, pure/tested, used
identically by client padlock UI and server validation). Unlocked ships
persist as a `Set<ShipType>` on the account. Ship Selection padlocks: green
("SPACE to unlock", affordable), white ("not enough XP"). Server
re-validates every `UnlockShipRequest`/`SpawnRequest` — the client's own
UI gating is never trusted alone.

### 2.13 Ship Tree

Unlocking is also branch-gated, not just XP-gated: **Imperial** TIE
Fighter → TIE Interceptor → Star Destroyer; **Rebel** A-Wing → X-wing →
Falcon. Snowspeeder is outside both branches. A ship whose prerequisite
isn't met shows a third padlock ("TIER TOO HIGH"), overriding green/white
regardless of affordability. `core.sim.ShipTree` (pure/tested), enforced
both client-UI and server-side (`GameNetworkServer.handleUnlockShipRequest`).
Hovering a locked ship's portrait shows a tooltip naming the specific
blocker ("You need to unlock the A-Wing..." / "You're lacking 1000 XP...").
**Not exclusive** — nothing stops one account unlocking ships on both
branches.

### 2.14 Radar / minimap

Three independent per-ship detection mechanisms, individually enabled per
ship type and **tiered by `ShipType#getTier()`**: tier 1 (Snowspeeder) base
only; tier 2/3 base+cone; tier 4 all three.

1. **Base** — omnidirectional, 60m, always-on.
2. **Cone** — forward ±30°, 120m.
3. **Active pulse** — **R**, omnidirectional, 200m, 30s cooldown, active
   for 5s per trigger; while active it also makes the pulsing ship visible
   to *every* other player's radar regardless of range/equipment, for the
   same 5s window.

**Server decides, per observing player, which other ships they currently
know about** — `broadcastSnapshot()` builds a personalized `ShipState[]`
per player (own ship always + whatever their radar detects), sent
individually, not one shared broadcast — the only version consistent with
never trusting the client (a client-side-only fog-of-war would be trivially
bypassed). `sim.RadarDetection` (pure/tested) does the geometry.
**Projectiles are never radar-filtered** — broadcast to everyone
unfiltered, same as asteroids/power-ups/mines (a deliberate scope boundary,
not an oversight).

**Rendering** (`RadarHud`): north-up, fixed scope (only the forward cone
rotates with facing) — rings/cone are separate runtime-scaled/rotated
overlays, not baked into the background, since each ring's real-world
range can differ per ship type. The scope's outer edge = this ship type's
own largest enabled range (`ShipStats#getRadarMaxRangeMeters()`), so the
scale never visibly jumps when the pulse fires. A pulse-revealed contact
beyond the observer's own equipment clamps to the scope's edge at its true
bearing, drawn as a distinct chevron (not a dot) — direction only, no false
precision. Asteroids draw as blue blips (unfiltered, same treatment as
ship contacts). Arena boundary edges draw as amber lines
(`RadarScopeMath.computeBoundaryLine`, clipped to both range and the edge's
own extent — reduces to a plain scaled identity since the scope never
rotates, so an axis-aligned world line stays axis-aligned on the scope). A
pulse-cooldown LED (green = pulse enabled and ready, red = disabled or on
cooldown) and the player's own rounded arena coordinates round out the
widget.

### 2.15 Missiles

X-wing and TIE Interceptor only, 2 missiles at spawn. **M** fires once a
**lock** is acquired: an enemy must stay inside the firing ship's own
forward cone-radar (2.14) for 5 uninterrupted seconds — leaving the cone at
any point (acquiring or already-acquired) drops the lock to nothing
immediately. Multiple enemies in the cone: locks onto whichever is closest
when acquisition starts, stays sticky even if another gets closer.
Acquiring a lock stops entirely once out of missiles (also doubles as the
only ammo-out indicator). A fired missile has 5s fuel (self-destructs
after), limited turning torque (plain pursuit toward the target's *current*
position, not a lead solve — `MissileGuidanceSystem`, a continuous-force
system run per-physics-step same as `ShipControlSystem`), 100 damage
(10× a blaster hit) applied via the same 10-step chunked mechanic as 2.6.
If the target dies mid-flight the missile flies straight until fuel runs
out — no retarget, no early self-destruct. Spawns at the firing ship's
*exact* center (no offset — the `ContactFilter` already prevents any
same-shooter collision regardless of spawn position) with every ship
rendered on top of its own missiles, so a launch reads as emerging from
underneath the hull.

`ProjectileComponent.trackedTargetPlayerId` (sentinel `NO_TRACKED_TARGET`
for an ordinary bolt) is the only thing distinguishing a missile from a
blaster bolt, wire and component alike — reused for hit resolution,
sprite selection, and reticle logic with no separate type enum.

**Lock reticle** — three additive animated stages (outer pulse, inner
rotation, static center mark), drawn **both attacker-side** (anchored to
the target) **and victim-side** (anchored to the local player's own ship,
whenever any enemy currently has them locked — `ShipState`'s
`targetedByMissileLock`/`targetedByMissileLockAcquired`, aggregated across
every attacker). No tweening library — both animations are one-line
`MathUtils` formulas.

### 2.16 Arena bounds

A fixed **500m × 500m square**, centered on the origin, that ships
**bounce off** (a single static `ChainShape` around the perimeter, shared
code between server and client-prediction worlds so a bounce can't diverge
and fight reconciliation). Restitution `0.6`, near-zero friction (a clean
reflection, not induced spin). `CollisionCategories.ARENA_BOUNDARY` masks
only into ship fixtures (and, since 2.18/2.19, power-up/mine — see those
sections) — a projectile/missile just flies past and expires on its own
lifetime.

**Wall-impact damage** (`ArenaBounds.wallImpactDamage`): 0 below 20 m/s,
1.5 damage/m/s above it, via the same `ShipDamage.apply` split every other
damage source uses. Never marks combat (`CombatTimerComponent`), never
credits a kill — an environmental death, same treatment asteroids/mines
get.

**Visual:** a tiled hazard-tape band around the perimeter
(`ArenaBoundaryRenderer`, thickness derived from the actual loaded
texture's own aspect ratio so a re-authored texture can never silently
stretch). Also drawn on the radar (2.14).

**Spawn/respawn:** `core.sim.SpawnPointFinder.findSpawnPoint` — a random
point ≥20m (`BOUNDARY_MARGIN_METERS`) inside the edge and ≥100m
(`MIN_ENEMY_DISTANCE_METERS`) from every currently-alive enemy ship (up to
50 random attempts, falling back to the least-bad candidate tried if none
succeed — a ship must spawn *somewhere* right now).
`isFarEnoughFromEnemies` lets a caller (asteroids/power-ups/mines) reject
that fallback outright instead, since those can simply retry later.

### 2.17 Asteroids

Exactly 4 always active, drawn from 8 textures (never two active sharing
one), each `AsteroidType`'s `pixelsPerMeter` sized so its long axis reads
as ~20m regardless of source resolution. Spawn at a random point ≥100m
from every player (rejecting `SpawnPointFinder`'s fallback, retried next
tick if short), random slow velocity (3–8 m/s) and spin (±0.3 rad/s), then
**no drag at all** — keeps exact initial speed/spin forever. Never interact
with each other or the boundary; one that drifts outside the arena
despawns and a fresh one spawns elsewhere. Indestructible — a
projectile/missile hitting one is destroyed on impact, dealing/taking no
damage.

Density `3f` (~30–60× a ship's mass, given the area ratio — heavy enough
to feel solid, light enough to still read as a two-body collision, not a
wall), restitution `0.1f` (a visible but soft bounce). Ship-vs-asteroid
damage reuses `wallImpactDamage`'s formula but on the ship's speed
*relative to the asteroid's own* (an asteroid is itself moving — matching
its velocity and grazing gently shouldn't read as a fast wall hit).
`CollisionCategories.ASTEROID` masks into ship/projectile fixtures only.

Client-side local-prediction mirror: a `KinematicBody` per asteroid in the
client's own `localWorld`, hard-synced to the server's reported
transform/velocity every snapshot (not simulated) — without it, the
locally-predicted ship would fly straight through an asteroid the server
is actually bouncing it off.

### 2.18 Power-ups

4 always active in the arena. Touching one applies its effect and it
respawns elsewhere; very light (a stray/deliberate shot visibly shoves
it), collides with the boundary/asteroids/projectiles/missiles/mines but
**not ships directly**, real bouncy (restitution `0.85`), no drag, never
despawns on its own.

**Effects:** REPAIR (heals 50% of max hull, capped at full), BOOST
(doubles power generation for 15s, see below), MISSILE (+1 missile, no-op
on a non-missile-capable ship), BOMB (spawns a mine, 2.19, capped at 10
active).

**Ship-detection: two Box2D fixtures on one body** (`PowerUpFactory`) — a
real physical fixture (mass, restitution, collides with boundary/asteroid/
projectile/missile/mine, mask excludes `SHIP`) plus a sensor fixture
(`isSensor=true`, mask = `SHIP` only) purely for pickup detection. A sensor
generates contact events with zero physical response regardless of
restitution — "detect without physically shoving" a body deliberately kept
too light to bump into safely. Hitbox: circle, radius 59px/64px-per-meter
(118×118px spec). Density `0.05` (far lighter than an asteroid); a
missile's impulse is clamped to 30 m/s per tick (`clampPowerUpSpeed`) plus
an out-of-bounds despawn/respawn safety net, since unclamped it would
tunnel past the boundary.

**BOOST mechanically:** `PowerBoostComponent` (every ship) —
`activate(15f)` on pickup, refreshing not stacking; a flat ×2 multiplier
stacked on top of whatever `PowerDistribution.multiplierFor(...)` already
computes for a system (thrust/torque, shield regen, capacitor recharge —
not turret recharge, since only `WeaponSystem` recharges it). Broadcast
per-ship (`ShipState.getPowerGenerationMultiplier()`), server-decided so
the client just trusts the latest snapshot.

**Server field maintenance:** same shape as asteroids — `powerUpsById`/
`trySpawnPowerUp()` (reuses `SpawnPointFinder` unchanged, same ≥20m/≥100m
rule), one attempt per tick while under 4.

### 2.19 Mines

Spawn **exclusively** as a BOMB power-up pickup, never spontaneously,
capped at 10 active at once (a pickup past the cap does nothing further,
still consumed/respawns as usual). Permanently stationary and non-rotating
once spawned — a `BodyType.StaticBody` (immovable and non-rotating by
Box2D's own definition, stronger than `fixedRotation` on a dynamic body).
The one fixture is a sensor (same "detect without physically shoving"
reasoning as a power-up's own sensor, 2.18) — detonates the instant
anything touches it: missile-tier damage (`MissileStats.INSTANCE`'s
damage/chunk count, 2.6/2.15) via the same 10-step chunked mechanic, but
only to an actual ship among the touchers; a non-ship toucher (asteroid,
power-up) just triggers the detonation with no effect on itself; a
projectile/missile among the touchers is destroyed too, consistent with
every other solid obstacle. Treated as an environmental hazard, not
combat — no kill credit, no `CombatTimerComponent` mark (no tracked
owner exists to credit even if it wanted to). Reuses the existing
ship-destruction explosion particle + sound.

`CollisionCategories.MINE` masks into ship/projectile/asteroid/power-up
fixtures (both directions) but deliberately **not** the arena boundary — a
mine spawns inside the boundary margin and never moves, so that pairing
can never fire.

**Spawn point:** same rule as asteroids/power-ups — ≥20m boundary/≥100m
enemy distance, rejecting the fallback. Since a mine spawn is a one-shot
event tied to a specific pickup (not a continuously-maintained field like
asteroids/power-ups), it needs its own retry mechanism:
`pendingMineSpawns` (a plain int, reserved immediately at pickup time so
the 10-mine cap correctly counts active+pending) drained one attempt per
tick by `tickMines()`/`trySpawnMine()` until it succeeds.

**Client:** 64-frame baked rotation animation (`mine_0000.png`..
`mine_0063.png`, 32px/m, packed into one indexed atlas region), played via
a `com.badlogic.gdx.graphics.g2d.Animation<TextureRegion>` — one shared
clock for every active mine, not independently phased. `RemoteMine` needs
no dead reckoning or local-prediction mirror at all — it never moves.
`MineDetonatedMessage` (x/y only) triggers a dedicated explosion pool
reusing `GameAssets.EXPLOSION_PARTICLE`/`EXPLOSION_SOUND`.

## 3. Architecture

### 3.1 High-level shape

Client/server, **authoritative dedicated server**. The server owns
simulation truth (positions, hits, deaths, XP); clients render, collect
input, and predict their own ship locally for responsiveness, reconciling
against server state as it arrives. Required for a competitive shooter and
is what makes the combat-lock rule (2.3) trustworthy.

### 3.2 Modules (Maven)

- `core` — shared network layer (message classes, `NetworkServer`,
  `NetworkClient`) used by both client and server, plus
  `de.mkoehler.starwars.sim` (Ashley/Box2D simulation). The full Ashley
  `Engine` runs only in `GameNetworkServer` (`server` module); the client
  uses one small slice directly with no Ashley involved — `ShipFactory
  .createBody` + `PhysicsSystem` + `ShipControlSystem.applyInput`, for its
  own client-side-predicted ship (and, since asteroids, a `localWorld`
  mirroring server-authoritative obstacles).
- `lwjgl3` — desktop client (rendering, input, audio, UI).
- `server` — dedicated server, `gdx-backend-headless`. `GameServer` wraps
  `GameNetworkServer`, calling `tick()` every `render()`; `ServerLauncher`
  is the process entry point. Needs `gdx-platform` *and*
  `gdx-box2d-platform` natives (CLAUDE.md).
- `dev-tools` — offline authoring tools, never shipped (the sprite
  metadata editor, 2.5). Depends on `core` for the shared JSON model; plain
  Swing/AWT, no libGDX.

No separate `network` module — shared wire DTOs live directly in `core`
under `de.mkoehler.starwars.net`.

### 3.3 Simulation stack

**Ashley** (ECS) + **Box2D** (2D rigid-body physics), both treated as
decided project dependencies. `PhysicsConstants.PIXELS_PER_METER = 32`
(global default; several entity types now carry their own `pixelsPerMeter`
instead, see 2.5/2.7). `PlayerInputSystem` was renamed `ShipControlSystem`
and reads a `NetworkInputComponent` (set from received network input) not
`Gdx.input` directly — headless-safe, hence server-side.

**Fixed-timestep rendering requires interpolation.** `PhysicsSystem` steps
Box2D at a fixed `PhysicsConstants.TIME_STEP` (1/60s) via an accumulator,
independent of render frame rate — drawing the raw just-stepped position
jitters (some frames get 0 steps, others 1–2). Fix: snapshot a pre-step
position/angle **immediately before every individual `world.step()`** (not
once per outer call — doing it once per call left a subtle jitter visible
only at speed, when a frame happened to batch two steps), interpolate
between that and the post-step state using `PhysicsSystem.getAlpha()`.
Used both for the client's own locally-predicted ship and (originally, now
superseded by server-side simulation) any local rendering.

### 3.4 Networking

[KryoNet fork](https://github.com/crykn/kryonet) —
`com.github.crykn:kryonet` via JitPack, pinned to 2.22.9. Gives a real
TCP (reliable) + UDP (unreliable) dual channel with automatic Kryo
serialization. `de.mkoehler.starwars.net.MessageRegistry` is the single
place both ends register wire classes with Kryo, in a fixed append-only
order (CLAUDE.md). `NetworkServer`/`NetworkClient` (both `core`, no
libGDX) wrap connection lifecycle, a handshake, and TCP+UDP ping/pong.

### 3.5 Netcode approach

- **Reliable (TCP):** login/account, join/leave, ship selection, spawn/
  despawn, death/kill events, one-shot actions (power adjust, turret
  toggle, radar pulse, missile fire, leave-match, unlock requests).
- **Unreliable (UDP):** per-tick `WorldSnapshotMessage` (position/velocity/
  rotation for ships/projectiles/asteroids/power-ups/mines).
- **Not networked at all:** a player's own power allocation (2.2).
- **Client-side prediction:** the client runs its own local Box2D body for
  its own ship, applying held input immediately via the exact static
  `ShipControlSystem.applyInput` the server also calls, so predicted and
  authoritative physics can only diverge from packet loss/timing drift, not
  disagreement about the rules.
- **Reconciliation — simplified, not full input-replay.** Compares the
  local body against the server's reported state for the local player's
  entry, **extrapolated forward by wall-clock time since the previous
  reconciliation using its own reported velocity** (a snapshot is never
  "now" — comparing a stale position to a live one manufactures a phantom
  error proportional to speed). Small error (≤3m): blend 20% toward the
  server's state. Large error (>3m): hard-snap position/angle/velocity.
  Sequence-numbered input-replay was considered and explicitly rejected as
  more complexity than this project needs.
- **Other players' ships and every projectile/asteroid/power-up — dead-
  reckoned** (`lastKnownPosition + velocity × timeSinceSnapshot`), never
  eased (an ease lags a fast target proportional to its speed — a 50m/s
  object can trail by several meters, misread as a wrong hitbox size).
  Mines need no dead reckoning at all (2.19, never move).
- **Radar personalizes the snapshot per player** (2.14) — no longer one
  shared broadcast; `broadcastSnapshot()` builds and sends each connected
  player their own `ShipState[]`. Projectiles/asteroids/power-ups/mines
  stay broadcast unfiltered to everyone (a deliberate scope boundary).
- Tick rate 30Hz (`NetworkConstants.SIMULATION_TICK_RATE_HZ`), physics
  steps at fixed 60Hz — **Box2D clears a body's applied forces after every
  individual step**, so continuous force/torque application must go
  through `PhysicsSystem.update(deltaTime, beforeEachStep)`'s per-step
  callback (CLAUDE.md), not be applied once per tick.
- Server logging: plain `Gdx.app.log(...)`, no separate logging dependency.
- **Known standing issue:** a large, intermittent, environment-level delay
  in raw socket connect/close on Windows — see CLAUDE.md's "Current
  status" entry. Worked around via `NetworkClient.stopAsync()` on every
  screen-transition teardown (never block the render/tick thread waiting
  on it); root cause unconfirmed.

### 3.6 Accounts & persistence (server-side)

Flat JSON file (`data/accounts.json`, no database). `PlayerAccount`:
`login` (unique id), `passwordHash` (SHA-256 + per-account random salt,
never the raw password), `displayName` (not required unique, overwritten
on every login — no separate "edit profile" flow), `xp`, `kills`, `deaths`
(all lifetime, persisted — not session state), `unlockedShips`
(`Set<ShipType>`, 2.12). Connecting with an unknown login auto-creates the
account; an existing login requires the password to match or the
connection is rejected with an on-screen error.

Login/spawn are two separate messages (`HandshakeRequest` then
`SpawnRequest`) specifically so validating a password never spawns a real
ship just to check it.

`server.accounts`: `PlayerAccount` (Jackson bean), `PasswordHasher`
(constant-time compare), `AccountStore` — no libGDX dependency (plain
`java.nio.file.Path`), directly unit-tested. **Persistence is async, not
write-through:** mutations only touch an in-memory map (`synchronized`);
a background `ScheduledExecutorService` flushes every 60s (skipped if
nothing changed), copying via a real **deep** copy (`PlayerAccount#copy()`
— a shallow copy would still alias the live, concurrently-mutable beans).
Rolling backups every 30th flush (~30 min), pruned to the newest 48. A JVM
shutdown hook does one final synchronous flush. Accepted trade-off: only a
hard kill (SIGKILL/crash) can lose up to ~60s of changes.

### 3.7 Client local config (connection)

The four Connect Dialog fields (host, display name, login, password)
persist to `connection-config.json` after a successful login and pre-fill
the dialog next launch. Raw password stored locally in plaintext — an
accepted trade-off for a single-purpose game login. No-saved-config
fallback host: the user's own dedicated server's public hostname (not
`localhost`), so a first-time player doesn't need to know the real address.

### 3.8 Client local config (keybinds)

Every gameplay action is fully remappable (5.2), persisted to
`keybindings.json`. **ESC (leave match) is the one hardcoded exception,**
never remappable. libGDX/GLFW reports keycodes by **physical key position
on a US reference layout**, not the character an actual OS layout
produces — capture (press-a-key-to-bind) works correctly for any layout
out of the box, but the *displayed* label needs resolving separately: a
`core.input.KeyLabelResolver`/`KeyLabels` seam (defaulting to
`Input.Keys.toString`) plus an `lwjgl3.LocalizedKeyLabelResolver` using
GLFW's `glfwGetKeyName` (registered by `Lwjgl3Launcher`) shows the real
OS-layout character (e.g. a German QWERTZ "Z" key correctly displays as
"Z", not the US-layout "Y" it physically maps to) — **only the label
changes, what's persisted is still the physical/US-layout keycode**.
Rebinding a key already bound to a different action **swaps** the two
rather than leaving one key bound to both.

`core.input.GameAction` (15 actions: thrust forward, turn left/right, fire
weapon, toggle turret, fire missile, radar pulse, power shields/weapons/
engines/reset, show scoreboard, show player names, zoom in/out).
`KeyBindings` (`EnumMap<GameAction, Integer>`), owned for the app's run by
`StarWarsGame`.

**Punctuation-key defaults need checking against the backend's own
mapping table** before being chosen: the lwjgl3 backend never emits
`Input.Keys.PLUS` at all (see `DefaultLwjgl3Input.getGdxKeyCode`), so it
would be a dead default. The zoom keys default to the **numpad** `+`/`-`
(`NUMPAD_ADD`/`NUMPAD_SUBTRACT`) for that reason plus a layout one: they
are the only keys physically labeled "+" and "-" on both a US and a German
layout, since the main-row "+"/"-" sit on entirely different physical keys
per layout (and the persisted keycode is always the physical/US one, per
the paragraph above). A player without a numpad rebinds them like any
other action.

### 3.9 JSON serialization

**Jackson** (`jackson-databind`, latest) for every JSON file: accounts
store (3.6), connection config (3.7), keybinds (3.8), audio settings
(3.16), camera settings (4.1), and `ShipSpriteMetadata`/ship stats JSON. One library everywhere;
plain bean-style classes (public no-arg constructor + getters/setters) —
distinct from the immutable-final-field style used for Kryo network
message classes.

### 3.10 Build/release versioning & client-server version check

**jgitver** computes `${project.version}` from git tags automatically —
no hand-edited version anywhere (CLAUDE.md has the exact mechanics/
gotchas). `AppVersion` (`core`) reads the baked-in version from a
resource-filtered properties file; `HandshakeRequest` carries it;
`NetworkServer` rejects a version mismatch before the account lookup ever
runs. Cutting a release is just `git tag vX.Y.Z` + push — nothing to edit.
**CI builds pass the version explicitly** (`-Djgitver.use-version`,
derived from the triggering tag) rather than relying on jgitver's own
auto-detection inside GitHub Actions, which was found to sometimes
miscompute a `-SNAPSHOT` even exactly on a clean tag.

### 3.11 Client packaging: a self-contained zip via jpackage

`jpackage --type app-image` (built into the JDK) produces a native
`StarWars.exe` + a jlinked trimmed runtime, built fresh at package time —
nothing committed to git for this. Layout: `StarWars.exe`, `app/`
(the jar), `runtime/` (jlinked JRE), `update.cmd` (self-updates in place,
never touches `connection-config.json`, stages the swap with an `.old`-
suffix rename so a failed download/extract rolls back rather than leaving
a half-replaced install; hits GitHub's fixed
`releases/latest/download/StarWars-Client.zip` URL, resolving only against
the newest non-prerelease). Built via `lwjgl3/pom.xml`'s opt-in
`release-client` Maven profile; a tag-triggered GitHub Actions workflow
(`release-client.yml`) publishes it to a GitHub Release automatically.
**Repo is currently private** — anonymous downloads (including
`update.cmd`'s own) 404 for anyone but the authenticated owner; the repo
needs to go public before this distribution mechanism works for anyone
else.

**Release sequencing matters:** the version check (3.10) is an exact-
string match, so redeploy the server (3.12) from a new tag *before or
alongside* publishing the matching client zip, never after.

### 3.12 Dedicated server deployment: Docker via QNAP Container Station

Runs on the user's QNAP NAS via Container Station (a GUI over real
Docker). GitHub Actions builds and pushes a Docker image to GHCR on every
`v*` tag push (`release-server.yml`, separate from the client workflow);
Container Station pulls it directly as a single-service Compose
"Application" (`deploy/docker-compose.yml`, pinned to an exact version tag,
never `latest` — the version check is exact-string). `server/Dockerfile`
is multi-stage: `maven:3.9-eclipse-temurin-25` build stage (build context
is the whole repo root — Maven needs to parse every module the root
`pom.xml` declares to build its reactor graph, even with `-pl`/`-am`
restricting what actually builds), `eclipse-temurin:25-jre` runtime stage
(not `-alpine` — libGDX/Box2D natives are glibc-built). Bind-mounts a real
NAS folder onto `/app/data` so account data survives a redeploy.

**Not yet done:** actually deploying to the real NAS is a manual
Container-Station/FritzBox-port-forwarding step outside this repo, tracked
with the user directly rather than here.

### 3.13 Embedded dev-only MCP server for remote-controlling the client

Only runs in a dev build, started via `Lwjgl3Launcher.main` with a
`--mcp` argument — absent from a normal player-facing launch. Uses
`io.modelcontextprotocol.sdk` (`mcp-core`+`mcp-json-jackson2`) over stdio
(newline-delimited JSON-RPC), so a running client process becomes an MCP
server Claude Code can spawn/attach to directly, instead of driving it via
OS-level `SendKeys`/screenshot automation (CLAUDE.md).

**Architecture:** `core.remote` (`RemoteControllable` interface a screen
implements; `RemoteControlRegistry` tracks whichever one is currently
showing; `RemoteControlQueue` bridges the MCP thread(s) to the render
thread, `submit(Callable)` returning a `CompletableFuture` the MCP thread
blocks on). `lwjgl3.mcp.McpBridge` builds the actual server, redirecting
`System.out` to `System.err` for everything except the JSON-RPC stream
itself (stdout must carry *only* JSON-RPC).

**Only `ConnectScreen` is remote-controllable so far** (`get_active_screen`,
`connect_screen_login`) — standing permission (CLAUDE.md) to extend the
same pattern to more screens/richer state whenever it would help
verification.

Registered as a project-scoped MCP server (`.mcp.json`, committed) via
`start_mcp_client.cmd`, which always reinstalls `core`/repackages `lwjgl3`
first (same jgitver reasoning as `start_client.cmd`). Requires a Claude
Code restart to pick up a new/changed `.mcp.json`.

### 3.14 Shared `AssetManager` + splash screen

`StarWarsGame` owns one `AssetManager` for the app's whole run
(`getAssets()`); a `SplashScreen` loads everything up front (logo shown
synchronously first, then `render.GameAssets.queueAll(...)` queues every
shared texture/atlas/sound, driven one step at a time via
`AssetManager.update()`) before handing off to `ConnectScreen`. Every
consumer screen/HUD widget reads already-resident assets instead of
constructing/disposing its own copies on every transition — this is what
actually used to cause a noticeable pause switching screens.

**Deliberately left outside the `AssetManager`:** `GameFonts`-generated
`BitmapFont`s (cheap to regenerate per call site), `PlaceholderStarfield`'s
procedural texture (not a file), `audio/StarWarsTheme.mp3`/the hangar
ambience track (`Music`, loaded once directly, not repeated-load
concerns).

### 3.15 Network diagnostics

`core.net.NetworkLogging` installs a custom minlog `Log.Logger` prefixing
every KryoNet log line with an absolute wall-clock timestamp (not
minlog's default time-since-process-start counter) so two independent
processes' logs can be lined up. `NetworkClient.connect()`/`stop()`,
`Client`/`ShipSelectionScreen`'s `show()`/`dispose()`, and
`Client.render()`/`GameNetworkServer.tick()`'s large-`deltaTime` stall
warnings are all permanent, cheap, always-on instrumentation — not
behind a flag — kept for any future networking-timing question, not just
the investigation that originally motivated them (see CLAUDE.md's
"Known standing issue").

### 3.16 Client local config (audio settings)

A master volume plus three per-category volumes (Weapons, Engines, Sound
Effects), each `[0,1]`, persisted to `audio-settings.json`
(`core.audio.AudioSettings`/`AudioSettingsStore`, owned by `StarWarsGame`).
Every field defaults to `1f` (100%) so an older save file missing a newer
category, or a brand-new one, behaves as full volume. **The volume
actually passed to any sound is always `masterVolume × the relevant
category's own volume`** (`getEffectiveWeaponsVolume()`/
`getEffectiveEnginesVolume()`/`getEffectiveSoundEffectsVolume()`);
`getMasterVolume()` alone is used wherever there's no category concept
(Connect Screen music, hangar ambience).

## 4. Rendering & presentation

### 4.1 Camera

- **Battlefield camera:** with no other ship in sight the camera chases
  the local ship as it always has; as soon as anything is visible it
  chases the **weighted centroid of the local ship plus every visible
  other ship** instead — two ships give the midpoint of the line between
  them, three the centroid of their triangle, and so on — leaning the view
  toward the fight so more of the battlefield stays on screen. Owned by
  the pure, unit-tested `render.CameraFocus`; `Client.updateCamera` only
  feeds it positions and eases toward the result.
  - "Visible" needs no client-side filtering: everything in `Client`'s
    `ships` map is already exactly what this player's radar detects
    (2.14 — the server only ever sends detected contacts), same as the
    radar HUD's own contacts.
  - **Fade band, not a hard cutoff:** a contact counts at full weight out
    to 45m and then fades linearly to zero weight at 60m (*both untuned*).
    60m is deliberately the base radar's range — the only always-on
    omnidirectional detector — so a contact that is about to drop out of
    `ships` entirely was already contributing almost nothing and the focus
    point doesn't jump when it does. Inside 45m the focus is the exact
    plain centroid, so the ordinary close-quarters case isn't a
    distance-distorted version of the model above. Contacts detected
    further out (cone/pulse) still show on the radar HUD; they just don't
    drag the camera toward something far off screen.
  - **Offset clamp:** the focus point never sits further from the local
    ship than 50% of the currently-visible half-extent (shorter screen
    axis) — i.e. the local ship never leaves the middle half of its own
    screen (*untuned*). Deliberately a fraction of the live visible extent
    rather than a fixed distance, so the guarantee "the player stays
    comfortably inside their own view" holds automatically at every zoom
    level, manual or speed-driven. **This, not the centroid, is normally
    the binding constraint in an actual fight** — at 1920×1080 and default
    zoom it allows a ~8.4m lean, while the true centroid for a duel at 30m
    separation sits 15m away. Intended: the centroid picks the *direction*
    and the full-weight band decides *who counts*, while the clamp decides
    how far the camera may act on it. First constant to try changing if
    the effect reads as too weak or too strong.
  - **One smoothing stage only:** the fade band handles a contact drifting
    out of relevance, and the existing position easing below handles a
    contact appearing/disappearing outright (~0.3s glide, not a snap).
    Smoothing the focus point itself *as well* would just double the
    camera's lag.
- **Manual zoom (player's default zoom level):** Zoom In/Zoom Out keybinds
  (3.8/5.4) step a saved multiplier over \[50%, 200%\] of the default zoom
  level, persisted to `camera-settings.json` (3.9) via
  `render.CameraSettings`/`CameraSettingsStore` and owned for the app's run
  by `StarWarsGame`, exactly like the keybinds/audio settings. **A
  *smaller* multiplier is zoomed *in*** (a narrower, magnified view),
  matching libGDX's own `OrthographicCamera.zoom` convention — so "Zoom
  In" steps the value down toward 0.5. Steps are **geometric** (×/÷ 1.1,
  ~7 presses to either end, *untuned*) rather than a fixed amount, so in
  and out take the same number of presses despite the range being
  asymmetric around 1. Saved on every press (no separate Save button, same
  convention as keybinds/audio settings), and readable while dead/waiting
  to respawn since it's a pure view preference that sends nothing to the
  server.
- **Speed-linked zoom:** eases `camera.zoom` toward a target driven by
  current ship speed — +25% zoom-out at/above a 90 m/s reference speed
  (one shared reference across every ship type, a deliberate "ballpark,
  doesn't need to be exact" simplification), on its own slower easing
  speed than position tracking so a thrust burst doesn't visibly pulse the
  zoom. **Unchanged by the manual zoom above and always applied on top of
  it**, multiplicatively: `targetZoom = playerZoom × (1 + speedFraction ×
  0.25)`. Resets to the player's chosen zoom (not a flat `1f`) on
  spawn/respawn — speed is 0 at spawn, so that's exactly the resting
  target, and anyone who changed the setting would otherwise see the
  camera lurch on every respawn.
- **Camera inertia:** eases toward the focus point rather than rigidly
  locking it to screen center — tracks closely in normal flight, lags
  visibly during hard maneuvers before catching up. Deliberate feel/juice.
- **Window/viewport:** 1920×1080, `ScreenViewport` (not `StretchViewport`/
  `FitViewport`) — the "world" units already *are* screen pixels (scaled
  through `PIXELS_PER_METER`), so `ScreenViewport` grows/shrinks the
  visible area with the window on resize with no distortion/letterboxing;
  `viewport.update(..., false)` deliberately doesn't recenter the camera
  on resize.

### 4.2 Parallax starfield background

At least two layers, each a single seamlessly-tileable texture
(`TextureWrap.Repeat`) sampled with a UV offset sliding as `cameraPosition
* parallaxFactor`, drawn as one full-viewport quad per layer (not discrete
tiled sprites) — covers an arbitrarily large/moving world with one draw
call per layer. Only the layers drawn *on top of* the backmost one need
transparency; the backmost layer is opaque and fully covers the screen.
Backmost layer: `blue_nebula.png` (CC0). A closer, faster-scrolling
procedurally-generated star-dot layer (`PlaceholderStarfield`) still
awaits real transparent star art.

### 4.3 Ship sprites & animation

**Only the neutral-bank frame** (`..._0020.png`) is rendered per ship, not
the full 41-frame bank-angle sequence — bank frames visibly shift any
tracked attachment point (engine glow, muzzle origin, turret overlay) as
the frame changes, unsolved from an earlier version of this project;
revisit post-v1 with per-frame authored anchors. Turret-equipped ships
still work fine via an independently-rotated overlay on the neutral frame
(2.9). Strictly 2D — every moving visual element is a sprite or a particle
effect, no 3D models.

**Particle effects** (all classic libGDX 2D `.p` files, authored by the
user, each effect's referenced image kept alongside its `.p` file):

- **Engine trail** (`ThrusterEffect`) — one per ship type
  (`ShipTypeConfig.engineParticleEffect`, all 7 configured; two are shared:
  `thruster_falcon` also serves the Star Destroyer, `thruster_tie` serves
  both TIE variants), attached to `"ENGINE"` point(s), rotated every frame
  to the ship's current facing (rewriting the emitter's authored `"Angle"`
  range — the classic `ParticleEffect` API has no built-in "rotate the
  whole effect" call). Hard on/off (not faded) while the local thrust key
  is held; visible for every ship (`ShipState.thrusting` broadcast, not
  extrapolated). Drawn **before** the hull (not after) so the flame reads
  as emerging from underneath it.
- **Positioning lights** (`ShipLightEffect`) — `"LIGHT_RED"`/`"LIGHT_GREEN"`
  attachment points (split by which half of the sprite's width the point
  falls on), one shared red/green template for every ship type, always
  on for as long as the ship exists, stationary particles authored
  `attached: true` (so `setPosition` moves already-spawned particles with
  the ship, rather than leaving them behind at the original spawn point).
- **Damage smoke** (`DamageSmokeEffect`) — `"DAMAGE_SMOKE"` point(s),
  first point activates past 10% hull damage, a second (if present) past
  50% (`DAMAGE_SMOKE_THRESHOLDS`); one shared template for every ship
  type; authored `attached: false` (drifts/trails behind a moving ship,
  the opposite convention from lights, deliberately).
- **Radar pulse wave** (`OneShotParticleEffect`, shared with explosions
  below) — a one-shot expanding ring, triggered purely by observing a
  rising edge on `ShipState.getRadarPulseCooldownRemaining()` (needs no
  dedicated wire signal), for any visible ship, not just the local player.
- **Muzzle flash** (`MuzzleFlashEffect`) — one-shot, at each `"PROJECTILE"`
  point, rotated to the shooter's facing; triggered from client-side shot
  prediction for the local player (2.4), from the "new, unadopted
  projectile" branch of `onWorldSnapshot` for everyone else — a free
  signal, since projectiles are already broadcast unfiltered. The emitter
  is nudged forward every frame along the shooter's recorded velocity
  (`attached: true`) so it doesn't visibly trail a fast-moving ship.
- **Explosions** (`OneShotParticleEffect`) — a small one at any actual
  projectile hit (`ProjectileHitMessage`, a genuinely new server signal —
  a projectile's disappearance alone is ambiguous between a hit and simply
  expiring), a full one centered on any `ShipDestroyedMessage`. **The
  local player never sees their own destruction's particle** — this
  `Client` instance disposes and transitions away a few lines after
  processing its own death, so it would never actually render; every other
  player's destruction shows normally.

**Display names** float above every other visible player's ship
(never the local player's own), toggled by **N**
(`GameAction.SHOW_DISPLAY_NAMES`, default on), positioned by each ship's
own real rendered height (not a generic radius). Rendered with libGDX's
plain built-in `BitmapFont` (not "SF Distant Galaxy") — deliberately
lighter/more legible for a small floating label, and the first text in
this project needing neither baked art nor a `gdx-freetype` file. Reuses
the existing `ScoreboardMessage`/`PlayerScoreEntry` broadcast for the name
lookup — no new wire field.

**Texture atlas pipeline:** see CLAUDE.md's "Asset pipeline" section for
the mechanics/gotchas. Packed atlases: `ships`, `projectiles`, `menu`,
`asteroids`, `powerups`, `mines`.

### 4.4 UI framework

**Scene2D** as the base, with **VisUI** layered on top for menu-style
screens (Connect Dialog) that actually need form widgets — validated text
fields, buttons. In-match HUD and screens without real form widgets
(Ship Selection, Keybind Setup, Audio Settings) stay plain raw
`SpriteBatch` + custom hit-testing, deliberately not VisUI, since there's
no widget skinning work to save there. Considered and rejected: raw
Scene2D everywhere (too much hand-skinning for the form-heavy screens);
Dear ImGui bindings (fine for internal debug UI, not player-facing menus
— not adopted).

**Live text via `gdx-freetype`:** `core.render.GameFonts
.generateSfDistantGalaxy(sizePx)` rasterizes the actual "SF Distant
Galaxy.ttf" (bundled at `assets/fonts/sf_distant_galaxy.ttf`, license
terms checked and read as compliant with this project's free-internet-
distribution model) into a live `BitmapFont` at runtime — used for
Connect Screen fields/errors, the scoreboard, tooltips, and every other
screen's live text (everything *except* floating display names, 4.3,
which deliberately use libGDX's own built-in default font instead).

### 4.5 Audio

`Sound` (fully loaded, independently instance-controllable — `loop()`
returns an id whose volume/stop can be controlled per-instance) for
anything continuous/faded (engine loops); `Music` (streamed) for
long one-shot-per-screen tracks (Connect Screen theme, hangar ambience).

- **Connect Screen theme** — plays on `ConnectScreen`, faded out (1.5s,
  `StarWarsGame.fadeOutAndDisposeMusic`, ticked from the top-level
  `Game#render()` so it survives past the screen that started it) rather
  than cut on leaving.
- **Hangar ambience** (`ambience_hangar.mp3`) — loops continuously across
  Ship Selection/Keybind Settings/Audio Settings/Death Screen (the
  "hangar zone" — these four screens only ever transition to each other
  or to `Client` for real gameplay, never back to Connect). One
  persistent `Music` instance owned by `StarWarsGame` for the app's whole
  run (unlike the Connect theme, never disposed until app shutdown — just
  paused/resumed). `StarWarsGame.playHangarAmbience()` (idempotent —
  `Music.play()` no-ops if already playing, the actual mechanism behind
  "no interruption moving between zone screens") is called from each zone
  screen's own `show()`; `fadeOutHangarAmbience()` is called once, from
  `Client.show()`, the sole real exit from the zone. Volume is kept
  live-synced every frame from the master-volume slider (not set once at
  `play()` time, unlike the Connect theme) since Audio Settings — where
  that slider lives — is itself one of the zone screens.
- **Engine sound** — every ship type's own seamless loop
  (`Client.updateLocalEngineSound`/`updateRemoteEngineSounds`), volume
  fades 0↔1 over 250ms as the thrust key is held/released (the loop itself
  never restarts). Remote ships get the same loop with a linear distance
  falloff (full at 0m, silent at 40m,
  `REMOTE_SOUND_MAX_AUDIBLE_RANGE_METERS`, shared with every other
  positional sound below). Not rebuilt on respawn unless the ship type
  actually changed (restarting a loop mid-playback would audibly glitch
  it) — `ensureLocalEngineSound`.
- **Weapon sound** — one-shot per firing volley (never per-projectile,
  even across multiple `PROJECTILE` points or several turret mounts), via
  `Client.playPositionalSound(Sound, x, y)` (same distance falloff as
  engine sound). Main gun: per ship type. Turret: one shared clip
  (currently a placeholder, pending the real recording). Missile launch:
  one shared clip. A shot's `ProjectileComponent`/`ProjectileState
  .isTurretShot()` distinguishes a turret shot from a main-gun shot on the
  wire (nothing did before this feature); a missile is already
  distinguished by `trackedTargetPlayerId`.
- **Explosion sound** — one shared clip on any `ShipDestroyedMessage`,
  "Sound Effects" category. Unlike the explosion *particle*, the local
  player **does** hear their own destruction (a played `Sound` instance
  outlives this screen; a triggered particle would not).
- **Missile lock sound** — two looping clips, "trying" (inside the 5s
  acquisition window) / "acquired" (locked), mutually exclusive, local-
  player/attacker-side only, no distance falloff (it's targeting-computer
  feedback, not a real-world sound). Both start/stop instantly by
  construction — derived fresh from the same lock-state fields driving the
  visual reticle, no separate logic needed.
- **Impact sounds** — one of 4 interchangeable clips, picked randomly and
  **independently per client** (no wire field for which sample — purely
  cosmetic), whenever a ship physically collides with another ship, the
  arena boundary, or an asteroid (`ShipImpactMessage`, `registerPotentialShipImpact`
  — a new `ContactListener` hook independent of the wall/asteroid *damage*
  threshold, since this is a contact cue not a damage indicator; a ship-vs-
  ship collision naturally fires it twice, once per ship, accepted as a
  simplification).
- **Power-up pickup sound** — one shared clip via `PowerUpPickedUpMessage`,
  broadcast from the existing pickup-resolution path.

## 5. UX flow

### 5.1 Screen flow

```
 Connect Dialog → Ship Selection ⇄ Keybind Setup
                        │      ⇄ Audio Settings
                        │ select ship
                        ▼
                    Gameplay ──ship destroyed──► Death Screen
                        │ ESC (not in combat)          │
                        └──────────────────────────────┘
                                    back to Ship Selection
```

- **Connect Dialog:** host/display name/login/password, prefilled from
  local config (3.7). Full keyboard nav (TAB/Shift+TAB/ENTER); failed auth
  shows the server's own message, keeps every field intact. VisUI-based
  (4.4); blocking connect+handshake (no separate "connecting..." state).
- **Ship Selection:** the screen the player always returns to. Cycles
  every `ShipType` (arrow keys/buttons); locked ships show a padlock
  (2.12/2.13) with a hover tooltip. Holds its own live connection (for
  XP/unlock data) for as long as the player browses. Entry point to
  Keybind Setup (F12) and Audio Settings (F11).
- **Gameplay:** ends via ESC (not in combat, 2.3 — straight back to Ship
  Selection) or death (→ Death Screen).
- **Death Screen:** a random Star Wars quote + matching image (23
  user-authored cards, `QuoteDeck` shuffle-bag — no repeat until all 23
  have shown, session-local, owned by `StarWarsGame` so it persists across
  deaths). **ESC** returns to Ship Selection (each quote card bakes in its
  own "Press 'ESC' to continue"). Holding TAB shows the local player's own
  scoreboard row (a one-shot snapshot, since this screen has no live
  connection to ask about anyone else's).

### 5.2 Keybind Setup screen

Reachable only from Ship Selection (a "KEYBINDS" button or **F12**). Lists
every remappable `GameAction` (3.8) with a "press a key to bind" capture
field — click a row to enter listening mode, ESC cancels, any other key
rebinds and saves immediately (no separate Save button). "RESET TO
DEFAULTS" resets all at once; "BACK"/ESC returns to Ship Selection. Plain
raw `SpriteBatch` + `Gdx.input` polling (no VisUI — no form widgets
actually needed here). Scrollable (mouse wheel, `ScissorStack`-clipped)
now that the action list is longer than one screen's worth.

### 5.3 Audio Settings screen

Reachable only from Ship Selection (an "AUDIO" button, or **F11**). Four
sliders \[0%, 100%\]: Master, Weapons, Engines, Sound Effects (3.16). A
live-drawn `render.Slider` (rail/fill/handle/percentage readout) — click
anywhere on the track to jump+drag; persists to disk once, on release, not
every per-frame drag update. "RESET TO DEFAULTS"/"BACK" match the Keybind
screen's own buttons.

### 5.4 Controls (defaults — every action remappable via 5.2)

- **W** — thrust forward. **A/D** — rotate (rotational thrust, no
  instant-snap turning). **No reverse thrust** — turn 180° and thrust
  instead; removed outright, not reassigned.
- **SPACE** — fire primary weapon. **T** — toggle turret (Falcon/Star
  Destroyer only). **M** — fire missile (once locked, X-wing/TIE
  Interceptor only). **R** — trigger active radar pulse.
- **ESC** — leave match (blocked while in combat, 2.3) — the one
  non-remappable action.
- **Power distribution (2.2):** `J`=Shields, `I`=Weapons, `L`=Engines
  (`J`/`L` flank the reset key `K` on the home row, matching the HUD
  bars' left-to-right order). Tap = one increment; hold = jump to max.
- **TAB** — scoreboard. **N** — toggle other players' display names.
- **Numpad +/-** — zoom the camera in/out, over \[50%, 200%\] of the
  default zoom level (4.1). Saved across matches and sessions; usable
  while dead/waiting to respawn.

Classic "Asteroids-style" Newtonian control scheme.

## 6. Components / TODOs

Everything through 2.19/3.x/4.x/5.x above is implemented and playable.
Genuinely still open, beyond what's tracked in §7:

- **Ship roster balance** — every ship still shares the X-wing's exact
  thrust/torque/hull/shield numbers; no real per-ship balance pass has
  happened, only the tiered unlock costs (2.12) and branch order (2.13).
- **Turret/asteroid/power-up/missile physics numbers** — density,
  restitution, spawn velocity ranges, capacitor sizing, missile thrust/
  torque are all explicitly untuned starting points, flagged individually
  throughout §2, pending real feel-testing.
- **No player-visible missile-count or capacitor-charge indicator** — the
  missile lock simply stops appearing once out (2.15), which is the only
  current "ammo" signal.
- **`update.cmd`'s actual successful download/swap path** is unverified
  against a real prior install (3.11) — its self-relaunch and download-
  failure paths are.
- **Server not yet deployed to the real NAS** (3.12) — Docker
  image/Compose file are built and locally verified, the actual NAS/router
  steps are tracked with the user directly.

### 6.1 Content: death screen quotes

Filled — 23 complete user-authored quote+image cards
(`assets-raw/after_death/Quote_1.png`..`Quote_23.png`), see 5.1.

## 7. Open design questions

- **Ship Tree exclusivity** — nothing stops one account unlocking ships on
  both the Imperial and Rebel branches (2.13); no faction-exclusivity
  decision has actually been made, this is just the current behavior by
  default.
- **Tick rate / snapshot rate tuning** for the netcode — still the
  original 30Hz placeholder, never revisited.
- **Lag compensation** for hit detection (rewind-time hit registration vs.
  simple current-state checks) — matters more as ping increases; not
  tested over real (non-loopback) internet latency between the actual
  UK/Belgium/Norway players yet.
- **Bank-angle ship rendering** (the full 41-frame sequence per ship,
  4.3) — deliberately deferred post-v1 until attachment-point tracking
  across bank frames can be solved properly.
