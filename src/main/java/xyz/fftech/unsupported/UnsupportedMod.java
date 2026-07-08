package xyz.fftech.unsupported;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.fftech.unsupported.support.BlockUpdateHooks;
import xyz.fftech.unsupported.support.SupportScanResult;

import java.util.Optional;
import java.util.OptionalDouble;

public class UnsupportedMod implements ModInitializer {
    public static final String MOD_ID = "unsupported";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        BlockWeights.load(LOGGER);
        BlockStrengths.load(LOGGER);
        UnsupportedConfig config = UnsupportedConfig.load(LOGGER);
        registerCommands();
        BlockUpdateHooks.register(config, LOGGER);
        LOGGER.info("Unsupported initialized: {} block weight values, {} strength profiles, active collapse hooks registered", BlockWeights.size(), BlockStrengths.size());
    }

    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                Commands.literal("weight")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.argument("block", BlockStateArgument.block(registryAccess))
                        .executes(context -> reportWeight(context.getSource(), BlockStateArgument.getBlock(context, "block")))
                    )
            );

            dispatcher.register(
                Commands.literal("unsupported")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("weight")
                        .then(Commands.argument("block", BlockStateArgument.block(registryAccess))
                            .executes(context -> reportWeight(context.getSource(), BlockStateArgument.getBlock(context, "block")))
                        )
                    )
                    .then(Commands.literal("strength")
                        .then(Commands.argument("block", BlockStateArgument.block(registryAccess))
                            .executes(context -> reportStrength(context.getSource(), BlockStateArgument.getBlock(context, "block")))
                        )
                    )
                    .then(Commands.literal("scan")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                            .executes(context -> {
                                ServerLevel level = context.getSource().getLevel();
                                BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
                                SupportScanResult result = BlockUpdateHooks.scanOnly(level, pos);
                                context.getSource().sendSuccess(
                                    () -> Component.literal("Unsupported scan at " + pos.toShortString()
                                        + ": status=" + result.status()
                                        + ", scanned=" + result.scannedCount()
                                        + ", failed=" + result.failedPositions().size()),
                                    false
                                );
                                return result.failedPositions().size();
                            })
                        )
                    )
            );
        });
    }

    private static int reportWeight(net.minecraft.commands.CommandSourceStack source, BlockInput input) {
        Block block = input.getState().getBlock();
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        OptionalDouble weight = BlockWeights.get(block);
        if (weight.isPresent()) {
            source.sendSuccess(
                () -> Component.literal(id + " weighs " + formatWeight(weight.getAsDouble())),
                false
            );
            return 1;
        }
        source.sendFailure(Component.literal("No weight configured for " + id));
        return 0;
    }

    private static int reportStrength(net.minecraft.commands.CommandSourceStack source, BlockInput input) {
        Block block = input.getState().getBlock();
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        Optional<StrengthProfile> profile = BlockStrengths.get(block);
        if (profile.isEmpty()) {
            source.sendFailure(Component.literal("No strength profile or stone fallback available for " + id));
            return 0;
        }
        StrengthProfile value = profile.get();
        String fallback = BlockStrengths.hasExact(block) ? "" : " (stone fallback)";
        source.sendSuccess(
            () -> Component.literal(id + fallback + " m=" + formatWeight(value.massKg())
                + " C=" + formatWeight(value.carryKg())
                + " S=" + formatWeight(value.shearKg())
                + " T=" + formatWeight(value.tensionKg())
                + " arch=" + value.maxArchSpan),
            false
        );
        return 1;
    }

    private static String formatWeight(double value) {
        if (value == Math.rint(value)) {
            return Long.toString(Math.round(value));
        }
        return Double.toString(value);
    }
}
