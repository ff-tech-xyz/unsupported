package xyz.fftech.unsupported.support;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
        sideArmFollowsSelectedHorizontalParentPath();
        sideParentMustBeRecordedPathPredecessor();
        scannerGroundingFixtureUsesSelectedHorizontalParentArm();
        downGroundingResetsArmAndOnlyHorizontalStepsIncreaseIt();
        benchmarkBoundedLoadTree();
        System.out.println("SupportLogicTest: 13 passed");
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
        assertEquals(List.of(chain.west()), AttachmentResolver.hosts(chain,
            Blocks.IRON_CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.X)));
        assertEquals(List.of(chain.above()), AttachmentResolver.hosts(chain,
            Blocks.IRON_CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y)));
        BlockPos bell = new BlockPos(0, 5, 0);
        assertEquals(List.of(bell.above()), AttachmentResolver.hosts(bell,
            Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING)));
        assertEquals(List.of(bell.east()), AttachmentResolver.hosts(bell, Blocks.BELL.defaultBlockState()
            .setValue(BellBlock.ATTACHMENT, BellAttachType.SINGLE_WALL).setValue(BellBlock.FACING, Direction.EAST)));
        assertEquals(List.of(bell.east(), bell.west()), AttachmentResolver.hosts(bell, Blocks.BELL.defaultBlockState()
            .setValue(BellBlock.ATTACHMENT, BellAttachType.DOUBLE_WALL).setValue(BellBlock.FACING, Direction.EAST)));
        assertClose(1_400, StateMassModifiers.apply(100, Blocks.SEA_PICKLE.defaultBlockState()
            .setValue(SeaPickleBlock.PICKLES, 4).setValue(BlockStateProperties.WATERLOGGED, true)));
        assertClose(400, StateMassModifiers.apply(100, Blocks.TURTLE_EGG.defaultBlockState()
            .setValue(TurtleEggBlock.EGGS, 4)));
        assertEquals(List.of(bell.below()), AttachmentResolver.hosts(bell, Blocks.SEA_PICKLE.defaultBlockState()));
        assertEquals(List.of(bell.below()), AttachmentResolver.hosts(bell, Blocks.TURTLE_EGG.defaultBlockState()));
        assertEquals(List.of(), AttachmentResolver.hosts(bell, Blocks.CHAIN_COMMAND_BLOCK.defaultBlockState()));
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

    private static void sideArmFollowsSelectedHorizontalParentPath() {
        StrengthProfile strongSide = new StrengthProfile();
        strongSide.C = strongSide.S = strongSide.T = 10_000;
        StrengthProfile weakSide = new StrengthProfile();
        weakSide.C = weakSide.T = 10_000;
        weakSide.S = 1;
        BlockPos child = new BlockPos(2, 30, 0);
        BlockPos selectedSide = child.west();
        BlockPos competingSide = child.east();
        Map<BlockPos, SupportScanner.NodeData> nodes = new LinkedHashMap<>();
        nodes.put(child, new SupportScanner.NodeData(strongSide, 1, List.of()));
        nodes.put(selectedSide, new SupportScanner.NodeData(strongSide, 1, List.of()));
        nodes.put(competingSide, new SupportScanner.NodeData(weakSide, 1, List.of()));
        Map<BlockPos, SupportScanner.SupportPath> paths = Map.of(
            child, new SupportScanner.SupportPath(2, new BlockPos(10, -20, 0)),
            selectedSide, new SupportScanner.SupportPath(1, new BlockPos(0, 0, 0)),
            competingSide, new SupportScanner.SupportPath(1, new BlockPos(3, 29, 0)));

        SupportScanner.Parent parent = SupportScanner.chooseParent(nodes, paths, child);

        assertEquals(selectedSide, parent.pos());
        assertEquals(SupportEdgeType.SIDE, parent.edge());
        assertEquals(2, parent.momentArm());
    }

    private static void sideParentMustBeRecordedPathPredecessor() {
        StrengthProfile weak = new StrengthProfile();
        weak.C = weak.T = 10_000;
        weak.S = 1;
        StrengthProfile strong = new StrengthProfile();
        strong.C = strong.S = strong.T = 10_000;
        BlockPos child = new BlockPos(0, 10, 0);
        BlockPos weakWest = child.west();
        BlockPos strongEast = child.east();
        BlockPos ground = new BlockPos(-2, 0, 0);
        Map<BlockPos, SupportScanner.NodeData> nodes = new LinkedHashMap<>();
        nodes.put(child, new SupportScanner.NodeData(strong, 1, List.of()));
        nodes.put(weakWest, new SupportScanner.NodeData(weak, 1, List.of()));
        nodes.put(strongEast, new SupportScanner.NodeData(strong, 1, List.of()));
        Map<BlockPos, SupportScanner.SupportPath> paths = Map.of(
            child, new SupportScanner.SupportPath(2, 2, ground),
            weakWest, new SupportScanner.SupportPath(1, 1, ground),
            strongEast, new SupportScanner.SupportPath(1, 10, new BlockPos(10, 0, 0)));

        SupportScanner.Parent parent = SupportScanner.chooseParent(nodes, paths, child);

        assertEquals(weakWest, parent.pos());
        assertEquals(SupportEdgeType.SIDE, parent.edge());
        assertEquals(2, parent.momentArm());
    }

    private static void scannerGroundingFixtureUsesSelectedHorizontalParentArm() {
        StrengthProfile strong = new StrengthProfile();
        strong.C = strong.S = strong.T = 10_000;
        StrengthProfile weak = new StrengthProfile();
        weak.C = weak.T = 10_000;
        weak.S = 1;
        Map<BlockPos, SupportScanner.NodeData> nodes = new LinkedHashMap<>();
        for (int y = 1; y <= 10; y++) {
            BlockPos pos = new BlockPos(0, y, 0);
            nodes.put(pos, new SupportScanner.NodeData(strong, 1, List.of()));
        }
        BlockPos selectedSide = new BlockPos(1, 10, 0);
        BlockPos child = new BlockPos(2, 10, 0);
        BlockPos competingRoot = new BlockPos(5, 9, 0);
        BlockPos competingTop = competingRoot.above();
        BlockPos competingBridge = new BlockPos(4, 10, 0);
        BlockPos competingSide = new BlockPos(3, 10, 0);
        nodes.put(selectedSide, new SupportScanner.NodeData(strong, 1, List.of()));
        nodes.put(child, new SupportScanner.NodeData(strong, 1, List.of()));
        nodes.put(competingRoot, new SupportScanner.NodeData(weak, 1, List.of()));
        nodes.put(competingTop, new SupportScanner.NodeData(weak, 1, List.of()));
        nodes.put(competingBridge, new SupportScanner.NodeData(weak, 1, List.of()));
        nodes.put(competingSide, new SupportScanner.NodeData(weak, 1, List.of()));
        Map<BlockPos, net.minecraft.world.level.block.state.BlockState> supports = Map.of(
            new BlockPos(0, 0, 0), Blocks.STONE.defaultBlockState(),
            new BlockPos(5, 8, 0), Blocks.STONE.defaultBlockState());
        SupportScanner.WorldView world = new SupportScanner.WorldView() {
            @Override public boolean isLoaded(BlockPos pos) { return true; }
            @Override public net.minecraft.world.level.block.state.BlockState getBlockState(BlockPos pos) {
                return supports.getOrDefault(pos, Blocks.AIR.defaultBlockState());
            }
            @Override public int getMinY() { return -64; }
        };

        Map<BlockPos, SupportScanner.SupportPath> paths = new SupportScanner(new xyz.fftech.unsupported.UnsupportedConfig())
            .supportPaths(world, nodes).paths();
        SupportScanner.Parent parent = SupportScanner.chooseParent(nodes, paths, child);

        assertEquals(selectedSide, parent.pos());
        assertEquals(SupportEdgeType.SIDE, parent.edge());
        assertEquals(2, parent.momentArm());
    }

    private static void downGroundingResetsArmAndOnlyHorizontalStepsIncreaseIt() {
        BlockPos root = new BlockPos(0, 0, 0);
        SupportScanner.SupportPath path = new SupportScanner.SupportPath(0, root);
        path = SupportScanner.advancePath(path, Direction.UP, root.above());
        path = SupportScanner.advancePath(path, Direction.UP, root.above(2));
        assertEquals(0, path.horizontalArm());
        path = SupportScanner.advancePath(path, Direction.EAST, root.above(2).east());
        path = SupportScanner.advancePath(path, Direction.EAST, root.above(2).east(2));
        assertEquals(2, path.horizontalArm());
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