package xyz.fftech.unsupported;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class UnsupportedConfig {
    public static final int DEFAULT_SCAN_CAP = 256;
    public static final int DEFAULT_MAX_COLLAPSE_BLOCKS_PER_TICK = 64;
    public static final int DEFAULT_MAX_SCAN_RADIUS = 16;
    public static final boolean DEFAULT_COLLAPSE_ENABLED = true;
    public static final boolean DEFAULT_MESSAGES_ENABLED = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean collapseEnabled = DEFAULT_COLLAPSE_ENABLED;
    public int scanCap = DEFAULT_SCAN_CAP;
    public int maxScanRadius = DEFAULT_MAX_SCAN_RADIUS;
    public int maxCollapseBlocksPerTick = DEFAULT_MAX_COLLAPSE_BLOCKS_PER_TICK;
    public boolean messagesEnabled = DEFAULT_MESSAGES_ENABLED;
    public String unknownBlockFallback = "stone";
    public String finalFallbackBehavior = "untouched";

    public static UnsupportedConfig load(Logger logger) {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("unsupported.json");
        UnsupportedConfig config = new UnsupportedConfig();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                UnsupportedConfig loaded = GSON.fromJson(reader, UnsupportedConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            } catch (Exception e) {
                logger.warn("Unsupported could not read {}; using defaults", path, e);
            }
        } else {
            try {
                Files.createDirectories(path.getParent());
                try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                    GSON.toJson(config, writer);
                }
            } catch (IOException e) {
                logger.warn("Unsupported could not write default config {}; using in-memory defaults", path, e);
            }
        }
        config.clamp();
        logger.info("Unsupported config: collapseEnabled={}, scanCap={}, maxScanRadius={}, maxCollapseBlocksPerTick={}, messagesEnabled={}",
            config.collapseEnabled, config.scanCap, config.maxScanRadius, config.maxCollapseBlocksPerTick, config.messagesEnabled);
        return config;
    }

    private void clamp() {
        scanCap = clamp(scanCap, 16, 4096, DEFAULT_SCAN_CAP);
        maxScanRadius = clamp(maxScanRadius, 4, 64, DEFAULT_MAX_SCAN_RADIUS);
        maxCollapseBlocksPerTick = clamp(maxCollapseBlocksPerTick, 1, 512, DEFAULT_MAX_COLLAPSE_BLOCKS_PER_TICK);
        messagesEnabled = false;
        if (unknownBlockFallback == null || unknownBlockFallback.isBlank()) {
            unknownBlockFallback = "stone";
        }
        if (finalFallbackBehavior == null || finalFallbackBehavior.isBlank()) {
            finalFallbackBehavior = "untouched";
        }
    }

    private static int clamp(int value, int min, int max, int fallback) {
        if (value <= 0) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }
}
