package jaydon.antipie.bukkit;

import java.util.Set;

final class BlockEntityClassifier {
    private static final Set<String> DIRECT_TYPES = Set.of(
            "beacon", "chest", "trapped_chest", "ender_chest", "enchanting_table", "lectern",
            "conduit", "decorated_pot", "end_portal", "end_gateway", "structure_block", "jigsaw",
            "trial_spawner", "vault", "test_instance_block", "bell", "shelf"
    );

    private BlockEntityClassifier() {
    }

    static String fromBlockName(String namespacedBlockName) {
        int separator = namespacedBlockName.indexOf(':');
        String name = separator >= 0 ? namespacedBlockName.substring(separator + 1) : namespacedBlockName;
        if (DIRECT_TYPES.contains(name)) return "minecraft:" + name;
        if (name.equals("spawner")) return "minecraft:mob_spawner";
        if (name.equals("moving_piston")) return "minecraft:piston";
        if (name.equals("campfire") || name.equals("soul_campfire")) return "minecraft:campfire";
        if (name.equals("suspicious_sand") || name.equals("suspicious_gravel")) return "minecraft:brushable_block";
        if (name.endsWith("_hanging_sign")) return "minecraft:hanging_sign";
        if (name.endsWith("_sign")) return "minecraft:sign";
        if (name.endsWith("_shulker_box")) return "minecraft:shulker_box";
        if (name.endsWith("_banner") || name.endsWith("_wall_banner")) return "minecraft:banner";
        if (name.endsWith("_head") || name.endsWith("_skull")) return "minecraft:skull";
        if (name.endsWith("_bed")) return "minecraft:bed";
        if (name.contains("copper_golem_statue")) return "minecraft:copper_golem_statue";
        return null;
    }
}
