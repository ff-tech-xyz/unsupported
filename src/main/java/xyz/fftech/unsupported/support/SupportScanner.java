package xyz.fftech.unsupported.support;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import xyz.fftech.unsupported.BlockStrengths;
import xyz.fftech.unsupported.StrengthProfile;
import xyz.fftech.unsupported.UnsupportedConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SupportScanner {
    private final UnsupportedConfig config;

    public SupportScanner(UnsupportedConfig config) {
        this.config = config;
    }

    public SupportScanResult scan(ServerLevel level, BlockPos origin) {
        Map<BlockPos, StrengthProfile> nodes = collectSolidRegion(level, origin);
        if (nodes.isEmpty()) {
            return new SupportScanResult(origin.immutable(), 0, List.of(), SupportStatus.SUPPORTED);
        }

        boolean limitReached = nodes.size() >= config.scanCap;
        Set<BlockPos> supported = new HashSet<>();
        for (BlockPos pos : nodes.keySet()) {
            if (hasExternalDownSupport(level, nodes, pos)) {
                supported.add(pos);
            }
        }

        boolean changed;
        do {
            changed = false;
            for (Map.Entry<BlockPos, StrengthProfile> entry : nodes.entrySet()) {
                BlockPos pos = entry.getKey();
                if (supported.contains(pos)) {
                    continue;
                }
                StrengthProfile profile = entry.getValue();
                if (isSupportedByBelow(nodes, supported, pos, profile)
                    || isSupportedBySide(nodes, supported, pos, profile)
                    || isSupportedByArch(nodes, supported, pos, profile)
                    || isSupportedByAbove(nodes, supported, pos, profile)) {
                    supported.add(pos);
                    changed = true;
                }
            }
        } while (changed);

        List<BlockPos> failed = new ArrayList<>();
        if (!limitReached) {
            for (BlockPos pos : nodes.keySet()) {
                if (!supported.contains(pos)) {
                    failed.add(pos.immutable());
                }
            }
        }

        SupportStatus status = limitReached ? SupportStatus.LIMIT_REACHED : failed.isEmpty() ? SupportStatus.SUPPORTED : SupportStatus.FAILED;
        return new SupportScanResult(origin.immutable(), nodes.size(), List.copyOf(failed), status);
    }

    private Map<BlockPos, StrengthProfile> collectSolidRegion(ServerLevel level, BlockPos origin) {
        Map<BlockPos, StrengthProfile> nodes = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        enqueueIfCandidate(level, origin, origin, queue, nodes);
        for (Direction direction : Direction.values()) {
            enqueueIfCandidate(level, origin.relative(direction), origin, queue, nodes);
        }

        while (!queue.isEmpty() && nodes.size() < config.scanCap) {
            BlockPos pos = queue.removeFirst();
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                enqueueIfCandidate(level, next, origin, queue, nodes);
                if (nodes.size() >= config.scanCap) {
                    break;
                }
            }
        }
        return nodes;
    }

    private void enqueueIfCandidate(ServerLevel level, BlockPos pos, BlockPos origin, ArrayDeque<BlockPos> queue, Map<BlockPos, StrengthProfile> nodes) {
        BlockPos immutable = pos.immutable();
        if (nodes.containsKey(immutable) || queue.contains(immutable)) {
            return;
        }
        if (distanceTooLarge(origin, immutable)) {
            return;
        }
        BlockState state = level.getBlockState(immutable);
        if (!isCollapsibleSolid(state)) {
            return;
        }
        Optional<StrengthProfile> profile = BlockStrengths.get(state.getBlock());
        if (profile.isEmpty()) {
            return;
        }
        nodes.put(immutable, profile.get());
        queue.addLast(immutable);
    }

    public static boolean isCollapsibleSolid(BlockState state) {
        return !state.isAir() && !state.liquid() && state.blocksMotion();
    }

    private boolean distanceTooLarge(BlockPos origin, BlockPos pos) {
        return Math.abs(origin.getX() - pos.getX()) > config.maxScanRadius
            || Math.abs(origin.getY() - pos.getY()) > config.maxScanRadius
            || Math.abs(origin.getZ() - pos.getZ()) > config.maxScanRadius;
    }

    private boolean hasExternalDownSupport(ServerLevel level, Map<BlockPos, StrengthProfile> nodes, BlockPos pos) {
        if (pos.getY() <= level.getMinY()) {
            return true;
        }
        BlockPos below = pos.below();
        if (nodes.containsKey(below)) {
            return false;
        }
        return isCollapsibleSolid(level.getBlockState(below));
    }

    private boolean isSupportedByBelow(Map<BlockPos, StrengthProfile> nodes, Set<BlockPos> supported, BlockPos pos, StrengthProfile profile) {
        BlockPos below = pos.below();
        StrengthProfile parent = nodes.get(below);
        return parent != null && supported.contains(below) && profile.massKg() <= parent.carryKg();
    }

    private boolean isSupportedBySide(Map<BlockPos, StrengthProfile> nodes, Set<BlockPos> supported, BlockPos pos, StrengthProfile profile) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(direction);
            StrengthProfile parent = nodes.get(side);
            if (parent != null && supported.contains(side) && profile.massKg() <= parent.shearKg()) {
                return true;
            }
        }
        return false;
    }

    private boolean isSupportedByArch(Map<BlockPos, StrengthProfile> nodes, Set<BlockPos> supported, BlockPos pos, StrengthProfile profile) {
        return archPairSupports(nodes, supported, pos, profile, Direction.NORTH, Direction.SOUTH)
            || archPairSupports(nodes, supported, pos, profile, Direction.EAST, Direction.WEST);
    }

    private boolean archPairSupports(Map<BlockPos, StrengthProfile> nodes, Set<BlockPos> supported, BlockPos pos, StrengthProfile profile, Direction a, Direction b) {
        BlockPos pa = pos.relative(a);
        BlockPos pb = pos.relative(b);
        StrengthProfile left = nodes.get(pa);
        StrengthProfile right = nodes.get(pb);
        return left != null && right != null
            && supported.contains(pa) && supported.contains(pb)
            && profile.massKg() <= Math.min(left.carryKg(), right.carryKg())
            && profile.maxArchSpan >= 1;
    }

    private boolean isSupportedByAbove(Map<BlockPos, StrengthProfile> nodes, Set<BlockPos> supported, BlockPos pos, StrengthProfile profile) {
        BlockPos above = pos.above();
        StrengthProfile parent = nodes.get(above);
        return parent != null && supported.contains(above) && profile.massKg() <= parent.tensionKg();
    }
}
