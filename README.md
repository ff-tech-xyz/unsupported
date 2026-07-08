# Unsupported

Unsupported is a PyreHaven Fabric server mod for Minecraft 26.2 that makes building care about structural support and block weight.

The goal is simple: heavy blocks should need sensible supports. A stone bridge, a copper tower, or a pile of anvils should not all behave like weightless decorations forever.

## Current status

Phase 1 adds the block-weight data loader and the operator `/weight <block>` command. Full structural-collapse gameplay is still planned for later phases.

## First testable feature

The mod loads the provided block-weight table and adds an operator command:

```mcfunction
/weight <block>
```

The command is tab-completable, usable by server operators, and returns the configured weight for the selected block.

## Block weight data

The initial block-weight table is saved in [`docs/BLOCK_WEIGHTS.md`](docs/BLOCK_WEIGHTS.md). It contains 1,254 Minecraft block ids and their proposed weight values.

## Roadmap

1. Load block weight data in a Fabric 26.2 server mod.
2. Add `/weight <block>` for operator testing.
3. Prototype non-destructive support checks so admins can see what would be supported or overloaded.
4. Add configurable unsupported-building behavior after testing the rules on a local server.

## Testing plan

This mod will use a PyreHaven local test-server profile:

```bash
pyretest switch mod:unsupported
```

The profile is intended to run a Fabric server on the default Minecraft port, `25565`.

## Release rules

Phred works on branches and opens PRs. Elijah merges `main` and controls official releases. Production server changes, if any, go through the normal PyreHaven VPS approval gate.
