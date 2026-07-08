# Collapse Math Spec — load, support, and the wedge rule

## 1. Per-material numbers (3 strengths + mass)

Every block gets 4 numbers. Mass `m` (kg) you already have. The other three come from real strengths, converted from MPa to kg of load:

```
capacity_kg = strength_MPa × 101,971 kg/MPa ÷ SF        (SF = safety/scale factor, default 100)
            ≈ strength_MPa × 1,020 kg
```

| Symbol | Meaning | Real source |
|---|---|---|
| `C` | **carry** — load it can pass straight *down* (column/compression) | compressive strength |
| `S` | **shear** — load it can hand *sideways* to a neighbor (beam/cantilever) | bending/shear strength |
| `T` | **tension** — load it can *hang* from the block above | tensile strength |

This is what makes it realistic for free: stone has huge `C` but tiny `S`/`T` (masonry can't cantilever), wood has moderate `C` but proportionally great `S` (beams), dirt/sand have almost nothing, metals are good at everything, gold is heavy *and* soft.

## 2. The load tree (what happens on a block update)

Every solid block picks exactly **one parent** — the neighbor it leans on — by priority:

1. **below** (sit on it) → edge type DOWN
2. **opposite horizontal pair both supported** → edge type ARCH (the wedge rule, §4)
3. **best-supported side** → edge type SIDE
4. **above** → edge type HANG

Loads accumulate leaf→root:

```
L(p) = m(p) + Σ L(child c whose parent is p)
```

Then each edge is checked against the *parent's* capacity for that edge type:

```
DOWN  : L ≤ C                               (almost never fails — realistic; wool/snow/ice pillars do)
SIDE  : L × d ≤ S      d = horizontal distance to nearest vertical support (moment arm)
ARCH  : L ≤ C_arch     and span ≤ maxArchSpan (§4)
HANG  : L ≤ T
```

If an edge fails → **the entire subtree above/behind that edge falls** (that's your "blocks above fall with it": the subtree is exactly the set that was leaning on the broken path).

### Max cantilever (closed form, for the config/table)
A uniform overhang of `n` blocks puts a moment of `m·n(n+1)/2` on the root block, so:

```
n_max = largest n with  m·n(n+1)/2 ≤ S
```

e.g. oak planks (m=700, S≈25,500) → n=8. Stone (m=2600, S≈5,100) → n=1. Iron (S≈255,000) → n=7. Wool → 0.

## 3. Support field — how a block knows it's grounded (the lag-friendly part)

You never walk the whole tree per update. Keep a cached per-block **support byte** (0–255), maintained exactly like Minecraft's light engine:

```
grounded (bedrock / bottom-of-world column) : support = 255
otherwise: support = max over neighbors of ( support(nb) − cost(edge) )
  cost(DOWN) = 0
  cost(SIDE) = ceil( K · L(p) / S(nb) )      → heavy load or weak material = support dies fast sideways
  cost(ARCH) = 1                              → arches carry support far (but see span cap)
  cost(HANG) = ceil( K · L(p) / T(nb) )
stable ⇔ support > 0
```

On any block change you run the light-engine two-queue BFS (a *decrease* flood then a *re-add* flood from the boundary), bounded by radius `R = max span in config` (~16). Cost is O(blocks actually affected) — placing one block in a wall touches a handful of cells. That plus these rules is the whole anti-lag story:

- integers only; `support` = byte, `L` quantized to 100 kg units in a short — two flat arrays per chunk section, saved with the chunk
- dirty positions deduped per tick, fixed per-tick budget (e.g. 4,096 cell updates), overflow rolls to next tick
- the BFS runs **off-thread on a snapshot** of the affected sections, grouped into 2D region buckets so workers never share data (parallel, race-free); main thread applies results next tick with a version check
- collapses are batched: one falling cluster = a few merged FallingBlockEntities (or direct downward block scan for big cave-ins), hard cap per event

## 4. The wedge rule (arch action) — caves for free

Real reason a block pinched in a cave ceiling stays: **compression arching**. A block with supported neighbors on *opposite* sides can't rotate out; it pushes outward into both walls and load travels as pure compression, and stone is superb in compression.

```
ARCH applies when: neighbors at x−/x+ (or z−/z+) both have support > 0
capacity: C (compression!) not S — this is why a 1-thick stone span holds where a cantilever wouldn't
span cap: thrust of a shallow arch with rise ≈ 1 block:  H ≈ m·span²/8  ≤ C
          maxArchSpan = floor( √(8·C / m) )
```

Examples: stone → √(8·51,000/2,600) ≈ **12 blocks**, deepslate ≈ 15, oak planks ≈ 20, wool ≈ 2. So natural cave ceilings (pinched between walls, span under ~12–15) are stable *by the same math as player builds* — no special-casing terrain. Mine the ceiling wider than the span cap, or cut one abutment, and it comes down.

Bonus realism knob: blocks *above* an arch block press down and count into `L`, so a thin bridge with a castle on it fails while the bare bridge stands.

## 5. Worked examples

- **Dirt shack, 3-wide flat dirt roof**: dirt S≈50 kg·block, m=1300 → cantilever 0, arch span √(8·300/1300)≈1. Roof collapses. Use wood.
- **Oak plank floor spanning a 7-wide room**: both walls supported → ARCH, span 7 ≤ 20 ✓ stays. Same floor as a balcony (one wall only) → SIDE, n_max=8 ✓ barely stays; stack a cobble wall on the tip → L×d explodes → snaps at the root, balcony subtree falls.
- **Cave ceiling, stone, 10-wide**: ARCH span 10 ≤ 12 ✓. Player mines a 1-wide slot across the middle: each half is now a cantilever, d up to 5, L×d ≫ S → cave-in, exactly like real mining without pillars.
- **Siege**: knock out a castle wall's base corner → every block whose only path to ground ran through it (the subtree) falls. TNT a central pillar → arch spans double → mass collapse.

## 6. Update flow summary (per block update received)

```
1. mark pos dirty (set, deduped)
2. end of tick: bucket dirty positions by 2D region → submit snapshot jobs to worker pool
3. worker: two-queue BFS over support field (radius ≤ R), recompute L along changed parent edges,
   collect {support/load array patches, failed-edge subtrees}
4. main thread next tick: version-check, write arrays, spawn batched collapses
   (collapsed blocks land → new block updates → loop continues naturally)
```

Files: `block_strengths.csv` (per block: m, C, S, T, max cantilever, max arch span), `block_strengths.json` (mod config), `compute_strengths.py` (tweak SF/strengths and regenerate).
