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

        Map<BlockPos, Integer> distance = supportDistances(level, region.nodes);
        Map<BlockPos, LoadTree.Node> tree = new LinkedHashMap<>();
        for (var entry : region.nodes.entrySet()) {
            StrengthProfile p = entry.getValue().profile;
            tree.put(entry.getKey(), new LoadTree.Node(entry.getKey().toShortString(), entry.getValue().effectiveMass, p.carryKg(), p.shearKg(), p.tensionKg()));
        }
        for (BlockPos pos : region.nodes.keySet()) {
            Parent parent = chooseParent(region.nodes, distance, pos);
            if (parent != null) tree.get(pos).attach(tree.get(parent.pos), parent.edge, parent.momentArm);
        }

        Set<BlockPos> failed = new HashSet<>();
        for (BlockPos pos : region.nodes.keySet()) if (!distance.containsKey(pos)) failed.add(pos);
        Map<String, BlockPos> byId = new HashMap<>();
        tree.forEach((pos, node) -> byId.put(node.id(), pos));
        for (LoadTree.Node node : LoadTree.evaluate(new ArrayList<>(tree.values()))) failed.add(byId.get(node.id()));

        List<BlockPos> collapse = new ArrayList<>();
        for (BlockPos pos : failed) {
            collapse.add(pos.immutable());
            for (BlockPos attached : region.nodes.get(pos).attached) collapse.add(attached.immutable());
        }
        SupportStatus status = collapse.isEmpty() ? SupportStatus.SUPPORTED : SupportStatus.FAILED;
        return new SupportScanResult(origin.immutable(), region.nodes.size(), List.copyOf(collapse), status);
    }

    private Region collectSolidRegion(ServerLevel level, BlockPos origin) {
        Map<BlockPos, NodeData> nodes = new LinkedHashMap<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin.immutable());
        for (Direction direction : Direction.values()) queue.add(origin.relative(direction).immutable());
        boolean limitReached = false;
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (!visited.add(pos) || distanceTooLarge(origin, pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (BlockPhysics.category(state) != BlockCategory.STRUCTURAL) continue;
            var profile = BlockStrengths.get(state.getBlock());
            if (profile.isEmpty()) continue;
            if (nodes.size() >= config.scanCap) { limitReached = true; break; }
            List<BlockPos> attached = attachedTo(level, pos);
            double mass = StateMassModifiers.apply(profile.get().massKg(), state);
            for (BlockPos attachment : attached) {
                BlockState attachedState = level.getBlockState(attachment);
                mass += BlockWeights.get(attachedState.getBlock()).orElse(0.0);
            }
            nodes.put(pos, new NodeData(profile.get(), mass, attached));
            for (Direction direction : Direction.values()) queue.addLast(pos.relative(direction).immutable());
        }
        return new Region(nodes, limitReached);
    }

    private List<BlockPos> attachedTo(ServerLevel level, BlockPos host) {
        List<BlockPos> result = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            BlockPos pos = host.relative(direction);
            BlockState state = level.getBlockState(pos);
            if (BlockPhysics.category(state) == BlockCategory.ATTACHED && AttachmentResolver.host(pos, state).equals(host)) result.add(pos.immutable());
        }
        return result;
    }

    private Map<BlockPos, Integer> supportDistances(ServerLevel level, Map<BlockPos, NodeData> nodes) {
        Map<BlockPos, Integer> distance = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos pos : nodes.keySet()) {
            if (hasExternalDownSupport(level, nodes, pos)) { distance.put(pos, 0); queue.add(pos); }
        }
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            int nextDistance = distance.get(pos) + 1;
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (nodes.containsKey(next) && !distance.containsKey(next)) { distance.put(next, nextDistance); queue.addLast(next); }
            }
        }
        return distance;
    }

    private Parent chooseParent(Map<BlockPos, NodeData> nodes, Map<BlockPos, Integer> distance, BlockPos pos) {
        Integer own = distance.get(pos);
        if (own == null || own == 0) return null;
        BlockPos below = pos.below();
        if (isCloser(distance, below, own)) return new Parent(below, SupportEdgeType.DOWN, 1);
        Parent arch = archParent(nodes, distance, pos, own, Direction.NORTH, Direction.SOUTH);
        if (arch == null) arch = archParent(nodes, distance, pos, own, Direction.EAST, Direction.WEST);
        if (arch != null) return arch;
        BlockPos best = null;
        double bestShear = -1;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(direction);
            if (isCloser(distance, side, own) && nodes.get(side).profile.shearKg() > bestShear) { best = side; bestShear = nodes.get(side).profile.shearKg(); }
        }
        if (best != null) return new Parent(best, SupportEdgeType.SIDE, own);
        BlockPos above = pos.above();
        return isCloser(distance, above, own) ? new Parent(above, SupportEdgeType.HANG, 1) : null;
    }

    private Parent archParent(Map<BlockPos, NodeData> nodes, Map<BlockPos, Integer> distance, BlockPos pos, int own, Direction a, Direction b) {
        BlockPos pa = pos.relative(a), pb = pos.relative(b);
        if (!isCloser(distance, pa, own) || !isCloser(distance, pb, own)) return null;
        int span = distance.get(pa) + distance.get(pb) + 2;
        int cap = Math.min(nodes.get(pa).profile.maxArchSpan, nodes.get(pb).profile.maxArchSpan);
        return span <= cap ? new Parent(nodes.get(pa).profile.carryKg() >= nodes.get(pb).profile.carryKg() ? pa : pb, SupportEdgeType.ARCH, 1) : null;
    }

    private static boolean isCloser(Map<BlockPos, Integer> distance, BlockPos pos, int own) {
        Integer candidate = distance.get(pos);
        return candidate != null && candidate < own;
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

    private record NodeData(StrengthProfile profile, double effectiveMass, List<BlockPos> attached) {}
    private record Region(Map<BlockPos, NodeData> nodes, boolean limitReached) {}
    private record Parent(BlockPos pos, SupportEdgeType edge, int momentArm) {}
}
