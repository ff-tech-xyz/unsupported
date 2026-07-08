# Unsupported Phase 2 Support / Collapse Plan

Status: superseded by active-collapse direction; see `docs/IMPLEMENTATION_PLAN.md`
Source docs copied from ignored scratch folder:

- `docs/WEIGHT_PLAN.md` — mass/weight assignment method for all 1,254 MC 26.2 blocks.
- `docs/COLLAPSE_MATH.md` — proposed load tree, support field, wedge/arch rule, and update flow.
- `docs/BLOCK_STRENGTHS.csv` — per-block mass plus carry/shear/tension capacities and derived span numbers.

## Goal

Turn Unsupported from a `/weight` lookup mod into active structural collapse. Elijah confirmed we can get straight to building: block-update scans, configurable cap default 256, stone-like unknown fallback with untouched final fallback, falling blocks, and no warnings.

## Key model from the gathered docs

Each block has four numbers:

- `m` — mass/load in kg, already loaded in Phase 1.
- `C` — carry/compression capacity for load passed downward.
- `S` — shear/beam capacity for sideways support and cantilevers.
- `T` — tension capacity for hanging from above.

Each solid block chooses one support parent in priority order:

1. block below: `DOWN`
2. opposite supported horizontal neighbors: `ARCH`
3. best supported side neighbor: `SIDE`
4. block above: `HANG`

Loads accumulate child-to-parent. An edge fails when the load exceeds the parent capacity for that edge type.

## Phase 2 boundaries

Phase 2 is now active collapse, not diagnostics-only:

- compute support status on block updates
- configurable scan cap, default 256
- unknown blocks fall back to stone-like behavior
- final fallback leaves untouchable/problem blocks alone
- failed structures become falling blocks
- no unsolicited warnings/messages

## Implementation slices

### 1. Add strength data resource

Create runtime data from `docs/BLOCK_STRENGTHS.csv`, probably as JSON under:

- `src/main/resources/data/unsupported/block_strengths.json`

Expected shape:

```json
{
  "minecraft:stone": { "m": 2600, "C": 51000, "S": 5100, "T": 5100 }
}
```

Keep `block_weights.json`; Phase 1 `/weight` should remain stable.

### 2. Add strength loader service

Likely files:

- `src/main/java/xyz/fftech/unsupported/BlockStrengths.java`
- possibly `src/main/java/xyz/fftech/unsupported/StrengthProfile.java`

Responsibilities:

- load `block_strengths.json`
- lookup by block id
- expose mass/carry/shear/tension
- fallback safely for unknown/modded blocks

### 3. Add support result types

Likely files:

- `src/main/java/xyz/fftech/unsupported/support/SupportStatus.java`
- `src/main/java/xyz/fftech/unsupported/support/SupportResult.java`
- `src/main/java/xyz/fftech/unsupported/support/SupportEdgeType.java`

Statuses:

- `SUPPORTED`
- `UNSUPPORTED`
- `OVERLOADED`
- `LIMIT_REACHED`
- `UNKNOWN`

### 4. Add bounded support scanner

Likely file:

- `src/main/java/xyz/fftech/unsupported/support/SupportScanner.java`

Start simple and safe:

- bounded BFS around an origin position
- hard maximum scanned block count
- hard maximum radius/span
- calculate local load and parent edge type
- return a `SupportResult`
- no world mutation

The final math can later move toward the cached support-byte system from `COLLAPSE_MATH.md`; do not build that entire engine in the first implementation pass unless required.

### 5. Add admin command namespace

Likely command shape:

```mcfunction
/unsupported weight <block>
/unsupported strength <block>
/unsupported check <pos>
```

Keep existing `/weight <block>` as a compatibility shortcut unless Elijah wants it removed.

Command output should show:

- block id
- mass
- carry/shear/tension
- chosen support mode
- total/load estimate
- status and short reason

### 6. Add event diagnostics only after command path works

After `/unsupported check` is reliable:

- hook block place/break events
- queue affected positions
- rate-limit logs
- do not collapse anything yet

### 7. Test cases

Use the local `mod:unsupported` server and test:

- stone block on ground
- short oak bridge between two supports
- stone cantilever
- wide stone arch/cave ceiling
- dirt roof
- heavy anvil/gold/cinnabar example

## Decisions from Elijah / OGMoo-cow

1. Diagnostics-only phase: no — go straight to building active behavior.
2. `/weight` alias: IDC; keep it for compatibility.
3. Unknown/modded fallback: stone-like, with an untouched final fallback if even that cannot apply.
4. Scan cap: configurable, default 256.
5. Protected blocks: none.
6. Collapse behavior: falling blocks.
7. Warnings: no warnings for anyone.

## Verification commands when implemented

```bash
./gradlew build
pyretest switch mod:unsupported
pyretest deploy
pyretest logs fabric
```

Success evidence:

- build passes
- server loads Unsupported
- strength data count matches 1,254 entries
- `/weight minecraft:stone` still works
- `/unsupported strength minecraft:stone` reports C/S/T
- `/unsupported check <pos>` reports support status without changing blocks
