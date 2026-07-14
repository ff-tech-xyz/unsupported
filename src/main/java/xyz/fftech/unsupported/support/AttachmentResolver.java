package xyz.fftech.unsupported.support;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Map;
import java.util.List;

public final class AttachmentResolver {
    private AttachmentResolver() {}

    public static List<BlockPos> hosts(BlockPos pos, BlockState state) {
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        Map<String, String> values = state.getValues().collect(
            java.util.stream.Collectors.toMap(e -> e.property().getName(), e -> e.valueName()));
        String facing = values.get("facing");
        String face = values.get("face");
        Direction direction = facing == null ? null : Direction.byName(facing);
        String attachment = values.get("attachment");
        if (id.equals("bell")) {
            if ("ceiling".equals(attachment)) return List.of(pos.above());
            if ("floor".equals(attachment)) return List.of(pos.below());
            if (direction != null && "single_wall".equals(attachment)) return List.of(pos.relative(direction));
            if (direction != null && "double_wall".equals(attachment)) {
                Direction.Axis axis = direction.getAxis();
                return List.of(pos.relative(Direction.get(Direction.AxisDirection.POSITIVE, axis)), pos.relative(Direction.get(Direction.AxisDirection.NEGATIVE, axis)));
            }
        }
        if (id.endsWith("_chain")) {
            String axisName = values.get("axis");
            Direction.Axis axis = axisName == null ? Direction.Axis.Y : Direction.Axis.byName(axisName);
            // Chains have one owner: vertical links transfer load upward; horizontal runs transfer
            // toward the negative-coordinate endpoint (west for X, north for Z). Repeating that
            // rule across links gives the whole run one deterministic endpoint owner.
            if (axis == Direction.Axis.Y) return List.of(pos.above());
            return List.of(pos.relative(Direction.get(Direction.AxisDirection.NEGATIVE, axis)));
        }
        if ("wall".equals(face) && direction != null) return List.of(pos.relative(direction.getOpposite()));
        if ((id.contains("wall_") || id.contains("ladder") || id.contains("tripwire_hook")) && direction != null) return List.of(pos.relative(direction.getOpposite()));
        if ("ceiling".equals(face) || "true".equals(values.get("hanging")) || id.contains("hanging_sign")) return List.of(pos.above());
        if ("floor".equals(face)) return List.of(pos.below());
        if (direction != null && (id.contains("button") || id.contains("lever"))) return List.of(pos.relative(direction.getOpposite()));
        // Known freestanding/floor-mounted attachment families have no directional face property.
        if (id.endsWith("_banner") || id.endsWith("_sign") || id.endsWith("_head")
            || id.endsWith("_skull") || id.equals("lantern") || id.endsWith("_lantern")) {
            return List.of(pos.below());
        }
        if (id.equals("sea_pickle") || id.equals("turtle_egg") || id.equals("flower_pot")
            || id.equals("torch") || id.endsWith("_candle") || id.equals("candle")
            || id.endsWith("_pressure_plate") || id.endsWith("_rail") || id.equals("rail")
            || id.endsWith("_sapling")) return List.of(pos.below());
        return List.of();
    }
}
