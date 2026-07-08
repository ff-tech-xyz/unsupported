# Unsupported Active Structural Collapse Implementation Plan

Status: approved direction from Elijah / OGMoo-cow
Last updated: 2026-07-08T02:26:34Z

## Player/design decisions locked for this implementation

- Go straight to building active behavior; do not spend a separate phase on diagnostics-only output.
- Keep existing `/weight` behavior; whether `/unsupported weight` also exists is not important.
- Unknown/modded blocks use a **stone-like fallback**.
- If even the fallback cannot be resolved, use a final **untouched fallback**: leave the block alone rather than crashing or forcing a collapse.
- Scan cap is configurable, default `256` blocks.
- Scans trigger from block updates.
- No protected block list for now.
- Failed structures become falling blocks.
- No warnings/messages for players or operators during normal collapse checks.

## Goal

Build the first active version of Unsupported: when a block update creates an unsupported or overloaded structure, the mod scans a bounded local region, calculates support using the gathered weight/strength model, and converts failed blocks into falling blocks.

The implementation must be safe under server load: bounded scan size, per-tick work budget, no chat spam, and no unbounded recursive cave-in chain. Chaotic caves, safe TPS.

## Source references

- `docs/WEIGHT_PLAN.md` — weight/mass assignment method.
- `docs/COLLAPSE_MATH.md` — load tree, support-byte idea, wedge/arch rule, falling subtree concept.
- `docs/BLOCK_STRENGTHS.csv` — 1,254-block table with `mass_kg`, `carry_C_kg`, `shear_S_kg`, `tension_T_kg`, `max_cantilever`, `max_arch_span`.
- Existing runtime data: `src/main/resources/data/unsupported/block_weights.json`.

## Architecture summary

Implement this in small vertical slices:

1. Load strength data.
2. Add configurable collapse settings.
3. Add a bounded support scanner that returns failed positions.
4. Wire scanner to block updates with a per-tick queue.
5. Convert failed blocks into falling blocks, capped per event/tick.
6. Verify with build + test server scenarios.

Do **not** start with the full off-thread cached support-byte engine from `COLLAPSE_MATH.md`. That is the long-term performance shape. First implementation should be synchronous but bounded, simple, and hard-capped. If the simple scanner proves too expensive, then promote to cached section data/off-thread jobs.

## Proposed file changes

### Data/resources

- Create: `src/main/resources/data/unsupported/block_strengths.json`
  - Generated from `docs/BLOCK_STRENGTHS.csv`.
  - Shape:
    ```json
    {
      "minecraft:stone": { "m": 2600.0, "C": 51000, "S": 5100, "T": 5100, "maxCantilever": 1, "maxArchSpan": 12 }
    }
    ```

### Core strength/config classes

- Create: `src/main/java/xyz/fftech/unsupported/StrengthProfile.java`
- Create: `src/main/java/xyz/fftech/unsupported/BlockStrengths.java`
- Create: `src/main/java/xyz/fftech/unsupported/UnsupportedConfig.java`

`UnsupportedConfig` defaults:

```java
public final class UnsupportedConfig {
    public static final int DEFAULT_SCAN_CAP = 256;
    public static final int DEFAULT_MAX_COLLAPSE_BLOCKS_PER_TICK = 64;
    public static final int DEFAULT_MAX_SCAN_RADIUS = 16;
    public static final boolean DEFAULT_COLLAPSE_ENABLED = true;
    public static final boolean DEFAULT_MESSAGES_ENABLED = false;
}
```

Add a config file if practical for Fabric 26.2 without pulling dependencies:

- `config/unsupported.json`

Config keys:

```json
{
  "collapseEnabled": true,
  "scanCap": 256,
  "maxScanRadius": 16,
  "maxCollapseBlocksPerTick": 64,
  "messagesEnabled": false,
  "unknownBlockFallback": "stone",
  "finalFallbackBehavior": "untouched"
}
```

If file-backed config is too much for the first pass, ship constants first but keep code structured so file-backed config can be added immediately after. User explicitly wants configurable cap, so prefer real config in the first implementation.

### Support/collapse package

Create package:

- `src/main/java/xyz/fftech/unsupported/support/`

Files:

- `SupportEdgeType.java` — `DOWN`, `ARCH`, `SIDE`, `HANG`, `NONE`
- `SupportStatus.java` — `SUPPORTED`, `FAILED`, `LIMIT_REACHED`, `UNTOUCHED_FALLBACK`
- `SupportNode.java` — position, strength profile, mass, parent, edge type, accumulated load
- `SupportScanResult.java` — origin, scanned count, failed positions, limit flag
- `SupportScanner.java` — bounded local scan and failure detection
- `CollapseQueue.java` — per-tick queue that turns blocks into falling blocks under caps
- `BlockUpdateHooks.java` or equivalent event registration helper

## Implementation tasks

### Task 1 — Generate and commit strength JSON

**Objective:** Turn `docs/BLOCK_STRENGTHS.csv` into runtime JSON.

Steps:
1. Add a small generation script or one-off Gradle/resource task only if the repo already accepts that pattern. Otherwise generate once and commit JSON.
2. Include every row from `docs/BLOCK_STRENGTHS.csv`.
3. Keep `minecraft:` prefix in JSON keys.
4. Preserve `m`, `C`, `S`, `T`, `maxCantilever`, `maxArchSpan`.

Verification:

```bash
python3 - <<'PY'
import csv, json
csv_rows = list(csv.DictReader(open('docs/BLOCK_STRENGTHS.csv')))
data = json.load(open('src/main/resources/data/unsupported/block_strengths.json'))
assert len(csv_rows) == 1254
assert len(data) == 1254
assert 'minecraft:stone' in data
print(data['minecraft:stone'])
PY
```

### Task 2 — Load strength profiles with stone fallback

**Objective:** Provide safe profile lookup for every block.

Implementation rules:
- `BlockStrengths.load(LOGGER)` loads JSON during mod init.
- `BlockStrengths.get(Block block)` returns:
  1. exact profile if present
  2. stone profile if exact profile missing
  3. empty/final fallback if stone profile missing
- Final fallback means scanner treats the block as untouched/non-collapsible and logs one concise warning at load time, not per block update.

Verification:
- `./gradlew build`
- server log shows strength count: `1254`

### Task 3 — Add real config loader

**Objective:** Make scan cap configurable with default 256.

Implementation rules:
- Load from `config/unsupported.json` on server init.
- If missing, write or use defaults.
- Clamp unsafe values:
  - `scanCap`: min 16, max 4096, default 256
  - `maxScanRadius`: min 4, max 64, default 16
  - `maxCollapseBlocksPerTick`: min 1, max 512, default 64
  - `messagesEnabled`: default false
- No player/operator warnings from scan/collapse unless `messagesEnabled` is later manually enabled; for now keep it false and do not wire chat output.

Verification:
- Start server once with no config; defaults apply.
- Edit `scanCap` locally to a test value and restart; log confirms loaded cap.

### Task 4 — Build bounded support scanner

**Objective:** Given a block update position, find local connected solid blocks and decide what fails.

First-pass scanner behavior:
- Input: `ServerLevel level`, `BlockPos origin`, config.
- BFS from origin and direct neighbors touched by the update.
- Stop at `scanCap` or `maxScanRadius`.
- Ignore air/fluid/zero-mass technical blocks.
- For each scanned solid block, choose parent by priority:
  1. below if solid/supporting
  2. arch if opposite horizontal neighbors are supported and span <= material `maxArchSpan`
  3. best supported side
  4. above for hanging
  5. none => failed
- Compute accumulated load bottom-up within the scanned graph.
- Edge checks:
  - `DOWN`: child load <= parent `C`
  - `SIDE`: child load * horizontal distance <= parent `S`
  - `ARCH`: span <= `maxArchSpan` and child load <= parent `C`
  - `HANG`: child load <= parent `T`
- If scanner hits cap, fail safe: do not collapse the whole unknown region. Return `LIMIT_REACHED` and only process clearly failed nodes inside the known local graph if safe; otherwise leave untouched.

Verification:
- Add unit-ish pure Java tests if possible, or at minimum command/internal test harness for sample profiles.
- Manual server scenarios listed below.

### Task 5 — Trigger scans from block updates

**Objective:** Collapses happen from real world changes, not commands.

Implementation rules:
- Register Fabric block place/break/change callbacks available in current 26.2 mappings/Fabric API.
- On update, enqueue origin and its six neighbors for end-of-tick processing.
- Deduplicate positions per tick.
- Per tick, process up to a fixed number of queued origins to avoid TPS spikes.
- Do not send chat messages.
- Keep logs sparse: startup config summary and serious errors only.

Verification:
- Place/remove supports on test server and observe falling behavior.
- Confirm no chat warnings appear.

### Task 6 — Convert failed blocks into falling blocks

**Objective:** Active collapse uses Minecraft falling block entities.

Implementation rules:
- For each failed position:
  - skip air/fluid
  - capture `BlockState`
  - remove block from world without drops, or with normal falling block semantics if available
  - spawn `FallingBlockEntity` at block center with captured state
- Cap total falling blocks per tick via config.
- If a failed set is larger than the per-tick cap, queue remainder for later ticks or leave remainder untouched until the next natural update. Prefer queue remainder with a hard global cap to avoid infinite collapse backlog.
- No protected blocks per Elijah; however, technical unspawnable blocks should use final untouched fallback if Minecraft cannot represent them as falling blocks.

Verification:
- Breaking a support under a small test roof makes roof blocks fall.
- Breaking one support of a cave/span can cause local cave-in.
- No drops duplication.
- No crash on chests/machines/unknown blocks; stone fallback or untouched fallback handles it.

### Task 7 — Keep admin commands for testing, but no warnings

**Objective:** Testing needs tools; gameplay should stay quiet.

Commands:
- Keep `/weight <block>`.
- Optional but useful:
  - `/unsupported strength <block>` — reports mass/C/S/T for ops.
  - `/unsupported scan <pos>` — reports scanned count/failure count for ops.

These commands are explicit operator actions, not warnings. They are acceptable even with “no warnings for anyone.”

Verification:
- `/weight minecraft:stone` still works.
- `/unsupported strength minecraft:stone` shows stone profile.
- `/unsupported scan ~ ~ ~` does not mutate unless command is explicitly named to test only. If using command for active trigger, name it clearly, e.g. `/unsupported collapsecheck <pos>`.

### Task 8 — Documentation update

Update:
- `README.md`
- `docs/BUILD_PLAN.md`
- Possibly create `docs/CONFIG.md`

Document:
- server-side only
- active block-update collapse behavior
- default config values
- falling block behavior
- no player warnings
- unknown block fallback behavior

## Manual test matrix

Run on `mod:unsupported` after build/deploy:

1. **Existing command regression**
   - `/weight minecraft:stone`
   - Expected: still reports configured weight.

2. **Stone-like fallback**
   - Test a modded/unknown block if one is available.
   - Expected: scanner treats it as stone-like or leaves untouched if fallback fails; no crash.

3. **Simple unsupported roof**
   - Build a small dirt/stone roof with inadequate support.
   - Break support.
   - Expected: unsupported blocks become falling blocks.

4. **Wood bridge sanity**
   - Build short oak span.
   - Expected: reasonable short span stays.

5. **Stone cantilever**
   - Build unsupported stone overhang.
   - Expected: overhang fails sooner than wood.

6. **Arch/wedge sanity**
   - Build/modify a span between two walls.
   - Expected: supported arch-like spans survive better than one-sided cantilevers.

7. **Scan cap**
   - Lower `scanCap` in config temporarily.
   - Trigger a large structure update.
   - Expected: no runaway scan/crash; cap behavior is safe.

8. **No warnings**
   - Trigger collapses as normal player/operator.
   - Expected: no unsolicited chat/actionbar warnings.

## Verification commands

```bash
./gradlew build
pyretest switch mod:unsupported
pyretest deploy
pyretest logs fabric
```

Evidence to collect before reporting done:

- build output success
- jar path under `build/libs/`
- server log startup lines showing weights + strengths + config loaded
- manual test observations for falling blocks
- no unsolicited warnings/messages
- `git diff --check` clean

## Risks / notes

- Falling blocks can cascade updates. Cap both scans and spawned entities.
- Some block states may not be valid as falling entities; use untouched fallback rather than crashing.
- Chests/machines are not protected by design, but they still must not crash the server. If Minecraft cannot safely spawn a block as falling, leave it untouched.
- The full cached support-byte engine is future optimization, not required for first active build.
