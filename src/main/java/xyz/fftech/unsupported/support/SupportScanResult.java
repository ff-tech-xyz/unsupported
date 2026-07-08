package xyz.fftech.unsupported.support;

import net.minecraft.core.BlockPos;

import java.util.List;

public record SupportScanResult(BlockPos origin, int scannedCount, List<BlockPos> failedPositions, SupportStatus status) {
    public boolean limitReached() {
        return status == SupportStatus.LIMIT_REACHED;
    }
}
