package xyz.fftech.unsupported.support;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import xyz.fftech.unsupported.UnsupportedConfig;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public final class CollapseQueue {
    private final UnsupportedConfig config;
    private final SupportScanner scanner;
    private final Logger logger;
    private final ArrayDeque<BlockPos> scanQueue = new ArrayDeque<>();
    private final Set<BlockPos> queuedScans = new HashSet<>();
    private final ArrayDeque<BlockPos> fallingQueue = new ArrayDeque<>();
    private final Set<BlockPos> queuedFalls = new HashSet<>();

    public CollapseQueue(UnsupportedConfig config, Logger logger) {
        this.config = config;
        this.scanner = new SupportScanner(config);
        this.logger = logger;
    }

    public void enqueueScanAround(BlockPos origin) {
        enqueueScan(origin);
        enqueueScan(origin.above());
        enqueueScan(origin.below());
        enqueueScan(origin.north());
        enqueueScan(origin.south());
        enqueueScan(origin.east());
        enqueueScan(origin.west());
    }

    public SupportScanResult scanOnly(ServerLevel level, BlockPos origin) {
        return scanner.scan(level, origin);
    }

    public void tick(ServerLevel level) {
        if (!config.collapseEnabled) {
            scanQueue.clear();
            queuedScans.clear();
            fallingQueue.clear();
            queuedFalls.clear();
            return;
        }

        int scansThisTick = 0;
        while (!scanQueue.isEmpty() && scansThisTick < 8) {
            BlockPos origin = scanQueue.removeFirst();
            queuedScans.remove(origin);
            SupportScanResult result = scanner.scan(level, origin);
            if (result.limitReached()) {
                continue;
            }
            for (BlockPos failed : result.failedPositions()) {
                enqueueFall(failed);
            }
            scansThisTick++;
        }

        int collapsed = 0;
        while (!fallingQueue.isEmpty() && collapsed < config.maxCollapseBlocksPerTick) {
            BlockPos pos = fallingQueue.removeFirst();
            queuedFalls.remove(pos);
            if (collapseBlock(level, pos)) {
                collapsed++;
            }
        }
    }

    private void enqueueScan(BlockPos pos) {
        BlockPos immutable = pos.immutable();
        if (queuedScans.add(immutable)) {
            scanQueue.addLast(immutable);
        }
    }

    private void enqueueFall(BlockPos pos) {
        BlockPos immutable = pos.immutable();
        if (queuedFalls.add(immutable)) {
            fallingQueue.addLast(immutable);
        }
    }

    private boolean collapseBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!SupportScanner.isCollapsibleSolid(state) || level.getBlockEntity(pos) != null) {
            return false;
        }
        try {
            FallingBlockEntity entity = FallingBlockEntity.fall(level, pos, state);
            entity.disableDrop();
            return true;
        } catch (RuntimeException e) {
            logger.debug("Unsupported left {} untouched because it could not become a falling block", pos, e);
            return false;
        }
    }
}
