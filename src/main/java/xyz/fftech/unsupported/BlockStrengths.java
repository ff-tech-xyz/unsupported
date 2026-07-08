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
import java.util.Optional;

public final class BlockStrengths {
    private static final String RESOURCE_PATH = "/data/unsupported/block_strengths.json";
    private static final String STONE_ID = "minecraft:stone";
    private static final Gson GSON = new Gson();
    private static final Type PROFILE_MAP_TYPE = new TypeToken<Map<String, StrengthProfile>>() {}.getType();
    private static Map<String, StrengthProfile> profiles = Collections.emptyMap();
    private static StrengthProfile stoneFallback;

    private BlockStrengths() {}

    public static void load(Logger logger) {
        try (InputStream stream = BlockStrengths.class.getResourceAsStream(RESOURCE_PATH)) {
            if (stream == null) {
                logger.error("Unsupported could not find {}", RESOURCE_PATH);
                profiles = Collections.emptyMap();
                stoneFallback = null;
                return;
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                Map<String, StrengthProfile> loaded = GSON.fromJson(reader, PROFILE_MAP_TYPE);
                profiles = Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
                stoneFallback = profiles.get(STONE_ID);
                if (stoneFallback == null) {
                    logger.warn("Unsupported strength data has no {}; unknown blocks will be left untouched", STONE_ID);
                }
                logger.info("Unsupported loaded {} block strength profiles", profiles.size());
            }
        } catch (Exception e) {
            logger.error("Unsupported failed to load block strengths", e);
            profiles = Collections.emptyMap();
            stoneFallback = null;
        }
    }

    public static Optional<StrengthProfile> get(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        StrengthProfile exact = profiles.get(id.toString());
        if (exact != null) {
            return Optional.of(exact);
        }
        return Optional.ofNullable(stoneFallback);
    }

    public static boolean hasExact(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        return profiles.containsKey(id.toString());
    }

    public static int size() {
        return profiles.size();
    }
}
