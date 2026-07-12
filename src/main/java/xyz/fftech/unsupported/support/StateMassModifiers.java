package xyz.fftech.unsupported.support;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

public final class StateMassModifiers {
    private StateMassModifiers() {}

    public static double apply(double baseMass, BlockState state) {
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        int count = integerProperty(state, "layers", integerProperty(state, "candles",
            integerProperty(state, "pickles", integerProperty(state, "eggs", 1))));
        String slabType = property(state, "type");
        String rule = id.equals("snow") ? "snow"
            : id.endsWith("_slab") && "double".equals(slabType) ? "slab_double"
            : id.contains("candle") ? "candle"
            : id.equals("sea_pickle") ? "sea_pickle"
            : id.equals("turtle_egg") ? "turtle_egg" : "plain";
        return apply(baseMass, rule, count, "true".equals(property(state, "waterlogged")));
    }

    private static int integerProperty(BlockState state, String name, int fallback) {
        String value = property(state, name);
        if (value == null) return fallback;
        try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return fallback; }
    }

    private static String property(BlockState state, String name) {
        return state.getValues().filter(value -> value.property().getName().equals(name))
            .map(net.minecraft.world.level.block.state.properties.Property.Value::valueName).findFirst().orElse(null);
    }

    public static double apply(double baseMass, String rule, int count, boolean waterlogged) {
        double mass = switch (rule) {
            case "snow" -> baseMass * count / 8.0;
            case "slab_double" -> baseMass * 2.0;
            case "candle", "sea_pickle", "turtle_egg" -> baseMass * count;
            default -> baseMass;
        };
        return mass + (waterlogged ? 1_000.0 : 0.0);
    }
}
