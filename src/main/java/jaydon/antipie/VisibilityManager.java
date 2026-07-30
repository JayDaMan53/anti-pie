package jaydon.antipie;

import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import jaydon.antipie.mixin.accessor.ChunkDataAccessor;
import jaydon.antipie.mixin.accessor.ChunkDataBlockEntityInfoAccessor;
import jaydon.antipie.mixin.accessor.SectionBlocksUpdatePacketAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class VisibilityManager {
	private static final Map<ServerPlayer, PlayerState> PLAYERS = new WeakHashMap<>();
	private static final double EPSILON = 1.0E-7;

	private VisibilityManager() {
	}

	/** Returns the original packet, a per-player replacement, or null to cancel it. */
	public static Packet<?> filter(ServerPlayer player, Packet<?> packet) {
		AntiPieConfig config = AntiPieConfig.get();

		if (packet instanceof ClientboundBlockEntityDataPacket blockEntityPacket) {
			if (!config.protects(blockEntityPacket.getType())) return packet;
			PlayerState state = state(player);
			BlockPos pos = blockEntityPacket.getPos();
			if (isVisible(player.level(), player.getEyePosition(), pos, config.alwaysVisibleDistance)) {
				state.hidden.remove(pos.asLong());
				state.visible.add(pos.asLong());
				return packet;
			}
			state.visible.remove(pos.asLong());
			state.hidden.add(pos.asLong());
			return null;
		}

		if (packet instanceof ClientboundLevelChunkWithLightPacket chunkPacket) {
			filterChunk(player, chunkPacket, config);
			return packet;
		}

		if (packet instanceof ClientboundBlockUpdatePacket updatePacket) {
			ServerLevel level = player.level();
			BlockPos pos = updatePacket.getPos();
			BlockEntity entity = level.getBlockEntity(pos);
			PlayerState existingState = PLAYERS.get(player);
			if (entity == null || !config.protects(entity.getType())) {
				if (existingState == null) return packet;
				PlayerState state = state(player);
				state.hidden.remove(pos.asLong());
				state.visible.remove(pos.asLong());
				return packet;
			}
			PlayerState state = state(player);
			if (isVisible(level, player.getEyePosition(), pos, config.alwaysVisibleDistance)) {
				state.hidden.remove(pos.asLong());
				state.visible.add(pos.asLong());
				return packet;
			}
			state.visible.remove(pos.asLong());
			state.hidden.add(pos.asLong());
			return null;
		}

		if (packet instanceof ClientboundSectionBlocksUpdatePacket sectionPacket) {
			return filterSectionUpdate(player, sectionPacket, config);
		}

		if (packet instanceof ClientboundForgetLevelChunkPacket forgetPacket) {
			PlayerState state = PLAYERS.get(player);
			if (state == null) return packet;
			int chunkX = forgetPacket.pos().x();
			int chunkZ = forgetPacket.pos().z();
			removeChunk(state.hidden, chunkX, chunkZ);
			removeChunk(state.visible, chunkX, chunkZ);
			if (state.hidden.isEmpty() && state.visible.isEmpty()) PLAYERS.remove(player);
		}

		return packet;
	}

	public static void tick(ServerPlayer player) {
		PlayerState state = PLAYERS.get(player);
		if (state == null) return;
		if (state.level != player.level()) {
			PLAYERS.remove(player);
			return;
		}
		AntiPieConfig config = AntiPieConfig.get();
		Vec3 eye = player.getEyePosition();
		boolean moved = state.lastCheckEye == null || state.lastCheckEye.distanceToSqr(eye) >= 0.0625;
		int interval = moved ? config.movingCheckIntervalTicks : config.stationaryCheckIntervalTicks;
		if (++state.ticksSinceCheck < interval) return;
		state.ticksSinceCheck = 0;
		state.lastCheckEye = eye;

		ServerLevel level = player.level();
		int budget = config.visibilityChecksPerPass;
		int hiddenBudget;
		int visibleBudget;
		if (state.hidden.isEmpty()) {
			hiddenBudget = 0;
			visibleBudget = budget;
		} else if (state.visible.isEmpty()) {
			hiddenBudget = budget;
			visibleBudget = 0;
		} else {
			hiddenBudget = Math.max(1, budget / 2);
			visibleBudget = Math.max(1, budget - hiddenBudget);
		}
		LongOpenHashSet chunksToRefresh = new LongOpenHashSet();
		LongArrayList stillHidden = new LongArrayList();
		LongIterator iterator = state.hidden.iterator();
		for (int checked = 0; checked < hiddenBudget && iterator.hasNext(); checked++) {
			long packedPos = iterator.nextLong();
			BlockPos pos = BlockPos.of(packedPos);
			BlockEntity entity = level.getBlockEntity(pos);
			if (entity == null || !config.protects(entity.getType())) {
				iterator.remove();
			} else if (isVisible(level, eye, pos, config.alwaysVisibleDistance)) {
				iterator.remove();
				state.visible.add(packedPos);
				chunksToRefresh.add(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4));
			} else {
				iterator.remove();
				stillHidden.add(packedPos);
			}
		}
		// Move checked-but-hidden entries to the back so every entry gets a bounded recheck.
		state.hidden.addAll(stillHidden);

		LongArrayList stillVisible = new LongArrayList();
		LongIterator visibleIterator = state.visible.iterator();
		for (int checked = 0; checked < visibleBudget && visibleIterator.hasNext(); checked++) {
			long packedPos = visibleIterator.nextLong();
			BlockPos pos = BlockPos.of(packedPos);
			BlockEntity entity = level.getBlockEntity(pos);
			visibleIterator.remove();
			if (entity == null || !config.protects(entity.getType())) {
				continue;
			}
			if (isVisible(level, eye, pos, config.alwaysVisibleDistance)) {
				stillVisible.add(packedPos);
			} else {
				state.hidden.add(packedPos);
				chunksToRefresh.add(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4));
			}
		}
		// Round-robin visible checks for the same reason as the hidden queue above.
		state.visible.addAll(stillVisible);

		LongIterator chunks = chunksToRefresh.iterator();
		while (chunks.hasNext()) {
			long packedChunk = chunks.nextLong();
			int chunkX = ChunkPos.getX(packedChunk);
			int chunkZ = ChunkPos.getZ(packedChunk);
			LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
			if (chunk != null) {
				player.connection.send(new ClientboundLevelChunkWithLightPacket(
						chunk, level.getLightEngine(), null, null));
			}
		}
		if (state.hidden.isEmpty() && state.visible.isEmpty()) PLAYERS.remove(player);
	}

	private static void filterChunk(ServerPlayer player,
			ClientboundLevelChunkWithLightPacket packet, AntiPieConfig config) {
		List<Object> entries =
				((ChunkDataAccessor) packet.getChunkData()).antiPie$getBlockEntitiesData();
		boolean hasProtectedEntry = entries.stream().anyMatch(info -> config.protects(
				((ChunkDataBlockEntityInfoAccessor) info).antiPie$getType()));
		if (!hasProtectedEntry) return;
		PlayerState state = state(player);
		int baseX = packet.getX() << 4;
		int baseZ = packet.getZ() << 4;
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		state.resetInitialChunkBudget(level.getGameTime(), config.initialChunkChecksPerTick);

		entries.removeIf(info -> {
			ChunkDataBlockEntityInfoAccessor accessor = (ChunkDataBlockEntityInfoAccessor) (Object) info;
			if (!config.protects(accessor.antiPie$getType())) return false;
			int packedXZ = accessor.antiPie$getPackedXZ();
			BlockPos pos = new BlockPos(baseX + SectionPos.sectionRelative(packedXZ >> 4),
					accessor.antiPie$getY(), baseZ + SectionPos.sectionRelative(packedXZ));
			boolean insideAlwaysVisibleRadius = eye.distanceToSqr(Vec3.atCenterOf(pos))
					<= config.alwaysVisibleDistance * config.alwaysVisibleDistance;
			boolean visible = insideAlwaysVisibleRadius
					|| (state.consumeInitialChunkCheck()
					&& isVisible(level, eye, pos, config.alwaysVisibleDistance));
			if (visible) {
				state.hidden.remove(pos.asLong());
				state.visible.add(pos.asLong());
				return false;
			}
			state.visible.remove(pos.asLong());
			state.hidden.add(pos.asLong());
			return true;
		});
	}

	private static Packet<?> filterSectionUpdate(ServerPlayer player,
			ClientboundSectionBlocksUpdatePacket packet, AntiPieConfig config) {
		SectionBlocksUpdatePacketAccessor accessor = (SectionBlocksUpdatePacketAccessor) packet;
		SectionPos sectionPos = accessor.antiPie$getSectionPos();
		short[] positions = accessor.antiPie$getPositions();
		BlockState[] states = accessor.antiPie$getStates();
		ShortOpenHashSet allowed = new ShortOpenHashSet(positions.length);
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		PlayerState playerState = PLAYERS.get(player);

		for (int index = 0; index < positions.length; index++) {
			short packedPosition = positions[index];
			BlockPos pos = sectionPos.relativeToBlockPos(packedPosition);
			if (!states[index].hasBlockEntity()) {
				if (playerState != null) {
					playerState.hidden.remove(pos.asLong());
					playerState.visible.remove(pos.asLong());
				}
				allowed.add(packedPosition);
				continue;
			}
			BlockEntity entity = level.getBlockEntity(pos);
			if (entity == null || !config.protects(entity.getType())) {
				if (playerState != null) {
					playerState.hidden.remove(pos.asLong());
					playerState.visible.remove(pos.asLong());
				}
				allowed.add(packedPosition);
				continue;
			}
			if (playerState == null) playerState = state(player);
			if (isVisible(level, eye, pos, config.alwaysVisibleDistance)) {
				playerState.hidden.remove(pos.asLong());
				playerState.visible.add(pos.asLong());
				allowed.add(packedPosition);
			} else {
				playerState.visible.remove(pos.asLong());
				playerState.hidden.add(pos.asLong());
			}
		}

		if (allowed.size() == positions.length) return packet;
		if (allowed.isEmpty()) return null;

		LevelChunkSection section = level.getChunk(sectionPos.x(), sectionPos.z())
				.getSection(level.getSectionIndexFromSectionY(sectionPos.y()));
		return new ClientboundSectionBlocksUpdatePacket(sectionPos, allowed, section);
	}

	private static PlayerState state(ServerPlayer player) {
		PlayerState state = PLAYERS.computeIfAbsent(player, ignored -> new PlayerState(player.level()));
		if (state.level != player.level()) {
			state = new PlayerState(player.level());
			PLAYERS.put(player, state);
		}
		return state;
	}

	private static boolean isVisible(ServerLevel level, Vec3 eye, BlockPos target,
			double alwaysVisibleDistance) {
		Vec3 center = Vec3.atCenterOf(target);
		if (eye.distanceToSqr(center) <= alwaysVisibleDistance * alwaysVisibleDistance) return true;
		if (clearRay(level, eye, center, target)) return true;

		// A geometrically near face can be buried while another face is exposed (for
		// example, a chest at the bottom of a one-block hole). Test every exposed
		// face so crossing an angle boundary cannot make a visible entity disappear.
		for (Direction face : Direction.values()) {
			if (level.getBlockState(target.relative(face)).isSolidRender()) continue;
			if (clearFace(level, eye, target, face)) return true;
		}
		return false;
	}

	private static boolean clearFace(ServerLevel level, Vec3 eye, BlockPos target, Direction face) {
		double fixedX = face.getStepX() < 0 ? 0.02 : face.getStepX() > 0 ? 0.98 : 0.5;
		double fixedY = face.getStepY() < 0 ? 0.02 : face.getStepY() > 0 ? 0.98 : 0.5;
		double fixedZ = face.getStepZ() < 0 ? 0.02 : face.getStepZ() > 0 ? 0.98 : 0.5;
		if (clearRay(level, eye, new Vec3(target.getX() + fixedX,
				target.getY() + fixedY, target.getZ() + fixedZ), target)) return true;

		for (int first = 0; first < 2; first++) {
			for (int second = 0; second < 2; second++) {
				double edgeA = first == 0 ? 0.02 : 0.98;
				double edgeB = second == 0 ? 0.02 : 0.98;
				double x = fixedX;
				double y = fixedY;
				double z = fixedZ;
				if (face.getAxis() == Direction.Axis.X) {
					y = edgeA;
					z = edgeB;
				} else if (face.getAxis() == Direction.Axis.Y) {
					x = edgeA;
					z = edgeB;
				} else {
					x = edgeA;
					y = edgeB;
				}
				if (clearRay(level, eye,
						new Vec3(target.getX() + x, target.getY() + y, target.getZ() + z), target)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Amanatides-Woo voxel traversal. Transparent and partial blocks do not obstruct the ray. */
	private static boolean clearRay(ServerLevel level, Vec3 start, Vec3 end, BlockPos target) {
		int x = floor(start.x), y = floor(start.y), z = floor(start.z);
		int endX = floor(end.x), endY = floor(end.y), endZ = floor(end.z);
		double dx = end.x - start.x, dy = end.y - start.y, dz = end.z - start.z;
		int stepX = Integer.compare(endX, x), stepY = Integer.compare(endY, y), stepZ = Integer.compare(endZ, z);
		double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
		double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
		double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);
		double tMaxX = firstBoundary(start.x, x, stepX, dx);
		double tMaxY = firstBoundary(start.y, y, stepY, dy);
		double tMaxZ = firstBoundary(start.z, z, stepZ, dz);
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		for (int steps = 0; steps < 1024; steps++) {
			if (x == endX && y == endY && z == endZ) return true;
			double nextBoundary = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));
			if (tMaxX <= nextBoundary + EPSILON) {
				x += stepX;
				tMaxX += tDeltaX;
			}
			if (tMaxY <= nextBoundary + EPSILON) {
				y += stepY;
				tMaxY += tDeltaY;
			}
			if (tMaxZ <= nextBoundary + EPSILON) {
				z += stepZ;
				tMaxZ += tDeltaZ;
			}
			if (x == target.getX() && y == target.getY() && z == target.getZ()) return true;
			cursor.set(x, y, z);
			if (level.getBlockState(cursor).isSolidRender()) return false;
		}
		return false;
	}

	private static int floor(double value) {
		int integer = (int) value;
		return value < integer ? integer - 1 : integer;
	}

	private static double firstBoundary(double coordinate, int block, int step, double delta) {
		if (step == 0) return Double.POSITIVE_INFINITY;
		double boundary = step > 0 ? block + 1.0 : block;
		return Math.max(0.0, (boundary - coordinate) / delta);
	}

	private static void removeChunk(LongLinkedOpenHashSet positions, int chunkX, int chunkZ) {
		LongIterator iterator = positions.iterator();
		while (iterator.hasNext()) {
			long pos = iterator.nextLong();
			if ((BlockPos.getX(pos) >> 4) == chunkX && (BlockPos.getZ(pos) >> 4) == chunkZ) {
				iterator.remove();
			}
		}
	}

	private static final class PlayerState {
		private final ServerLevel level;
		private final LongLinkedOpenHashSet hidden = new LongLinkedOpenHashSet();
		private final LongLinkedOpenHashSet visible = new LongLinkedOpenHashSet();
		private int ticksSinceCheck;
		private Vec3 lastCheckEye;
		private long initialChunkBudgetTick = Long.MIN_VALUE;
		private int initialChunkChecksRemaining;

		private PlayerState(ServerLevel level) {
			this.level = level;
		}

		private void resetInitialChunkBudget(long gameTime, int checks) {
			if (initialChunkBudgetTick != gameTime) {
				initialChunkBudgetTick = gameTime;
				initialChunkChecksRemaining = checks;
			}
		}

		private boolean consumeInitialChunkCheck() {
			if (initialChunkChecksRemaining <= 0) return false;
			initialChunkChecksRemaining--;
			return true;
		}
	}
}
