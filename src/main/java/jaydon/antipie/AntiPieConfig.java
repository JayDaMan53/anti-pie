package jaydon.antipie;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AntiPieConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("anti-pie.json5");
	private static final List<String> DEFAULT_TYPES = List.of(
			"minecraft:chest", "minecraft:trapped_chest", "minecraft:ender_chest",
			"minecraft:shulker_box", "minecraft:beacon", "minecraft:banner",
			"minecraft:skull", "minecraft:enchanting_table", "minecraft:lectern",
			"minecraft:campfire", "minecraft:conduit", "minecraft:sign",
			"minecraft:hanging_sign", "minecraft:decorated_pot",
			/*? if >=1.21.11 {*/
			"minecraft:shelf", "minecraft:copper_golem_statue",
			/*?}*/
			/*? if <26.2 {*/
			/*"minecraft:bed",*/
			/*?}*/
			"minecraft:mob_spawner", "minecraft:piston",
			"minecraft:end_portal", "minecraft:end_gateway", "minecraft:structure_block",
			/*? if >=1.21.11 {*/
			"minecraft:test_instance_block",
			/*?}*/
			"minecraft:bell", "minecraft:brushable_block",
			"minecraft:trial_spawner", "minecraft:vault"
	);
	private static volatile AntiPieConfig INSTANCE = defaults();

	public List<String> protectedBlockEntities = DEFAULT_TYPES;
	public double alwaysVisibleDistance = 5.0;
	public int visibilityChecksPerPass = 32;
	public int initialChunkChecksPerTick = 64;
	public int movingCheckIntervalTicks = 5;
	public int stationaryCheckIntervalTicks = 20;

	private transient Set<BlockEntityType<?>> protectedTypes = Set.of();

	public static AntiPieConfig get() {
		return INSTANCE;
	}

	public static void load() {
		AntiPieConfig loaded = defaults();
		try {
			Files.createDirectories(PATH.getParent());
			if (!Files.exists(PATH)) writeDefaultConfig();
			try (Reader reader = Files.newBufferedReader(PATH)) {
				JsonReader jsonReader = new JsonReader(reader);
				jsonReader.setLenient(true);
				AntiPieConfig parsed = GSON.fromJson(jsonReader, AntiPieConfig.class);
				if (parsed != null) loaded = parsed;
			}
		} catch (IOException | RuntimeException error) {
			AntiPie.LOGGER.error("Could not load {}; using defaults", PATH, error);
		}

		loaded.sanitize();
		INSTANCE = loaded;
		AntiPie.LOGGER.info("Protecting {} block entity types (always visible within {} blocks)",
				loaded.protectedTypes.size(), loaded.alwaysVisibleDistance);
	}

	public boolean protects(BlockEntityType<?> type) {
		return protectedTypes.contains(type);
	}

	private void sanitize() {
		alwaysVisibleDistance = Math.max(0.0, alwaysVisibleDistance);
		visibilityChecksPerPass = Math.clamp(visibilityChecksPerPass, 2, 4096);
		initialChunkChecksPerTick = Math.clamp(initialChunkChecksPerTick, 1, 4096);
		movingCheckIntervalTicks = Math.clamp(movingCheckIntervalTicks, 1, 200);
		stationaryCheckIntervalTicks = Math.clamp(stationaryCheckIntervalTicks,
				movingCheckIntervalTicks, 1200);

		Set<BlockEntityType<?>> resolved = new HashSet<>();
		if (protectedBlockEntities != null) {
			for (String value : protectedBlockEntities) {
				Identifier id = Identifier.tryParse(value);
				if (id == null) {
					AntiPie.LOGGER.warn("Ignoring invalid block entity identifier: {}", value);
					continue;
				}
				BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(id).ifPresentOrElse(
						resolved::add,
						() -> AntiPie.LOGGER.warn("Ignoring unknown block entity type: {}", id)
				);
			}
		}
		protectedTypes = Set.copyOf(resolved);
	}

	private static void writeDefaultConfig() throws IOException {
		try (InputStream input = AntiPieConfig.class.getResourceAsStream("/default-anti-pie.json5")) {
			if (input == null) throw new IOException("Bundled default-anti-pie.json5 is missing");
			Files.copy(input, PATH);
		}
	}

	private static AntiPieConfig defaults() {
		return new AntiPieConfig();
	}
}
