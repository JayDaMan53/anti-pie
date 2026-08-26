package jaydon.antipie.bukkit;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class AntiPieSettings {
    final Set<String> protectedTypes;
    final double alwaysVisibleDistance;
    final int checksPerPass;
    final int movingCheckIntervalTicks;
    final int stationaryCheckIntervalTicks;

    private AntiPieSettings(Set<String> protectedTypes, double alwaysVisibleDistance,
                            int checksPerPass,
                            int movingCheckIntervalTicks, int stationaryCheckIntervalTicks) {
        this.protectedTypes = protectedTypes;
        this.alwaysVisibleDistance = alwaysVisibleDistance;
        this.checksPerPass = checksPerPass;
        this.movingCheckIntervalTicks = movingCheckIntervalTicks;
        this.stationaryCheckIntervalTicks = stationaryCheckIntervalTicks;
    }

    static AntiPieSettings load(FileConfiguration config) {
        Set<String> types = new HashSet<>();
        addTypes(types, config.getStringList("protected-block-entities"));
        if (config.getBoolean("protect-structural-block-entities", true)) {
            addTypes(types, config.getStringList("structural-block-entities"));
        }

        int moving = clamp(config.getInt("moving-check-interval-ticks", 5), 1, 200);
        return new AntiPieSettings(
                Set.copyOf(types),
                Math.max(0.0, config.getDouble("always-visible-distance", 5.0)),
                clamp(config.getInt("visibility-checks-per-pass", 32), 2, 4096),
                moving,
                clamp(config.getInt("stationary-check-interval-ticks", 20), moving, 1200)
        );
    }

    boolean protects(String type) {
        return protectedTypes.contains(normalize(type));
    }

    private static void addTypes(Set<String> target, Iterable<String> values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isBlank()) target.add(normalized);
        }
    }

    private static String normalize(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() || normalized.indexOf(':') >= 0 ? normalized : "minecraft:" + normalized;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
