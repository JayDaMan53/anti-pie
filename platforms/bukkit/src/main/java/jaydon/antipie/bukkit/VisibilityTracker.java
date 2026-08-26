package jaydon.antipie.bukkit;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

final class VisibilityTracker {
    private final Map<UUID, PlayerState> players = new ConcurrentHashMap<>();
    private volatile AntiPieSettings settings;

    VisibilityTracker(AntiPieSettings settings) {
        this.settings = settings;
    }

    void updateSettings(AntiPieSettings settings) {
        this.settings = settings;
        players.clear();
    }

    boolean protects(String type) {
        return settings.protects(type);
    }

    boolean observe(UUID playerId, BlockPosition position, String type) {
        PlayerState state = players.computeIfAbsent(playerId, ignored -> new PlayerState());
        TrackedBlock block = state.blocks.compute(position, (ignored, existing) -> {
            if (existing != null && existing.type.equals(type)) return existing;
            TrackedBlock replacement = new TrackedBlock(type);
            state.queue.offer(position);
            return replacement;
        });
        PlayerSnapshot snapshot = state.snapshot;
        if (snapshot != null && snapshot.distanceSquared(position) <= square(settings.alwaysVisibleDistance)) {
            block.visible = true;
        }
        return block.visible;
    }

    void forget(UUID playerId, BlockPosition position) {
        PlayerState state = players.get(playerId);
        if (state == null) return;
        state.blocks.remove(position);
    }

    void forgetChunk(UUID playerId, int chunkX, int chunkZ) {
        PlayerState state = players.get(playerId);
        if (state == null) return;
        long chunkKey = BlockPosition.chunkKey(chunkX, chunkZ);
        state.blocks.keySet().removeIf(position -> position.chunkKey() == chunkKey);
    }

    void remove(UUID playerId) {
        players.remove(playerId);
    }

    void tick(Iterable<? extends Player> onlinePlayers) {
        Set<UUID> online = new HashSet<>();
        for (Player player : onlinePlayers) {
            UUID playerId = player.getUniqueId();
            online.add(playerId);
            PlayerState state = players.computeIfAbsent(playerId, ignored -> new PlayerState());
            Location eye = player.getEyeLocation();
            UUID worldId = player.getWorld().getUID();
            boolean changedWorld = state.snapshot != null && !state.snapshot.worldId.equals(worldId);
            boolean moving = changedWorld || state.lastEye == null || state.lastEye.distanceSquared(eye) > 0.0001
                    || state.lastYaw != eye.getYaw() || state.lastPitch != eye.getPitch();
            if (changedWorld) {
                state.blocks.clear();
                state.queue.clear();
            }
            state.snapshot = new PlayerSnapshot(worldId, eye.getX(), eye.getY(), eye.getZ());
            state.lastEye = eye.clone();
            state.lastYaw = eye.getYaw();
            state.lastPitch = eye.getPitch();

            int interval = moving ? settings.movingCheckIntervalTicks : settings.stationaryCheckIntervalTicks;
            if (++state.ticksSinceCheck < interval) continue;
            state.ticksSinceCheck = 0;
            checkPlayer(player, state, eye);
        }
        players.keySet().removeIf(playerId -> !online.contains(playerId));
    }

    private void checkPlayer(Player player, PlayerState state, Location eye) {
        World world = player.getWorld();
        int budget = settings.checksPerPass;
        Set<Long> refreshChunks = new HashSet<>();

        for (int checked = 0; checked < budget; checked++) {
            BlockPosition position = state.queue.poll();
            if (position == null) break;
            TrackedBlock block = state.blocks.get(position);
            if (block == null) continue;

            if (!world.isChunkLoaded(position.x() >> 4, position.z() >> 4)) {
                state.blocks.remove(position, block);
                continue;
            }
            String currentType = BlockEntityClassifier.fromBlockName(
                    world.getBlockAt(position.x(), position.y(), position.z()).getType().getKey().toString());
            if (!block.type.equals(currentType)) {
                state.blocks.remove(position, block);
                continue;
            }

            boolean visible = VoxelVisibility.isVisible(world, eye, position, settings.alwaysVisibleDistance);
            if (block.visible != visible) {
                block.visible = visible;
                refreshChunks.add(position.chunkKey());
            }
            if (state.blocks.get(position) == block) state.queue.offer(position);
        }

        for (long chunkKey : refreshChunks) {
            int chunkX = (int) chunkKey;
            int chunkZ = (int) (chunkKey >>> 32);
            if (world.isChunkLoaded(chunkX, chunkZ)) world.refreshChunk(chunkX, chunkZ);
        }
    }

    private static double square(double value) {
        return value * value;
    }

    private static final class PlayerState {
        final Map<BlockPosition, TrackedBlock> blocks = new ConcurrentHashMap<>();
        final ConcurrentLinkedQueue<BlockPosition> queue = new ConcurrentLinkedQueue<>();
        volatile PlayerSnapshot snapshot;
        Location lastEye;
        float lastYaw;
        float lastPitch;
        int ticksSinceCheck;
    }

    private static final class TrackedBlock {
        final String type;
        volatile boolean visible;
        TrackedBlock(String type) {
            this.type = type;
        }
    }

    private record PlayerSnapshot(UUID worldId, double x, double y, double z) {
        double distanceSquared(BlockPosition position) {
            double dx = x - (position.x() + 0.5);
            double dy = y - (position.y() + 0.5);
            double dz = z - (position.z() + 0.5);
            return dx * dx + dy * dy + dz * dz;
        }
    }
}
