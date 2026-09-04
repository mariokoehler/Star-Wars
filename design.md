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
  consideration, not an edge case. See 3.8 / 4.2.
- **Fan project note:** this uses Star Wars ships, characters, quotes and
  likenesses purely as a non-commercial personal project. Not for
  distribution/monetization — keep that in mind if that ever changes.

## 2. Core gameplay mechanics

### 2.1 Newtonian flight

Covered in detail once implementation starts (see 3.3 for the Box2D-based
approach). Default controls are in 4.3.

### 2.2 Power distribution

Each ship has a power core producing a fixed amount of power per second,
divided between three systems: **Shields**, **Weapons**, **Engines**. How a
player allocates that power is a real-time tactical choice, not a one-time
loadout decision:

- **Shields** — more power = faster shield regeneration.
- **Weapons** — more power = higher rate of fire.
- **Engines** — more power = more thrust and better overall agility
  (turn rate).

This is a continuous throughput split (percentages of current output), not
a stored/depletable battery — simple to reason about and to implement.

- **Baseline:** power is split evenly across all three systems (~33.3%
  each) by default.
- **Adjustment:** three keybinds, one per system (defaults in 4.3).
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
4.1). To prevent using ESC as a "safe escape hatch" out of a losing
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
fight (see 4.1).

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

- `core` — shared code: entities, components, physics/flight math, game rules.
  Used by **both** client and server so simulation logic is never duplicated
  or allowed to drift between them.
- `lwjgl3` — desktop client (rendering, input, audio, UI).

**To add:**

- `server` — dedicated server module, built on `gdx-backend-headless`
  (`com.badlogicgames.gdx:gdx-backend-headless`). This runs the standard
  libGDX application lifecycle (`create()`/`render()`/`dispose()`,
  `Gdx.app`, `Gdx.files`, `Gdx.net`) with no window/graphics/audio — exactly
  what a headless server process needs, while still letting it depend on
  `core` directly.
- `network` (tentative — may just live inside `core`) — shared wire message
  definitions (packet/DTO classes) referenced by both `client` and `server`
  modules, so the protocol is defined once.

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

### 3.5 Netcode approach (planned, not yet implemented)

- **Reliable channel (TCP):** login/account handshake, join/leave, ship
  selection, spawn/despawn, death/kill events, chat, match state changes.
- **Unreliable channel (UDP):** per-tick position/velocity/rotation
  snapshots — frequent, latest-value-wins, fine to drop a packet since the
  next one supersedes it.
- **Not networked at all:** a player's own power allocation (2.2) — it's
  intentionally hidden from other players, so it never needs to leave the
  owning client/server pair beyond what's needed for the server to apply
  its gameplay effects.
- **Client-side prediction:** each client simulates its own ship locally on
  input immediately, then reconciles against the authoritative server
  snapshot for that ship (standard replay-unacknowledged-inputs pattern).
- **Other clients' ships:** interpolated/extrapolated between received
  snapshots to smooth over network jitter.
- Tick rate, snapshot rate, and interpolation buffer sizing: **not yet
  decided** — needs prototyping once basic movement is networked.
- At 8 players, we can likely broadcast full world state to everyone (no
  interest management / area-of-interest filtering needed at this scale).

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
(see 4.1). This matches the "fill it out once, then it's quick" experience
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
screen (4.2), persisted to a client-local JSON file, separate from the
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

## 4. UX flow

### 4.1 Screen flow

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
  Setup screen (4.2).
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

### 4.2 Keybind Setup screen

Lists every remappable action with a "press a key to bind" capture field
(see 3.8 for why this approach is layout-safe). Actions to cover: thrust
forward/reverse, rotate left/right, fire, the three power-distribution
keys, and power reset. **ESC (leave match) is fixed, not remappable** —
decided: every keyboard/layout has an ESC key, so there's no
internationalization reason to expose it here, and it's simpler to keep it
hardcoded. Changes save immediately to the local keybinds file.

### 4.3 Controls (v1, defaults)

Keyboard only for now; more keybinds will follow as further features are
added. All of the below are just the **defaults** — every action is
remappable via the Keybind Setup screen (4.2).

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

## 5. Components / TODOs

Rough build order — check items off as they land, add detail as sub-bullets
once a component is actually being worked on.

- [ ] **`server` Maven module** — `gdx-backend-headless`, depends on `core`,
      runs the authoritative simulation loop.
- [ ] **Networking layer** — KryoNet fork wired into both `client` and
      `server`, shared message/packet classes, connect/disconnect handling.
- [ ] **Account system (server)** — JSON-file-backed `PlayerAccount` store,
      auto-register-or-validate-on-connect flow, salted password hashing.
- [ ] **Client local config** — load/save connection fields (3.7) and
      keybinds (3.8) to separate local JSON files.
- [ ] **Connect Dialog screen** — host/display name/login/password fields,
      prefilled from local config, error display on failed auth.
- [ ] **Keybind Setup screen** — press-to-bind capture, localized key-label
      display (see 3.8 implementation note), persists to local config.
- [ ] **Entity/component model** (Ashley) for ships, projectiles, pickups.
- [ ] **Newtonian flight model** on Box2D bodies — thrust, rotation
      (torque), inertia/drag tuning so it feels like flying, not floating
      forever or drifting like a brick.
- [ ] **Power distribution system** — server-authoritative allocation
      state per ship (not networked to other clients), feeding shield
      regen rate / weapon fire rate / engine thrust & agility; the
      three-keybind +5/-2.5/-2.5 adjustment with the 10% floor/redirect/
      no-op algorithm (2.2), and reset-to-even logic.
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
- [ ] **HUD** — health, shield, target/radar or minimap, kill feed,
      scoreboard. (No power-distribution readout for *other* players —
      that's intentionally hidden, see 2.2.)
- [ ] **Death Screen** — random Star Wars quote + matching image; needs a
      content pool (see 5.1) and ENTER-to-continue handling.
- [ ] **XP & progression** — award XP per match/kill, unlock additional
      ships at XP thresholds (persisted via the account system above).

### 5.1 Content TODO: death screen quotes

Need to compile a pool of (quote, matching image) pairs before this screen
can ship. Starting example:

- Qui-Gon Jinn — *"There's always a bigger fish."* — image of the Naboo
  swamp monster chasing the sub (*The Phantom Menace*).

Add more pairs here as we pick them; keep it to one clear "you just died,
here's the universe laughing at you" beat per entry.

## 6. Open design questions

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
