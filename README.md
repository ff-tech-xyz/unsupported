# Unsupported

Unsupported is a PyreHaven Fabric server mod for Minecraft 26.2 that makes building care about structural support and block weight.

The goal is simple: heavy blocks should need sensible supports. A stone bridge, a copper tower, or a pile of anvils should not all behave like weightless decorations forever.

## Current status

Phase 2 adds active structural-collapse behavior. The mod loads block weight and strength data, watches server-side block interactions, scans a bounded local region, and turns unsupported failed blocks into falling blocks.

The first safety limits are deliberately conservative:

- scan cap: `256` blocks by default
- max scan radius: `16`
- max falling blocks per tick: `64`
- unknown blocks use a stone-like strength fallback
- blocks that cannot safely become falling blocks are left untouched
- no automatic player/operator warning messages

## Operator commands

The mod keeps the original weight command and adds an `unsupported` namespace for testing:

```mcfunction
/weight <block>
/unsupported weight <block>
/unsupported strength <block>
/unsupported scan <pos>
```

`/unsupported scan` reports support status without directly mutating the world. Normal collapse checks happen from server-side block break/use events and are processed at the end of the level tick.

## Block weight data

The initial block-weight table is saved in [`docs/BLOCK_WEIGHTS.md`](docs/BLOCK_WEIGHTS.md). It contains 1,254 Minecraft block ids and their proposed weight values.

## Roadmap

1. Load block weight data in a Fabric 26.2 server mod.
2. Add `/weight <block>` for operator testing.
3. Add active structural-collapse checks with bounded scans, config safety caps, and falling-block failures.
4. Tune support math and gameplay feel on the local test server.

## Testing plan

This mod will use a PyreHaven local test-server profile:

```bash
pyretest switch mod:unsupported
```

The profile is intended to run a Fabric server on the default Minecraft port, `25565`.

## Release rules

Phred works on branches and opens PRs. Elijah merges `main` and controls official releases. Production server changes, if any, go through the normal PyreHaven VPS approval gate.
