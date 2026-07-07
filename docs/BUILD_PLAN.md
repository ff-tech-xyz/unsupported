# Unsupported Build Plan

Status: active / phase 0 in progress
Channel: #unsupported (thread: 1524141731086336021; parent channel id not exposed to Phred)
Project slug: unsupported
Repo: ff-tech-xyz/unsupported
Local checkout: /home/p-h-r-e-d/workspaces/mods/unsupported
Last updated: 2026-07-07T20:02:06Z

## 1. Concept

Unsupported is a server-side Fabric 26.2 mod that makes building need support. Blocks get weights, and the server can use those weights to decide whether a structure is supported well enough to remain standing.

The first player/admin-facing slice is deliberately small: load the provided block-weight table, attach/query weights for Minecraft blocks, and expose an operator-only `/weight` command for checking values in game.

## 2. Decisions so far

- Elijah approved Phase 0 with public GitHub visibility and corrected spelling to `weight`.
- Project name/slug: `unsupported`.
- Type: standalone PyreHaven server-side Fabric mod targeting Minecraft/PyreHaven 26.2.
- Proposed repo owner/path: `ff-tech-xyz/unsupported`, checkout under `~/workspaces/mods/unsupported`.
- Proposed default branch: `main`, created as the protected stable branch. Feature work should happen on `feature/weight` or `update/mc-26-2`, never directly on `main`.
- First feature branch: `feature/weight`.
- Initial data artifact saved: `BLOCK_WEIGHTS.md` with 1254 parsed block-weight entries.
- Test route: local Fabric test server profile `mod:unsupported`, default Minecraft port `25565`, one active pyretest profile at a time.

## 3. Open questions

- [ ] Confirm whether the command should be exactly `/weight` or corrected to `/weight`. Plan keeps `/weight` unless told otherwise.
- [ ] Confirm whether weight values should be stored only as queryable data in Phase 1, or immediately affect block-support physics.
- [ ] Define support rules for the actual building mechanic: max carried weight, support propagation range, which blocks count as supports, and what happens when unsupported blocks fail.
- [ ] Confirm whether clients need the mod installed. Current assumption: server-side only for commands and block checks.

## 4. Research notes and sources

- https://docs.fabricmc.net/develop/ — Fabric's developer guides cover creating Fabric mods and server-side development topics. Implication: use the existing PyreHaven Fabric/Gradle structure and keep Fabric API/loader versions aligned with the 26.2 local test stack.
- Local `pyretest list` on 2026-07-07T19:55:40Z: existing Fabric 26.2 profiles use loader `0.19.3`; `mod:moo-cow-mod` is the closest 26.2 standalone mod template.
- Existing PyreHaven routing rules: new/unregistered project channels begin in plan mode, then get a state file, repo checkout, and pyretest profile once the project lane is approved.

## 5. Architecture

- **Mod entrypoint:** Fabric server/common initializer for `unsupported`.
- **Weight data:** generated resource/data file from `BLOCK_WEIGHTS.md`, loaded into a registry-backed map keyed by block id.
- **Block injection/query:** attach weights through a small service/API used by commands and later support simulation. Avoid hard-coding weights in Java.
- **Command:** `/weight <block>` available to operators only. The `<block>` argument should use Minecraft's block argument so it is tab-completable and validates block ids.
- **Support simulation (later phase):** evaluate connected/supporting blocks after placement/break events, calculate carried weight, and trigger configured failure behavior for unsupported structures.
- **Permissions/security:** command requires operator permission level; no player-run destructive testing until support rules are explicit.

## 6. Repository / project setup

- Repo owner/visibility: `ff-tech-xyz/unsupported`, public.
- Local checkout: `/home/p-h-r-e-d/workspaces/mods/unsupported`.
- Branch flow: initialize `main` as default/protected; implementation on `feature/weight`; PR to `main`; Elijah merges `main`.
- Build toolchain: Gradle + Fabric Loom, matching PyreHaven 26.2 standalone mod conventions.
- Test profile: add `mod:unsupported` to pyretest using Minecraft 26.2, Fabric loader 0.19.3, default port 25565.
- Documentation: README with plain description; `BLOCK_WEIGHTS.md` committed as the source weight table; usage docs for `/weight`.

## 7. Phased build plan

### Phase 0 — Intake and scaffolding

**Goal:** Turn #unsupported into a registered PyreHaven project lane without implementing gameplay.

**Phred does:**
- Confirm repo visibility if Elijah has not already answered.
- Create `ff-tech-xyz/unsupported` with default branch `main` if approved.
- Clone to `/home/p-h-r-e-d/workspaces/mods/unsupported`.
- Add basic README/project documentation and commit `BLOCK_WEIGHTS.md`.
- Add a project state file and, once the repo exists, register #unsupported in `projects-index.yaml`.

**Verification Phred runs:**
- `gh repo view ff-tech-xyz/unsupported` after creation.
- `git -C /home/p-h-r-e-d/workspaces/mods/unsupported status --short --branch`.
- Confirm `main` exists locally/remotely and no feature work was committed to it beyond approved scaffolding.

**Stop point / Elijah tests:** Elijah reviews the repo/docs and says `do phase 1`.

**What phrase starts it:** `do phase 0` plus visibility if not already clear.

### Phase 1 — Weight data and `/weight` command

**Goal:** Build a working Fabric 26.2 server mod that loads the block-weight table and lets ops query any block's weight in game.

**Phred does:**
- Create branch `feature/weight` off the approved base.
- Set up Fabric 26.2 Gradle project metadata and `fabric.mod.json`.
- Convert the provided weight values into a resource/data file.
- Implement a block-weight lookup service.
- Implement `/weight <block>` with operator permission and tab completion through Minecraft's block argument type.
- Add README usage documentation.

**Verification Phred runs:**
- `./gradlew build`.
- Confirm jar produced under `build/libs/`.
- Run or deploy on local Fabric test server.
- In server console/logs, verify the mod loads and the command is registered.

**Stop point / Elijah tests:** `mod:unsupported` test server running on default port 25565; Elijah can join and run `/weight minecraft:stone` as op.

**What phrase starts it:** `do phase 1`

### Phase 2 — Basic support checks

**Goal:** Make building support visible without risking destructive world behavior yet.

**Phred does:**
- Add support graph calculations after block place/break.
- Add debug/admin command output showing supported vs overloaded structures.
- Add config values for max load/support behavior.

**Verification Phred runs:**
- `./gradlew build`.
- Server smoke tests with simple pillars, bridges, and heavy blocks.
- Log evidence for supported and unsupported examples.

**Stop point / Elijah tests:** Elijah tests whether the rules feel right before blocks start breaking.

**What phrase starts it:** `do phase 2`

### Phase 3 — Active unsupported-building behavior

**Goal:** Enforce the support mechanic in gameplay.

**Phred does:**
- Add configured failure behavior for unsupported/overweight blocks.
- Add protections against runaway updates and server-lag cascades.
- Add admin config and documentation.

**Verification Phred runs:**
- `./gradlew build`.
- Stress tests on a local test world.
- `pyretest deploy` and log review for errors.

**Stop point / Elijah tests:** Elijah tests gameplay feel on the local server.

**What phrase starts it:** `do phase 3`

## 8. Test and release path

- Local build/test: `./gradlew build` in `/home/p-h-r-e-d/workspaces/mods/unsupported`.
- Test server: `pyretest switch mod:unsupported` after the profile exists; default Minecraft port 25565.
- Release gate: branch/PR flow only. Phred never commits/pushes/merges `main`; Elijah merges.
- Production gate: none for local testing. If this ever reaches production server install/restart, that is a `#vps` approval flow.

## 9. Risks / dependencies / approvals

- **Design risk:** the actual support mechanic can become laggy if it recalculates too much after every block update; Phase 2 should prototype cautiously.
- **Gameplay risk:** destructive failure behavior can grief builds if rules are wrong; Phase 3 should be gated by Elijah testing.
- **Compatibility risk:** 26.2/Fabric mappings are PyreHaven-specific; copy the local known-good pattern rather than guessing new dependency versions.

## 10. Commands / verification checklist

- [x] Routed #unsupported as an unregistered new-project channel.
- [x] Saved the provided weight table as Markdown: `/home/p-h-r-e-d/.hermes/project-memory/plans/unsupported/BLOCK_WEIGHTS.md`.
- [x] Parsed JSON successfully: 1254 entries.
- [x] Checked Fabric documentation source availability.
- [x] Checked local pyretest profiles for 26.2 Fabric version clues.
- [x] Confirmed public visibility; created repo and checkout.
- [ ] Register project in `projects-index.yaml` after repo/test profile facts exist.
- [ ] Implement Phase 1 only after Elijah says `do phase 1`.
