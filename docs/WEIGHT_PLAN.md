# Block Weight Plan — Structural Physics Mod (MC 26.2, server-only Fabric)

Method: every block = 1 m³ of its real material. `mass_kg = density(kg/m³) × volume_factor(shape)`.
The master table covers all 1,196 blocks reported by the vanilla 26.2 server data generator, plus
59 compatibility rows retained from the original table (including the required `minecraft:chain`).
There are zero unassigned vanilla registry ids.

## Material densities (kg/m³)

| Group | Density | Notes |
|---|---|---|
| Stone / smooth stone | 2600 | granite 2700, diorite 2800, andesite 2650, basalt 3000 |
| Cobblestone | 2200 | rubble, has voids |
| Deepslate | 2800 | blackstone 2900, tuff 1600, calcite 2710, obsidian 2550 |
| Sandstone | 2300 | end stone 2200, netherrack 1900, dripstone 2400 |
| Brick / nether brick | 1900 / 2000 | mud brick 1700, terracotta 2000 |
| Concrete | 2400 | powder 1600 (unset) |
| Glass / quartz | 2500 / 2650 | amethyst & purpur 2650, prismarine 2600 |
| Cinnabar (26.x) | **8100** | real HgS mineral — heaviest buildable stone |
| Sulfur (26.x) | 2070 | resin 1100 (amber) |
| Logs (green wood) | 450–900 | poplar 450 → dark oak 900; per-species |
| Planks (dry wood) | 420–800 | poplar 420 → mangrove 800; per-species |
| Soils | 1200–1760 | dirt 1300, clay 1760, mud 1730, sand 1600, gravel 1680 |
| Iron / copper / gold | 7870 / 8960 / 19300 | netherite 21000, raw ore blocks 5000–12000 |
| Diamond / emerald / lapis | 3510 / 2760 / 2800 | coal 1350, redstone 5000 |
| Ores | 2900 stone / 3100 deepslate / 2200 nether | host rock + vein |
| Wool / hay / leaves | 250 / 240 / 80 | sponge 100 (wet 1000), moss 300 |
| Ice / packed / blue | 917 / 920 / 920 | snow block 400, powder snow 200 |
| Bedrock / reinforced deepslate | 20000 / 6000 | effectively immovable anchors |

## Shape volume factors

full 1.0 · stairs 0.75 · slab 0.5 · wall 0.5 · grate 0.5 · shelf 0.4 · bed 0.35 · anvil 0.35 · fence/gate 0.25 · door/trapdoor 0.1875 · pane 0.125 · bars 0.08 · carpet/snow layer 0.0625 · pressure plate 0.055 · sign 0.05 · lantern 0.05 · head/skull 0.04 · chain/rod 0.03 · rail 0.02 · button 0.01 · torch/candle 0.005

Plants/crops/flowers = 1 kg nominal (never load-bearing). Technical blocks (air, water, portals, etc.) = 0, ignored by the simulator.

## Reference weights (kg per placed block)

stone 2600 · cobblestone 2200 · stone slab 1300 · oak planks 700 · oak stairs 525 · oak slab 350 · spruce planks 450 · oak log 750 · dirt 1300 · sand 1600 · glass 2500 · glass pane 312 · white wool 250 · iron block 7870 · gold block 19300 · netherite 21000 · cinnabar 8100 · iron door 1476 · oak fence 175

## Next step: support strength (the other half of the numbers)

Weight is the *load*; each material also needs a *carry capacity* — how many kg a single block can hold up before the column/beam fails, derived from real compressive strength (stone ~50 MPa, wood ~5 MPa along grain, etc.) scaled down so gameplay thresholds land in the tens-of-blocks range. That gives: heavier material needs more supports; stacks need more support lower down (load accumulates downward); caves stay stable because natural stone spans have huge compressive capacity and only fail when a span/overhang exceeds its beam limit.

## Generated data workflow

`data/master_blocks.csv` is the only editable source for block mass and strength data. Its columns
are `id, material, density_kg_m3, volume_fraction, category, C_MPa, S_MPa, T_MPa, notes`.
`tools/compute_strengths.py` applies the formulas in `docs/COLLAPSE_MATH.md` and deterministically
writes both runtime JSON files. The JSON map schemas cannot carry a metadata key without becoming a
fake block id, so this documentation and the generator's `--check` result are the generated-file
marker convention.

The imported rows deliberately use effective density and MPa inputs so all 1,254 existing numerical
profiles remain byte-for-byte reproducible. Physical-material retuning belongs to issue 1B, not this
pipeline change. `minecraft:chain` is the sole new profile: iron at the documented `0.03` chain/rod
volume fraction.

Run:

```text
python3 tools/compute_strengths.py
python3 tools/compute_strengths.py --check --audit
python3 -m unittest tests.test_compute_strengths -v
```

`data/vanilla_blocks_26.2.txt` is the committed coverage baseline. It was produced from the official
26.2 server jar with the vanilla data generator's `--reports` mode and contains the ids from
`generated/reports/blocks.json`. Rows intentionally ignored by the simulator are still explicit in
the master CSV with `category=ignored`; missing registry ids therefore always mean a data bug.
