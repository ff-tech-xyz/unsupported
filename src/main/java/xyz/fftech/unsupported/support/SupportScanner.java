package xyz.fftech.unsupported.support;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import xyz.fftech.unsupported.BlockPhysics;
import xyz.fftech.unsupported.BlockStrengths;
import xyz.fftech.unsupported.BlockWeights;
import xyz.fftech.unsupported.StrengthProfile;
import xyz.fftech.unsupported.UnsupportedConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SupportScanner {
    private final UnsupportedConfig config;

    public SupportScanner(UnsupportedConfig config) { this.config = config; }

    public SupportScanResult scan(ServerLevel level, BlockPos origin) {
        Region region = collectSolidRegion(level, origin);
        if (region.nodes.isEmpty()) return new SupportScanResult(origin.immutable(), 0, List.of(), SupportStatus.SUPPORTED);
        if (region.limitReached) return new SupportScanResult(origin.immutable(), region.nodes.size(), List.of(), SupportStatus.LIMIT_REACHED);

        Map<BlockPos, SupportPath> paths = supportPaths(level, region.nodes);
        Map<BlockPos, LoadTree.Node> tree = new LinkedHashMap<>();
        for (var entry : region.nodes.entrySet()) {
            StrengthProfile p = entry.getValue().profile;
            tree.put(entry.getKey(), new LoadTree.Node(entry.getKey().toShortString(), entry.getValue().effectiveMass, p.carryKg(), p.shearKg(), p.tensionKg()));
        }
        for (BlockPos pos : region.nodes.keySet()) {
            Parent parent = chooseParent(region.nodes, paths, pos);
            if (parent != null) {
                LoadTree.Node parentNode = tree.get(parent.pos);
                if (parentNode == null) {
                    BlockState supportState = level.getBlockState(parent.pos);
                    StrengthProfile profile = BlockStrengths.get(supportState.getBlock()).orElse(null);
                    if (profile != null) parentNode = new LoadTree.Node("external:" + parent.pos.toShortString(), 0, profile.carryKg(), profile.shearKg(), profile.tensionKg());
                }
                if (parentNode != null) tree.get(pos).attach(parentNode, parent.edge, parent.momentArm);
            }
        }

        Set<BlockPos> failed = new LinkedHashSet<>();
        for (BlockPos pos : region.nodes.keySet()) if (!paths.containsKey(pos)) failed.add(pos);
        Map<String, BlockPos> byId = new HashMap<>();
        tree.forEach((pos, node) -> byId.put(node.id(), pos));
        for (LoadTree.Node node : LoadTree.evaluate(new ArrayList<>(tree.values()))) failed.add(byId.get(node.id()));

        Set<BlockPos> collapseSet = new HashSet<>();
        for (BlockPos pos : failed) {
            collapseSet.add(pos.immutable());
            collapseSet.addAll(region.nodes.get(pos).attached);
        }
        List<BlockPos> collapse = new ArrayList<>(collapseSet);
        collapse.sort(Comparator.comparingLong((BlockPos p) -> p.getY()).thenComparingLong(p -> p.getX()).thenComparingLong(p -> p.getZ()));
        SupportStatus status = collapse.isEmpty() ? SupportStatus.SUPPORTED : SupportStatus.FAILED;
        return new SupportScanResult(origin.immutable(), region.nodes.size(), List.copyOf(collapse), status);
    }

    private Region collectSolidRegion(ServerLevel level, BlockPos origin) {
        Map<BlockPos, NodeData> nodes = new LinkedHashMap<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin.immutable());
        if (BlockPhysics.category(level.getBlockState(origin)) != BlockCategory.STRUCTURAL) {
            for (Direction direction : Direction.values()) queue.add(origin.relative(direction).immutable());
        }
        boolean limitReached = false;
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (!visited.add(pos)) continue;
            if (distanceTooLarge(origin, pos)) {
                if (BlockPhysics.category(level.getBlockState(pos)) == BlockCategory.STRUCTURAL) limitReached = true;
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (BlockPhysics.category(state) != BlockCategory.STRUCTURAL) continue;
            var profile = BlockStrengths.get(state.getBlock());
            if (profile.isEmpty()) continue;
            if (nodes.size() >= config.scanCap) { limitReached = true; break; }
            Attached attachedResult = attachedTo(level, pos);
            if (attachedResult.truncated) { limitReached = true; break; }
            List<BlockPos> attached = attachedResult.positions;
            double mass = StateMassModifiers.apply(profile.get().massKg(), state);
            for (BlockPos attachment : attached) {
                BlockState attachedState = level.getBlockState(attachment);
                mass += StateMassModifiers.apply(BlockWeights.get(attachedState.getBlock()).orElse(0.0), attachedState);
            }
            nodes.put(pos, new NodeData(profile.get(), mass, attached));
            queue.addLast(pos.above().immutable());
            for (Direction direction : Direction.Plane.HORIZONTAL) queue.addLast(pos.relative(direction).immutable());
        }
        return new Region(nodes, limitReached);
    }

    private Attached attachedTo(ServerLevel level, BlockPos host) {
        LinkedHashSet<BlockPos> result = new LinkedHashSet<>();
        HashSet<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (Direction direction : Direction.values()) queue.add(host.relative(direction).immutable());
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (!visited.add(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (BlockPhysics.category(state) != BlockCategory.ATTACHED) continue;
            List<BlockPos> candidates = AttachmentResolver.hosts(pos, state);
            boolean hosted = candidates.contains(host);
            if (!hosted) for (BlockPos candidate : candidates) if (result.contains(candidate)) { hosted = true; break; }
            if (!hosted) continue;
            if (result.size() >= config.scanCap) return new Attached(List.copyOf(result), true);
            result.add(pos.immutable());
            for (Direction direction : Direction.values()) queue.addLast(pos.relative(direction).immutable());
        }
        return new Attached(List.copyOf(result), false);
    }

    private Map<BlockPos, SupportPath> supportPaths(ServerLevel level, Map<BlockPos, NodeData> nodes) {
        Map<BlockPos, SupportPath> paths = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos pos : nodes.keySet()) {
            if (hasExternalDownSupport(level, nodes, pos)) { paths.put(pos, new SupportPath(0, pos)); queue.add(pos); }
        }
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            SupportPath path = paths.get(pos);
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (nodes.containsKey(next) && !paths.containsKey(next)) { paths.put(next, new SupportPath(path.distance + 1, path.ground)); queue.addLast(next); }
            }
        }
        return paths;
    }

    static Parent chooseParent(Map<BlockPos, NodeData> nodes, Map<BlockPos, SupportPath> paths, BlockPos pos) {
        SupportPath path = paths.get(pos);
        if (path == null) return null;
        if (path.distance == 0) return new Parent(pos.below(), SupportEdgeType.DOWN, 1);
        int own = path.distance;
        BlockPos below = pos.below();
        if (isCloser(paths, below, own)) return new Parent(below, SupportEdgeType.DOWN, 1);
        Parent arch = archParent(nodes, paths, pos, own, Direction.NORTH, Direction.SOUTH);
        if (arch == null) arch = archParent(nodes, paths, pos, own, Direction.EAST, Direction.WEST);
        if (arch != null) return arch;
        BlockPos best = null;
        double bestShear = -1;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(direction);
            if (isCloser(paths, side, own) && nodes.get(side).profile.shearKg() > bestShear) { best = side; bestShear = nodes.get(side).profile.shearKg(); }
        }
        if (best != null) return new Parent(best, SupportEdgeType.SIDE, horizontalDistance(pos, path.ground));
        BlockPos above = pos.above();
        return isCloser(paths, above, own) ? new Parent(above, SupportEdgeType.HANG, 1) : null;
    }

    private static Parent archParent(Map<BlockPos, NodeData> nodes, Map<BlockPos, SupportPath> paths, BlockPos pos, int own, Direction a, Direction b) {
        BlockPos pa = pos.relative(a), pb = pos.relative(b);
        if (!isCloser(paths, pa, own) || !isCloser(paths, pb, own)) return null;
        int span = paths.get(pa).distance + paths.get(pb).distance + 2;
        int cap = Math.min(nodes.get(pa).profile.maxArchSpan, nodes.get(pb).profile.maxArchSpan);
        return span <= cap ? new Parent(nodes.get(pa).profile.carryKg() >= nodes.get(pb).profile.carryKg() ? pa : pb, SupportEdgeType.ARCH, 1) : null;
    }

    private static boolean isCloser(Map<BlockPos, SupportPath> paths, BlockPos pos, int own) {
        SupportPath candidate = paths.get(pos);
        return candidate != null && candidate.distance < own;
    }

    static int horizontalDistance(BlockPos pos, BlockPos ground) {
        return Math.max(1, Math.abs(pos.getX() - ground.getX()) + Math.abs(pos.getZ() - ground.getZ()));
    }

    public static boolean isCollapsibleSolid(BlockState state) { return BlockPhysics.category(state) == BlockCategory.STRUCTURAL; }

    private boolean distanceTooLarge(BlockPos origin, BlockPos pos) {
        return Math.abs(origin.getX() - pos.getX()) > config.maxScanRadius || Math.abs(origin.getY() - pos.getY()) > config.maxScanRadius || Math.abs(origin.getZ() - pos.getZ()) > config.maxScanRadius;
    }

    private boolean hasExternalDownSupport(ServerLevel level, Map<BlockPos, NodeData> nodes, BlockPos pos) {
        if (pos.getY() <= level.getMinY()) return true;
        BlockPos below = pos.below();
        return !nodes.containsKey(below) && BlockPhysics.category(level.getBlockState(below)) == BlockCategory.STRUCTURAL;
    }

    record NodeData(StrengthProfile profile, double effectiveMass, List<BlockPos> attached) {}
    private record Region(Map<BlockPos, NodeData> nodes, boolean limitReached) {}
    record Parent(BlockPos pos, SupportEdgeType edge, int momentArm) {}
    record SupportPath(int distance, BlockPos ground) {}
    private record Attached(List<BlockPos> positions, boolean truncated) {}
}
