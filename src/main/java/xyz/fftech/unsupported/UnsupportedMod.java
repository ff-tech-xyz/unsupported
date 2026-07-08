package xyz.fftech.unsupported;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.OptionalDouble;

public class UnsupportedMod implements ModInitializer {
    public static final String MOD_ID = "unsupported";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        BlockWeights.load(LOGGER);
        registerWeightCommand();
        LOGGER.info("Unsupported initialized: {} block weight values available; /weight registered for ops", BlockWeights.size());
    }

    private static void registerWeightCommand() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            Commands.literal("weight")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("block", BlockStateArgument.block(registryAccess))
                    .executes(context -> {
                        BlockInput input = BlockStateArgument.getBlock(context, "block");
                        Block block = input.getState().getBlock();
                        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
                        OptionalDouble weight = BlockWeights.get(block);
                        if (weight.isPresent()) {
                            context.getSource().sendSuccess(
                                () -> Component.literal(id + " weighs " + formatWeight(weight.getAsDouble())),
                                false
                            );
                            return 1;
                        }
                        context.getSource().sendFailure(Component.literal("No weight configured for " + id));
                        return 0;
                    })
                )
        ));
    }

    private static String formatWeight(double value) {
        if (value == Math.rint(value)) {
            return Long.toString(Math.round(value));
        }
        return Double.toString(value);
    }
}
