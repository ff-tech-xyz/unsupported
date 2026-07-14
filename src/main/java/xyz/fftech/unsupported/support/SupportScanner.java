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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public final class SupportScanner {
    private static final Direction[] SUPPORT_DIRECTIONS = {
        Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN
    };
    private final UnsupportedConfig config;

    public SupportScanner(UnsupportedConfig config) { this.config = config; }

    public SupportScanResult scan(ServerLevel level, BlockPos origin) {
        return scan(new WorldView() {
            @Override public boolean isLoaded(BlockPos pos) { return level.hasChunkAt(pos); }
            @Override public BlockState getBlockState(BlockPos pos) { return level.getBlockState(pos); }
            @Override public int getMinY() { return level.getMinY(); }
        }, origin);
    }

    SupportScanResult scan(WorldView world, BlockPos origin) {
        Region region = collectSolidRegion(world, origin);
        if (region.incomplete != null) return incomplete(origin, region.budgetCount, region.incomplete);
        if (region.nodes.isEmpty()) return new SupportScanResult(origin.immutable(), region.budgetCount, List.of(), SupportStatus.SUPPORTED);

        Paths pathsResult = supportPaths(world, region.nodes);
        if (pathsResult.incomplete != null) return incomplete(origin, region.budgetCount, pathsResult.incomplete);
        Map<BlockPos, SupportPath> paths = pathsResult.paths;
        Map<BlockPos, LoadTree.Node> tree = new LinkedHashMap<>();
        for (var entry : region.nodes.entrySet()) {
            StrengthProfile profile = entry.getValue().profile;
            tree.put(entry.getKey(), new LoadTree.Node(entry.getKey().toShortString(), entry.getValue().effectiveMass,
                profile.carryKg(), profile.shearKg(), profile.tensionKg()));
        }
        for (BlockPos pos : region.nodes.keySet()) {
            Parent parent = chooseParent(region.nodes, paths, pos);
            if (parent == null) continue;
            LoadTree.Node parentNode = tree.get(parent.pos);
            if (parentNode == null) {
                if (!world.isLoaded(parent.pos)) return incomplete(origin, region.budgetCount, SupportStatus.UNLOADED);
                BlockState supportState = world.getBlockState(parent.pos);
                StrengthProfile profile = BlockStrengths.get(supportState.getBlock()).orElse(null);
                if (profile != null) {
                    parentNode = new LoadTree.Node("external:" + parent.pos.toShortString(), 0,
                        profile.carryKg(), profile.shearKg(), profile.tensionKg());
                }
            }
            if (parentNode != null) tree.get(pos).attach(parentNode, parent.edge, parent.momentArm);
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
        collapse.sort(Comparator.comparingLong((BlockPos pos) -> pos.getY())
            .thenComparingLong(pos -> pos.getX()).thenComparingLong(pos -> pos.getZ()));
        SupportStatus status = collapse.isEmpty() ? SupportStatus.SUPPORTED : SupportStatus.FAILED;
        return new SupportScanResult(origin.immutable(), region.budgetCount, List.copyOf(collapse), status);
    }

    private SupportScanResult incomplete(BlockPos origin, int count, SupportStatus status) {
        return new SupportScanResult(origin.immutable(), count, List.of(), status);
    }

    private Region collectSolidRegion(WorldView world, BlockPos origin) {
        Map<BlockPos, NodeData> nodes = new LinkedHashMap<>();
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> claimedAttachments = new HashSet<>();
        ScanBudget budget = new ScanBudget(config.scanCap);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin.immutable());
        if (!world.isLoaded(origin)) return new Region(nodes, budget.size(), SupportStatus.UNLOADED);
        if (BlockPhysics.category(world.getBlockState(origin)) != BlockCategory.STRUCTURAL) {
            for (Direction direction : Direction.values()) queue.add(origin.relative(direction).immutable());
        }
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (!visited.add(pos)) continue;
            if (!world.isLoaded(pos)) return new Region(nodes, budget.size(), SupportStatus.UNLOADED);
            if (distanceTooLarge(origin, pos)) {
                if (BlockPhysics.category(world.getBlockState(pos)) == BlockCategory.STRUCTURAL) {
                    return new Region(nodes, budget.size(), SupportStatus.RADIUS_REACHED);
                }
                continue;
            }
            BlockState state = world.getBlockState(pos);
            if (BlockPhysics.category(state) != BlockCategory.STRUCTURAL) continue;
            var profile = BlockStrengths.get(state.getBlock());
            if (profile.isEmpty()) continue;
            if (!budget.claim(pos)) return new Region(nodes, budget.size(), SupportStatus.LIMIT_REACHED);

            Attached attachedResult = attachedTo(world, origin, pos, budget, claimedAttachments);
            if (attachedResult.incomplete != null) return new Region(nodes, budget.size(), attachedResult.incomplete);
            double mass = StateMassModifiers.apply(profile.get().massKg(), state);
            for (BlockPos attachment : attachedResult.positions) {
                if (!world.isLoaded(attachment)) return new Region(nodes, budget.size(), SupportStatus.UNLOADED);
                BlockState attachedState = world.getBlockState(attachment);
                mass += StateMassModifiers.apply(BlockWeights.get(attachedState.getBlock()).orElse(0.0), attachedState);
            }
            nodes.put(pos, new NodeData(profile.get(), mass, attachedResult.positions));
            queue.addLast(pos.above().immutable());

            BlockPos below = pos.below();
            if (!world.isLoaded(below)) return new Region(nodes, budget.size(), SupportStatus.UNLOADED);
            BlockState belowState = world.getBlockState(below);
            boolean downwardContinuation = BlockPhysics.category(belowState) == BlockCategory.STRUCTURAL
                && belowState.getBlock() == state.getBlock();
            if (downwardContinuation) queue.addLast(below.immutable());
            boolean externalDownSupport = !downwardContinuation
                && BlockPhysics.category(belowState) == BlockCategory.STRUCTURAL;
            if (!externalDownSupport) {
                for (Direction direction : Direction.Plane.HORIZONTAL) queue.addLast(pos.relative(direction).immutable());
            }
        }
        return new Region(nodes, budget.size(), null);
    }

    private Attached attachedTo(WorldView world, BlockPos origin, BlockPos host, ScanBudget budget,
                                Set<BlockPos> claimedAttachments) {
        LinkedHashSet<BlockPos> result = new LinkedHashSet<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (Direction direction : Direction.values()) queue.add(host.relative(direction).immutable());
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (!visited.add(pos)) continue;
            if (!world.isLoaded(pos)) return new Attached(List.copyOf(result), SupportStatus.UNLOADED);
            BlockState state = world.getBlockState(pos);
            if (BlockPhysics.category(state) != BlockCategory.ATTACHED) continue;
            if (distanceTooLarge(origin, pos)) return new Attached(List.copyOf(result), SupportStatus.RADIUS_REACHED);
            List<BlockPos> candidates = AttachmentResolver.hosts(pos, state);
            if (candidates.isEmpty()) return new Attached(List.copyOf(result), SupportStatus.UNTOUCHED_FALLBACK);
            boolean hosted = candidates.contains(host);
            if (!hosted) for (BlockPos candidate : candidates) if (result.contains(candidate)) { hosted = true; break; }
            if (!hosted) continue;
            if (claimedAttachments.contains(pos)) continue;
            if (!budget.claim(pos)) return new Attached(List.copyOf(result), SupportStatus.LIMIT_REACHED);
            claimedAttachments.add(pos);
            result.add(pos.immutable());
            for (Direction direction : Direction.values()) queue.addLast(pos.relative(direction).immutable());
        }
        return new Attached(List.copyOf(result), null);
    }

    Paths supportPaths(WorldView world, Map<BlockPos, NodeData> nodes) {
        Map<BlockPos, SupportPath> paths = new HashMap<>();
        PriorityQueue<PathVisit> queue = new PriorityQueue<>(Comparator
            .comparingInt((PathVisit visit) -> visit.path.horizontalArm)
            .thenComparingInt(visit -> visit.path.distance)
            .thenComparingInt(visit -> visit.pos.getY())
            .thenComparingInt(visit -> visit.pos.getX())
            .thenComparingInt(visit -> visit.pos.getZ()));
        for (BlockPos pos : nodes.keySet()) {
            if (pos.getY() <= world.getMinY()) {
                SupportPath path = new SupportPath(0, 0, pos);
                paths.put(pos, path);
                queue.add(new PathVisit(pos, path));
                continue;
            }
            BlockPos below = pos.below();
            if (!world.isLoaded(below)) return new Paths(paths, SupportStatus.UNLOADED);
            if (!nodes.containsKey(below) && BlockPhysics.category(world.getBlockState(below)) == BlockCategory.STRUCTURAL) {
                SupportPath path = new SupportPath(0, 0, pos);
                paths.put(pos, path);
                queue.add(new PathVisit(pos, path));
            }
        }
        while (!queue.isEmpty()) {
            PathVisit visit = queue.remove();
            BlockPos pos = visit.pos;
            SupportPath path = paths.get(pos);
            if (!visit.path.equals(path)) continue;
            for (Direction direction : SUPPORT_DIRECTIONS) {
                BlockPos next = pos.relative(direction);
                if (!nodes.containsKey(next)) continue;
                SupportPath candidate = advancePath(path, direction, next);
                SupportPath current = paths.get(next);
                if (!betterPath(candidate, current)) continue;
                paths.put(next, candidate);
                queue.add(new PathVisit(next, candidate));
            }
        }
        return new Paths(paths, null);
    }

    static SupportPath advancePath(SupportPath parent, Direction direction, BlockPos next) {
        if (direction == Direction.UP) return new SupportPath(parent.distance + 1, 0, next.immutable());
        int horizontalArm = parent.horizontalArm + (direction.getAxis().isHorizontal() ? 1 : 0);
        return new SupportPath(parent.distance + 1, horizontalArm, parent.ground);
    }

    private static boolean betterPath(SupportPath candidate, SupportPath current) {
        if (current == null) return true;
        if (candidate.horizontalArm != current.horizontalArm) return candidate.horizontalArm < current.horizontalArm;
        if (candidate.distance != current.distance) return candidate.distance < current.distance;
        if (candidate.ground.getY() != current.ground.getY()) return candidate.ground.getY() < current.ground.getY();
        if (candidate.ground.getX() != current.ground.getX()) return candidate.ground.getX() < current.ground.getX();
        return candidate.ground.getZ() < current.ground.getZ();
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
            if (isCloser(paths, side, own) && nodes.get(side).profile.shearKg() > bestShear) {
                best = side;
                bestShear = nodes.get(side).profile.shearKg();
            }
        }
        if (best != null) return new Parent(best, SupportEdgeType.SIDE, paths.get(best).horizontalArm + 1);
        BlockPos above = pos.above();
        return isCloser(paths, above, own) ? new Parent(above, SupportEdgeType.HANG, 1) : null;
    }

    private static Parent archParent(Map<BlockPos, NodeData> nodes, Map<BlockPos, SupportPath> paths,
                                     BlockPos pos, int own, Direction a, Direction b) {
        BlockPos pa = pos.relative(a), pb = pos.relative(b);
        if (!isCloser(paths, pa, own) || !isCloser(paths, pb, own)) return null;
        int span = paths.get(pa).distance + paths.get(pb).distance + 2;
        int cap = Math.min(nodes.get(pa).profile.maxArchSpan, nodes.get(pb).profile.maxArchSpan);
        return span <= cap ? new Parent(nodes.get(pa).profile.carryKg() >= nodes.get(pb).profile.carryKg() ? pa : pb,
            SupportEdgeType.ARCH, 1) : null;
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
        return Math.abs(origin.getX() - pos.getX()) > config.maxScanRadius
            || Math.abs(origin.getY() - pos.getY()) > config.maxScanRadius
            || Math.abs(origin.getZ() - pos.getZ()) > config.maxScanRadius;
    }

    interface WorldView {
        boolean isLoaded(BlockPos pos);
        BlockState getBlockState(BlockPos pos);
        int getMinY();
    }

    record NodeData(StrengthProfile profile, double effectiveMass, List<BlockPos> attached) {}
    private record Region(Map<BlockPos, NodeData> nodes, int budgetCount, SupportStatus incomplete) {}
    record Paths(Map<BlockPos, SupportPath> paths, SupportStatus incomplete) {}
    record Parent(BlockPos pos, SupportEdgeType edge, int momentArm) {}
    record SupportPath(int distance, int horizontalArm, BlockPos ground) {
        SupportPath(int distance, BlockPos ground) { this(distance, distance, ground); }
    }
    private record PathVisit(BlockPos pos, SupportPath path) {}
    private record Attached(List<BlockPos> positions, SupportStatus incomplete) {}

    private static final class ScanBudget {
        private final int cap;
        private final Set<BlockPos> claimed = new HashSet<>();

        private ScanBudget(int cap) { this.cap = cap; }

        private boolean claim(BlockPos pos) {
            if (claimed.contains(pos)) return true;
            if (claimed.size() >= cap) return false;
            claimed.add(pos.immutable());
            return true;
        }

        private int size() { return claimed.size(); }
    }
}
