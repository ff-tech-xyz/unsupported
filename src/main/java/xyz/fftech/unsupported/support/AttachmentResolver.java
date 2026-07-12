package xyz.fftech.unsupported.support;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Map;

public final class AttachmentResolver {
    private AttachmentResolver() {}

    public static BlockPos host(BlockPos pos, BlockState state) {
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        Map<String, String> values = state.getValues().collect(
            java.util.stream.Collectors.toMap(e -> e.property().getName(), e -> e.valueName()));
        String facing = values.get("facing");
        String face = values.get("face");
        if ("wall".equals(face) && facing != null) return pos.relative(Direction.byName(facing).getOpposite());
        if ((id.contains("wall_") || id.contains("ladder") || id.contains("tripwire_hook")) && facing != null) return pos.relative(Direction.byName(facing).getOpposite());
        if ("ceiling".equals(face) || "true".equals(values.get("hanging")) || id.equals("chain") || id.contains("hanging_sign")) return pos.above();
        if ("floor".equals(face)) return pos.below();
        if (facing != null && (id.contains("button") || id.contains("lever"))) return pos.relative(Direction.byName(facing).getOpposite());
        return pos.below();
    }
}
