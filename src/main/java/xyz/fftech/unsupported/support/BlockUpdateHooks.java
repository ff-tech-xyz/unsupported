package xyz.fftech.unsupported.support;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import xyz.fftech.unsupported.UnsupportedConfig;

import java.util.Map;
import java.util.WeakHashMap;

public final class BlockUpdateHooks {
    private static final Map<ServerLevel, CollapseQueue> QUEUES = new WeakHashMap<>();
    private static UnsupportedConfig config;
    private static Logger logger;

    private BlockUpdateHooks() {}

    public static void register(UnsupportedConfig loadedConfig, Logger loadedLogger) {
        config = loadedConfig;
        logger = loadedLogger;

        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (level instanceof ServerLevel serverLevel) {
                queue(serverLevel).enqueueScanAround(pos);
            }
        });

        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (level instanceof ServerLevel serverLevel) {
                BlockPos target = hitResult.getBlockPos().relative(hitResult.getDirection());
                queue(serverLevel).enqueueScanAround(target);
            }
            return InteractionResult.PASS;
        });

        ServerTickEvents.END_LEVEL_TICK.register(level -> queue(level).tick(level));
    }

    public static SupportScanResult scanOnly(ServerLevel level, BlockPos origin) {
        return queue(level).scanOnly(level, origin);
    }

    private static CollapseQueue queue(ServerLevel level) {
        return QUEUES.computeIfAbsent(level, ignored -> new CollapseQueue(config, logger));
    }
}
