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

### Status / where we left off (2026-09-04)

Pure design phase so far — **no implementation has started**, no `server`
module, no networking code, no gameplay code beyond the Liftoff template's
placeholder `Client.java`. `design.md` is now reasonably comprehensive:
concept, core mechanics (power distribution incl. the exact clamping
algorithm, combat-lock ESC rule), architecture (modules, sim stack,
networking choice, netcode approach, accounts, client local config), UX
flow (screens, controls), a component TODO checklist, and a remaining
open-questions list (client distribution mechanism, ship roster, map/arena
design, tick/snapshot rate, lag compensation). Read `design.md` in full
before continuing — don't start implementation without an explicit go-ahead
from the user, since they've been explicit this is a "no programming yet,
just designing" phase.

## Build system

Maven, multi-module (migrated from the original gdx-liftoff Gradle setup on
2026-09-04). Modules: `core` (shared sim code), `lwjgl3` (desktop client).
`server` module (gdx-backend-headless) not yet created.

- `mvn clean package` from repo root builds everything; the runnable client
  jar ends up at `lwjgl3/target/StarWars-<version>.jar`.
- `mvn -pl lwjgl3 -am compile exec:exec` runs the client directly.
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
