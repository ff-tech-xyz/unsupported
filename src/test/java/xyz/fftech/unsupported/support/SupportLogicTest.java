package xyz.fftech.unsupported.support;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import xyz.fftech.unsupported.StrengthProfile;

import java.util.List;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SupportLogicTest {
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        assertClose(25.0, StateMassModifiers.apply(100.0, "snow", 2, false));
        assertClose(200.0, StateMassModifiers.apply(100.0, "slab_double", 1, false));
        assertClose(400.0, StateMassModifiers.apply(100.0, "candle", 4, false));
        assertClose(1_100.0, StateMassModifiers.apply(100.0, "plain", 1, true));

        LoadTree.Node root = new LoadTree.Node("root", 10, 50, 50, 50);
        LoadTree.Node middle = new LoadTree.Node("middle", 40, 100, 100, 100);
        LoadTree.Node leaf = new LoadTree.Node("leaf", 20, 100, 100, 100);
        middle.attach(root, SupportEdgeType.DOWN, 1);
        leaf.attach(middle, SupportEdgeType.DOWN, 1);
        List<LoadTree.Node> failed = LoadTree.evaluate(List.of(root, middle, leaf));
        assertClose(70, root.load());
        assertClose(60, middle.load());
        assertEquals(List.of("middle", "leaf"), failed.stream().map(LoadTree.Node::id).toList());
        assertEquals(SupportEdgeType.DOWN, LoadTree.firstAvailable(true, true, true, true));
        assertEquals(SupportEdgeType.ARCH, LoadTree.firstAvailable(false, true, true, true));
        assertEquals(SupportEdgeType.SIDE, LoadTree.firstAvailable(false, false, true, true));
        assertEquals(SupportEdgeType.HANG, LoadTree.firstAvailable(false, false, false, true));
        externalSupportCapacityIsChecked();
        sideMomentUsesHorizontalDistance();
        deepTreesAreIterative();
        attemptedWorkConsumesBudget();
        actualStatesDriveMassAndAttachmentDirection();
        scannerBuildsDownParentBeforeSideParent();
        benchmarkBoundedLoadTree();
        System.out.println("SupportLogicTest: 10 passed");
    }

    private static void externalSupportCapacityIsChecked() {
        LoadTree.Node dirt = new LoadTree.Node("dirt", 0, 1_000, 1_000, 1_000);
        LoadTree.Node cobble = new LoadTree.Node("cobble", 1_100, 100_000, 100_000, 100_000);
        cobble.attach(dirt, SupportEdgeType.DOWN, 1);
        assertEquals(List.of("cobble"), LoadTree.evaluate(List.of(cobble)).stream().map(LoadTree.Node::id).toList());
    }

    private static void sideMomentUsesHorizontalDistance() {
        LoadTree.Node support = new LoadTree.Node("support", 0, 10_000, 250, 10_000);
        LoadTree.Node beam = new LoadTree.Node("beam", 100, 10_000, 10_000, 10_000);
        beam.attach(support, SupportEdgeType.SIDE, 3);
        assertEquals(List.of("beam"), LoadTree.evaluate(List.of(beam)).stream().map(LoadTree.Node::id).toList());
    }

    private static void deepTreesAreIterative() {
        ArrayList<LoadTree.Node> nodes = chain(4_096);
        assertEquals(List.of(), LoadTree.evaluate(nodes));
        assertClose(4_096, nodes.get(0).load());
    }

    private static void attemptedWorkConsumesBudget() {
        ArrayDeque<Integer> queue = new ArrayDeque<>(List.of(1, 2, 3, 4));
        ArrayList<Integer> attempted = new ArrayList<>();
        CollapseQueue.drainAttempts(queue, 2, attempted::add);
        assertEquals(List.of(1, 2), attempted);
        assertEquals(List.of(3, 4), List.copyOf(queue));
    }

    private static void actualStatesDriveMassAndAttachmentDirection() {
        assertClose(400, StateMassModifiers.apply(100, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 4)));
        BlockPos chain = new BlockPos(10, 20, 30);
        assertEquals(List.of(chain.east(), chain.west()), AttachmentResolver.hosts(chain,
            Blocks.IRON_CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.X)));
        BlockPos bell = new BlockPos(0, 5, 0);
        assertEquals(List.of(bell.above()), AttachmentResolver.hosts(bell,
            Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING)));
        assertEquals(List.of(bell.east()), AttachmentResolver.hosts(bell, Blocks.BELL.defaultBlockState()
            .setValue(BellBlock.ATTACHMENT, BellAttachType.SINGLE_WALL).setValue(BellBlock.FACING, Direction.EAST)));
        assertEquals(List.of(bell.east(), bell.west()), AttachmentResolver.hosts(bell, Blocks.BELL.defaultBlockState()
            .setValue(BellBlock.ATTACHMENT, BellAttachType.DOUBLE_WALL).setValue(BellBlock.FACING, Direction.EAST)));
        assertEquals(3, SupportScanner.horizontalDistance(new BlockPos(2, 40, 1), new BlockPos(0, 0, 0)));
    }

    private static void scannerBuildsDownParentBeforeSideParent() {
        StrengthProfile profile = new StrengthProfile();
        profile.C = profile.S = profile.T = 10_000;
        BlockPos child = new BlockPos(1, 1, 0);
        BlockPos below = child.below();
        BlockPos side = child.west();
        Map<BlockPos, SupportScanner.NodeData> nodes = new LinkedHashMap<>();
        nodes.put(child, new SupportScanner.NodeData(profile, 1, List.of()));
        nodes.put(below, new SupportScanner.NodeData(profile, 1, List.of()));
        nodes.put(side, new SupportScanner.NodeData(profile, 1, List.of()));
        Map<BlockPos, SupportScanner.SupportPath> paths = Map.of(
            child, new SupportScanner.SupportPath(1, below),
            below, new SupportScanner.SupportPath(0, below),
            side, new SupportScanner.SupportPath(0, below));
        SupportScanner.Parent parent = SupportScanner.chooseParent(nodes, paths, child);
        assertEquals(below, parent.pos());
        assertEquals(SupportEdgeType.DOWN, parent.edge());
    }

    private static void benchmarkBoundedLoadTree() {
        benchmark(256, 10_000);
        benchmark(4_096, 250);
    }

    private static ArrayList<LoadTree.Node> chain(int size) {
        ArrayList<LoadTree.Node> nodes = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            LoadTree.Node node = new LoadTree.Node(Integer.toString(i), 1, 10_000_000, 10_000_000, 10_000_000);
            if (!nodes.isEmpty()) node.attach(nodes.get(i - 1), SupportEdgeType.DOWN, 1);
            nodes.add(node);
        }
        return nodes;
    }

    private static void benchmark(int size, int iterations) {
        ArrayList<LoadTree.Node> nodes = chain(size);
        for (int i = 0; i < 20; i++) LoadTree.evaluate(nodes);
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) LoadTree.evaluate(nodes);
        double micros = (System.nanoTime() - start) / (double) iterations / 1_000.0;
        System.out.printf("Pure load-tree benchmark: %d nodes average %.3f us%n", size, micros);
    }

    private static void assertClose(double expected, double actual) {
        if (Math.abs(expected - actual) > 0.0001) throw new AssertionError(expected + " != " + actual);
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
    }
}