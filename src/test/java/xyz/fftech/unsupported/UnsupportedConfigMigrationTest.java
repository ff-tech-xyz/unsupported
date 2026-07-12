package xyz.fftech.unsupported;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class UnsupportedConfigMigrationTest {
    public static void main(String[] args) throws Exception {
        legacyConfigWithoutVersionIsMigrated();
        absentConfigUsesCurrentSafeDefaults();
        currentConfigPreservesExplicitCollapseSetting();
        System.out.println("UnsupportedConfigMigrationTest: 3 tests passed");
    }

    private static void legacyConfigWithoutVersionIsMigrated() throws Exception {
        Path path = Files.createTempDirectory("unsupported-legacy-config").resolve("unsupported.json");
        Files.writeString(path, "{\"collapseEnabled\":true,\"scanCap\":256}");
        List<String> warnings = new ArrayList<>();

        UnsupportedConfig config = UnsupportedConfig.load(path, logger(warnings));

        check(!config.collapseEnabled, "legacy config must disable collapse");
        check(config.weightDataVersion == 1, "legacy config must migrate to version 1");
        JsonObject persisted = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        check(!persisted.get("collapseEnabled").getAsBoolean(), "migration must persist disabled collapse");
        check(persisted.get("weightDataVersion").getAsInt() == 1, "migration must persist version 1");
        check(warnings.stream().anyMatch(message -> message.contains("disabled collapse for the block-weight migration")),
            "migration must emit its warning");
    }

    private static void absentConfigUsesCurrentSafeDefaults() throws Exception {
        Path path = Files.createTempDirectory("unsupported-fresh-config").resolve("unsupported.json");

        UnsupportedConfig config = UnsupportedConfig.load(path, logger(new ArrayList<>()));

        check(!config.collapseEnabled, "fresh config must default collapse off");
        check(config.weightDataVersion == 1, "fresh config must use version 1");
        JsonObject persisted = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        check(!persisted.get("collapseEnabled").getAsBoolean(), "fresh config must persist collapse off");
        check(persisted.get("weightDataVersion").getAsInt() == 1, "fresh config must persist version 1");
    }

    private static void currentConfigPreservesExplicitCollapseSetting() throws Exception {
        Path path = Files.createTempDirectory("unsupported-current-config").resolve("unsupported.json");
        Files.writeString(path, "{\"collapseEnabled\":true,\"weightDataVersion\":1}");

        UnsupportedConfig config = UnsupportedConfig.load(path, logger(new ArrayList<>()));

        check(config.collapseEnabled, "current config must preserve explicit collapse setting");
        check(config.weightDataVersion == 1, "current config must remain at version 1");
    }

    private static Logger logger(List<String> warnings) {
        return (Logger) Proxy.newProxyInstance(
            Logger.class.getClassLoader(),
            new Class<?>[] {Logger.class},
            (proxy, method, args) -> {
                if (method.getName().equals("warn") && args != null && args.length > 0) {
                    warnings.add(String.valueOf(args[0]));
                }
                Class<?> returnType = method.getReturnType();
                if (returnType == boolean.class) {
                    return false;
                }
                if (returnType == int.class) {
                    return 0;
                }
                return null;
            }
        );
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}