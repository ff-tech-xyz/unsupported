package xyz.fftech.unsupported;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import xyz.fftech.unsupported.support.BlockCategory;
import xyz.fftech.unsupported.support.FailureMode;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockPhysics {
    private static final String RESOURCE_PATH = "/data/unsupported/block_physics.json";
    private static final Type TYPE = new TypeToken<Map<String, Entry>>() {}.getType();
    private static Map<String, Entry> entries = Collections.emptyMap();
    private static final Map<BlockState, BlockCategory> categoryCache = new ConcurrentHashMap<>();

    private BlockPhysics() {}

    public static void load(Logger logger) {
        categoryCache.clear();
        try (InputStream stream = BlockPhysics.class.getResourceAsStream(RESOURCE_PATH)) {
            if (stream == null) throw new IllegalStateException("missing " + RESOURCE_PATH);
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                entries = Collections.unmodifiableMap(new Gson().fromJson(reader, TYPE));
            }
            logger.info("Unsupported loaded {} block physics profiles", entries.size());
        } catch (Exception e) {
            logger.error("Unsupported failed to load block physics profiles", e);
            entries = Collections.emptyMap();
        }
    }

    public static BlockCategory category(BlockState state) {
        return categoryCache.computeIfAbsent(state, value -> {
            Entry entry = entry(value.getBlock());
            if (entry != null) return entry.category;
            if (value.blocksMotion()) return BlockCategory.STRUCTURAL;
            return BlockWeights.get(value.getBlock()).isPresent() ? BlockCategory.ATTACHED : BlockCategory.IGNORED;
        });
    }

    public static FailureMode failure(BlockState state) {
        Entry entry = entry(state.getBlock());
        return entry == null ? (state.blocksMotion() ? FailureMode.FALL : FailureMode.POP) : entry.failure;
    }

    private static Entry entry(Block block) {
        return entries.get(BuiltInRegistries.BLOCK.getKey(block).toString());
    }

    private static final class Entry {
        BlockCategory category;
        FailureMode failure;
    }
}
