package dev.differentenchant.enchanting;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import dev.differentenchant.enchanting.RandomEnchantPolicy.Parameters;

/** One immutable server snapshot; a failed reload never replaces the active settings. */
public final class EnchantingConfig {
    public record Settings(Parameters policy, int maxTier, int defaultTier, List<Integer> requiredLevels, DirectedEnchantPolicy.Parameters directed) {
        public Settings {
            Objects.requireNonNull(policy);
            Objects.requireNonNull(directed);
            requiredLevels = List.copyOf(requiredLevels);
            if (maxTier < 1 || maxTier > 10 || defaultTier < 1 || defaultTier > maxTier || requiredLevels.size() != 3)
                throw new IllegalArgumentException("Invalid machine tier/offer settings");
            for (int i = 0; i < 3; i++) if (requiredLevels.get(i) < i + 1 || requiredLevels.get(i) > 32767)
                throw new IllegalArgumentException("Required levels must cover 1/2/3 spent levels and fit menu sync");
        }
        public static Settings defaults() { return new Settings(Parameters.defaults(), 5, 1, List.of(5, 15, 30),DirectedEnchantPolicy.Parameters.defaults()); }
    }
    private static Settings current = Settings.defaults();
    public static Settings current() { return current; }
    public static void load() throws IOException {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("differentenchant.properties");
        if (!Files.exists(path)) {
            Files.createDirectories(path.getParent());
            try (var input = EnchantingConfig.class.getResourceAsStream("/differentenchant-defaults.properties")) {
                Objects.requireNonNull(input, "Missing default configuration");
                Files.copy(input, path);
            }
        }
        Properties props = new Properties();
        try (var reader = Files.newBufferedReader(path)) { props.load(reader); }
        current = parse(props);
    }
    public static Settings parse(Properties p) {
        var multipliers = new HashMap<String, Double>();
        for (String key : p.stringPropertyNames()) {
            if (key.startsWith("type_multiplier.")) multipliers.put(key.substring("type_multiplier.".length()), Double.parseDouble(p.getProperty(key)));
        }
        Parameters defaults = Parameters.defaults();
        Parameters parameters = new Parameters(
                number(p,"rarity.tier",defaults.rarityTier()), number(p,"rarity.shelves",defaults.rarityShelves()), number(p,"rarity.option",defaults.raritySlot()),
                number(p,"level.base",defaults.levelBase()), number(p,"level.tier",defaults.levelTier()), number(p,"level.shelves",defaults.levelShelves()), number(p,"level.option",defaults.levelSlot()),
                number(p,"extra.base",defaults.extraBase()), number(p,"extra.tier",defaults.extraTier()), number(p,"extra.shelves",defaults.extraShelves()), number(p,"extra.option",defaults.extraSlot()),
                integer(p,"max_enchantments",defaults.maxEnchantments()), integer(p,"bookshelf_scale",defaults.shelfScale()),
                number(p,"bookshelf_tail_gain",defaults.shelfTailGain()), multipliers);
        return new Settings(parameters, integer(p,"max_machine_tier",5), integer(p,"default_machine_tier",1),
                List.of(integer(p,"required_level.1",5),integer(p,"required_level.2",15),integer(p,"required_level.3",30)),
                new DirectedEnchantPolicy.Parameters(number(p,"directed.base",5),number(p,"directed.rarity",1.5),number(p,"directed.target",1.5),number(p,"directed.tier_discount",.5),
                        bool(p,"directed.allow_treasure",false),bool(p,"directed.allow_curses",false)));
    }
    private static boolean bool(Properties p,String key,boolean fallback) {
        String value=p.getProperty(key,Boolean.toString(fallback));
        if(!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Invalid boolean: "+key);
        return Boolean.parseBoolean(value);
    }
    private static double number(Properties p, String key, double fallback) { return Double.parseDouble(p.getProperty(key, Double.toString(fallback))); }
    private static int integer(Properties p, String key, int fallback) { return Integer.parseInt(p.getProperty(key, Integer.toString(fallback))); }
    private EnchantingConfig() {}
}
