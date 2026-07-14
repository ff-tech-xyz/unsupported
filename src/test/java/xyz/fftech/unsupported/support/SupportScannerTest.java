package xyz.fftech.unsupported.support;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.fftech.unsupported.BlockPhysics;
import xyz.fftech.unsupported.BlockStrengths;
import xyz.fftech.unsupported.BlockWeights;
import xyz.fftech.unsupported.UnsupportedConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class SupportScannerTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SupportScannerTest.class);

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        BlockWeights.load(LOGGER);
        BlockStrengths.load(LOGGER);
        BlockPhysics.load(LOGGER);

        terrainBoundaryDoesNotFlood();
        capAndRadiusAreExplicitlyUncertain();
        unloadedChunkIsCheckedBeforeWorldRead();
        externalDirtFailsWhileStoneSupports();
        topOriginReachesRealDirtAndStoneSupport();
        middleOriginReachesRealDirtAndStoneSupport();
        attachmentsShareBudgetAndCollapseTogether();
        attachmentRadiusIsMeasuredFromScanOrigin();
        unknownAttachmentIsNotClaimedByEitherStructure();
        deterministicCollapseOutput();
        staleAndRejectedAttemptsConsumeBudgets();
        System.out.println("SupportScannerTest: 11 passed");
    }

    private static void terrainBoundaryDoesNotFlood() {
        FakeWorld world = new FakeWorld();
        for (int x = -20; x <= 20; x++) {
            world.put(new BlockPos(x, 0, 0), Blocks.DIRT.defaultBlockState());
            world.put(new BlockPos(x, 1, 0), Blocks.DIRT.defaultBlockState());
        }
        BlockPos origin = new BlockPos(0, 1, 0);
        world.put(origin.below(), Blocks.STONE.defaultBlockState());
        world.put(origin, Blocks.COBBLESTONE.defaultBlockState());
        world.put(origin.above(), Blocks.COBBLESTONE.defaultBlockState());
        world.put(origin.above(2), Blocks.COBBLESTONE.defaultBlockState());

        SupportScanResult result = scanner(16, 16).scan(world, origin);

        assertEquals(SupportStatus.SUPPORTED, result.status());
        assertEquals(3, result.scannedCount());
        assertEquals(List.of(), result.failedPositions());
    }

    private static void capAndRadiusAreExplicitlyUncertain() {
        FakeWorld capped = new FakeWorld();
        BlockPos capOrigin = new BlockPos(0, 10, 0);
        for (int x = 0; x < 6; x++) capped.put(capOrigin.east(x), Blocks.COBBLESTONE.defaultBlockState());
        SupportScanResult capResult = scanner(4, 16).scan(capped, capOrigin);
        assertEquals(SupportStatus.LIMIT_REACHED, capResult.status());
        assertEquals(List.of(), capResult.failedPositions());

        FakeWorld radius = new FakeWorld();
        BlockPos radiusOrigin = new BlockPos(0, 1, 0);
        radius.put(radiusOrigin.below(), Blocks.STONE.defaultBlockState());
        for (int y = 0; y < 4; y++) radius.put(radiusOrigin.above(y), Blocks.COBBLESTONE.defaultBlockState());
        SupportScanResult radiusResult = scanner(16, 2).scan(radius, radiusOrigin);
        assertEquals(SupportStatus.RADIUS_REACHED, radiusResult.status());
        assertEquals(List.of(), radiusResult.failedPositions());
    }

    private static void unloadedChunkIsCheckedBeforeWorldRead() {
        FakeWorld world = new FakeWorld(pos -> pos.getX() < 16);
        BlockPos origin = new BlockPos(15, 10, 0);
        world.put(origin, Blocks.COBBLESTONE.defaultBlockState());

        SupportScanResult result = scanner(16, 16).scan(world, origin);

        assertEquals(SupportStatus.UNLOADED, result.status());
        assertEquals(List.of(), result.failedPositions());
        assertEquals(0, world.unloadedReads);
    }

    private static void externalDirtFailsWhileStoneSupports() {
        BlockPos origin = new BlockPos(0, 1, 0);
        FakeWorld dirt = column(origin, Blocks.DIRT.defaultBlockState(), 10);
        FakeWorld stone = column(origin, Blocks.STONE.defaultBlockState(), 10);

        SupportScanResult dirtResult = scanner(256, 64).scan(dirt, origin);
        SupportScanResult stoneResult = scanner(256, 64).scan(stone, origin);

        assertEquals(SupportStatus.FAILED, dirtResult.status());
        assertEquals(10, dirtResult.failedPositions().size());
        assertEquals(SupportStatus.SUPPORTED, stoneResult.status());
        assertEquals(List.of(), stoneResult.failedPositions());
    }

    private static void topOriginReachesRealDirtAndStoneSupport() {
        BlockPos base = new BlockPos(0, 1, 0);
        assertColumnFromOrigin(base, base.above(29));
    }

    private static void middleOriginReachesRealDirtAndStoneSupport() {
        BlockPos base = new BlockPos(0, 1, 0);
        assertColumnFromOrigin(base, base.above(14));
    }

    private static void assertColumnFromOrigin(BlockPos base, BlockPos scanOrigin) {
        FakeWorld dirt = column(base, Blocks.DIRT.defaultBlockState(), 30, Blocks.OAK_PLANKS.defaultBlockState());
        FakeWorld stone = column(base, Blocks.STONE.defaultBlockState(), 30, Blocks.OAK_PLANKS.defaultBlockState());

        SupportScanResult dirtResult = scanner(256, 64).scan(dirt, scanOrigin);
        SupportScanResult stoneResult = scanner(256, 64).scan(stone, scanOrigin);

        assertEquals(SupportStatus.FAILED, dirtResult.status());
        assertEquals(30, dirtResult.scannedCount());
        assertEquals(30, dirtResult.failedPositions().size());
        assertEquals(SupportStatus.SUPPORTED, stoneResult.status());
        assertEquals(30, stoneResult.scannedCount());
        assertEquals(List.of(), stoneResult.failedPositions());
    }

    private static void attachmentsShareBudgetAndCollapseTogether() {
        BlockPos origin = new BlockPos(0, 1, 0);
        FakeWorld world = new FakeWorld();
        world.put(origin.below(), Blocks.MUD_BRICK_WALL.defaultBlockState());
        world.put(origin, Blocks.COBBLESTONE.defaultBlockState());
        BlockPos firstChain = origin.east();
        BlockPos secondChain = firstChain.east();
        BlockPos lantern = secondChain.below();
        BlockState horizontalChain = Blocks.IRON_CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.X);
        world.put(firstChain, horizontalChain);
        world.put(secondChain, horizontalChain);
        world.put(lantern, Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));

        SupportScanResult complete = scanner(16, 16).scan(world, origin);
        assertEquals(SupportStatus.FAILED, complete.status());
        assertEquals(4, complete.scannedCount());
        assertEquals(List.of(lantern, origin, firstChain, secondChain), complete.failedPositions());

        SupportScanResult capped = scanner(3, 16).scan(world, origin);
        assertEquals(SupportStatus.LIMIT_REACHED, capped.status());
        assertEquals(3, capped.scannedCount());
        assertEquals(List.of(), capped.failedPositions());
    }

    private static void attachmentRadiusIsMeasuredFromScanOrigin() {
        BlockPos origin = new BlockPos(0, 1, 0);
        FakeWorld world = new FakeWorld();
        world.put(origin.below(), Blocks.MUD_BRICK_WALL.defaultBlockState());
        world.put(origin, Blocks.COBBLESTONE.defaultBlockState());
        BlockState horizontalChain = Blocks.IRON_CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.X);
        world.put(origin.east(), horizontalChain);
        world.put(origin.east(2), horizontalChain);

        SupportScanResult result = scanner(16, 1).scan(world, origin);

        assertEquals(SupportStatus.RADIUS_REACHED, result.status());
        assertEquals(List.of(), result.failedPositions());
    }

    private static void unknownAttachmentIsNotClaimedByEitherStructure() {
        BlockPos left = new BlockPos(0, 1, 0);
        BlockPos unknown = left.east();
        BlockPos right = left.east(2);
        FakeWorld world = new FakeWorld();
        world.put(left.below(), Blocks.DIRT.defaultBlockState());
        world.put(right.below(), Blocks.DIRT.defaultBlockState());
        world.put(left, Blocks.COBBLESTONE.defaultBlockState());
        world.put(unknown, Blocks.CHAIN_COMMAND_BLOCK.defaultBlockState());
        world.put(right, Blocks.COBBLESTONE.defaultBlockState());

        SupportScanResult leftResult = scanner(16, 16).scan(world, left);
        SupportScanResult rightResult = scanner(16, 16).scan(world, right);

        assertEquals(SupportStatus.UNTOUCHED_FALLBACK, leftResult.status());
        assertEquals(List.of(), leftResult.failedPositions());
        assertEquals(SupportStatus.UNTOUCHED_FALLBACK, rightResult.status());
        assertEquals(List.of(), rightResult.failedPositions());
    }

    private static void deterministicCollapseOutput() {
        BlockPos origin = new BlockPos(0, 1, 0);
        FakeWorld world = column(origin, Blocks.DIRT.defaultBlockState());
        SupportScanner scanner = scanner(256, 64);
        List<BlockPos> first = scanner.scan(world, origin).failedPositions();
        List<BlockPos> second = scanner.scan(world, origin).failedPositions();
        assertEquals(first, second);
        assertEquals(origin, first.get(0));
        assertEquals(origin.above(29), first.get(first.size() - 1));
    }

    private static void staleAndRejectedAttemptsConsumeBudgets() {
        ArrayDeque<String> scans = new ArrayDeque<>(List.of("stale", "uncertain", "valid"));
        ArrayList<String> scanAttempts = new ArrayList<>();
        CollapseQueue.drainAttempts(scans, 2, scanAttempts::add);
        assertEquals(List.of("stale", "uncertain"), scanAttempts);
        assertEquals(List.of("valid"), List.copyOf(scans));

        ArrayDeque<String> collapses = new ArrayDeque<>(List.of("air", "never", "unsafe", "valid"));
        ArrayList<String> collapseAttempts = new ArrayList<>();
        CollapseQueue.drainAttempts(collapses, 3, collapseAttempts::add);
        assertEquals(List.of("air", "never", "unsafe"), collapseAttempts);
        assertEquals(List.of("valid"), List.copyOf(collapses));
    }

    private static FakeWorld column(BlockPos origin, BlockState support) {
        return column(origin, support, 30);
    }

    private static FakeWorld column(BlockPos origin, BlockState support, int height) {
        return column(origin, support, height, Blocks.COBBLESTONE.defaultBlockState());
    }

    private static FakeWorld column(BlockPos origin, BlockState support, int height, BlockState columnState) {
        FakeWorld world = new FakeWorld();
        world.put(origin.below(), support);
        for (int y = 0; y < height; y++) world.put(origin.above(y), columnState);
        return world;
    }

    private static SupportScanner scanner(int cap, int radius) {
        UnsupportedConfig config = new UnsupportedConfig();
        config.scanCap = cap;
        config.maxScanRadius = radius;
        return new SupportScanner(config);
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
    }

    private static final class FakeWorld implements SupportScanner.WorldView {
        private final Map<BlockPos, BlockState> states = new HashMap<>();
        private final Predicate<BlockPos> loaded;
        private int unloadedReads;

        private FakeWorld() { this(pos -> true); }
        private FakeWorld(Predicate<BlockPos> loaded) { this.loaded = loaded; }

        private void put(BlockPos pos, BlockState state) { states.put(pos.immutable(), state); }

        @Override
        public boolean isLoaded(BlockPos pos) { return loaded.test(pos); }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (!isLoaded(pos)) {
                unloadedReads++;
                throw new AssertionError("scanner read unloaded position " + pos);
            }
            return states.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public int getMinY() { return -64; }
    }
}
