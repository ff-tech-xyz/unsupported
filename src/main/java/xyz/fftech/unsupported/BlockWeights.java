package xyz.fftech.unsupported;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalDouble;

public final class BlockWeights {
    private static final String RESOURCE_PATH = "/data/unsupported/block_weights.json";
    private static final Gson GSON = new Gson();
    private static final Type WEIGHT_MAP_TYPE = new TypeToken<Map<String, Double>>() {}.getType();
    private static Map<String, Double> weights = Collections.emptyMap();

    private BlockWeights() {}

    public static void load(Logger logger) {
        try (InputStream stream = BlockWeights.class.getResourceAsStream(RESOURCE_PATH)) {
            if (stream == null) {
                logger.error("Unsupported could not find {}", RESOURCE_PATH);
                weights = Collections.emptyMap();
                return;
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                Map<String, Double> loaded = GSON.fromJson(reader, WEIGHT_MAP_TYPE);
                weights = Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
                logger.info("Unsupported loaded {} block weight values", weights.size());
            }
        } catch (Exception e) {
            logger.error("Unsupported failed to load block weights", e);
            weights = Collections.emptyMap();
        }
    }

    public static OptionalDouble get(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        Double value = weights.get(id.toString());
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    public static int size() {
        return weights.size();
    }
}
