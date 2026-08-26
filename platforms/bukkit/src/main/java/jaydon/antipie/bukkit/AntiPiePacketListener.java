package jaydon.antipie.bukkit;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.blockentity.BlockEntityType;
import com.github.retrooper.packetevents.protocol.world.blockentity.BlockEntityTypes;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.TileEntity;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockEntityData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUnloadChunk;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class AntiPiePacketListener extends PacketListenerAbstract {
    private final VisibilityTracker tracker;
    private final ClientVersion serverVersion;

    AntiPiePacketListener(VisibilityTracker tracker) {
        super(PacketListenerPriority.HIGH);
        this.tracker = tracker;
        this.serverVersion = PacketEvents.getAPI().getServerManager().getVersion().toClientVersion();
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        UUID playerId = player.getUniqueId();

        if (event.getPacketType() == PacketType.Play.Server.CHUNK_DATA) {
            filterChunk(event, playerId);
        } else if (event.getPacketType() == PacketType.Play.Server.BLOCK_ENTITY_DATA) {
            filterBlockEntityData(event, playerId);
        } else if (event.getPacketType() == PacketType.Play.Server.BLOCK_CHANGE) {
            WrapperPlayServerBlockChange wrapper = new WrapperPlayServerBlockChange(event);
            BlockPosition position = position(wrapper.getBlockPosition());
            String type = BlockEntityClassifier.fromBlockName(wrapper.getBlockState().getType().getName());
            if (type != null && tracker.protects(type) && !tracker.observe(playerId, position, type)) {
                event.setCancelled(true);
            } else {
                if (type == null || !tracker.protects(type)) tracker.forget(playerId, position);
            }
        } else if (event.getPacketType() == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
            WrapperPlayServerMultiBlockChange wrapper = new WrapperPlayServerMultiBlockChange(event);
            List<WrapperPlayServerMultiBlockChange.EncodedBlock> filtered = new ArrayList<>();
            for (WrapperPlayServerMultiBlockChange.EncodedBlock block : wrapper.getBlocks()) {
                BlockPosition position = new BlockPosition(block.getX(), block.getY(), block.getZ());
                String type = BlockEntityClassifier.fromBlockName(block.getBlockState(serverVersion).getType().getName());
                if (type != null && tracker.protects(type)) {
                    if (tracker.observe(playerId, position, type)) filtered.add(block);
                } else {
                    tracker.forget(playerId, position);
                    filtered.add(block);
                }
            }
            if (filtered.size() != wrapper.getBlocks().length) {
                if (filtered.isEmpty()) event.setCancelled(true);
                else wrapper.setBlocks(filtered.toArray(WrapperPlayServerMultiBlockChange.EncodedBlock[]::new));
            }
        } else if (event.getPacketType() == PacketType.Play.Server.UNLOAD_CHUNK) {
            WrapperPlayServerUnloadChunk wrapper = new WrapperPlayServerUnloadChunk(event);
            tracker.forgetChunk(playerId, wrapper.getChunkX(), wrapper.getChunkZ());
        }
    }

    private void filterChunk(PacketSendEvent event, UUID playerId) {
        WrapperPlayServerChunkData wrapper = new WrapperPlayServerChunkData(event);
        Column column = wrapper.getColumn();
        TileEntity[] original = column.getTileEntities();
        List<TileEntity> filtered = new ArrayList<>(original.length);

        for (TileEntity tile : original) {
            BlockEntityType type = BlockEntityTypes.getById(serverVersion, tile.getType());
            if (type == null) {
                filtered.add(tile);
                continue;
            }
            String typeName = type.getName().toString();
            if (!tracker.protects(typeName)) {
                filtered.add(tile);
                continue;
            }
            BlockPosition position = new BlockPosition(
                    (column.getX() << 4) + tile.getX(), tile.getY(), (column.getZ() << 4) + tile.getZ());
            if (tracker.observe(playerId, position, typeName)) filtered.add(tile);
        }

        if (filtered.size() != original.length) {
            wrapper.setColumn(copyWithTiles(column, filtered.toArray(TileEntity[]::new)));
        }
    }

    private void filterBlockEntityData(PacketSendEvent event, UUID playerId) {
        WrapperPlayServerBlockEntityData wrapper = new WrapperPlayServerBlockEntityData(event);
        BlockEntityType type = wrapper.getBlockEntityType();
        if (type == null) return;
        String typeName = type.getName().toString();
        if (!tracker.protects(typeName)) return;
        if (!tracker.observe(playerId, position(wrapper.getPosition()), typeName)) event.setCancelled(true);
    }

    @SuppressWarnings("deprecation")
    private Column copyWithTiles(Column column, TileEntity[] tiles) {
        ServerVersion version = PacketEvents.getAPI().getServerManager().getVersion();
        if (version.isNewerThanOrEquals(ServerVersion.V_1_21_5)) {
            return new Column(column.getX(), column.getZ(), column.isFullChunk(), column.getChunks(), tiles,
                    column.getHeightmaps());
        }
        return new Column(column.getX(), column.getZ(), column.isFullChunk(), column.getChunks(), tiles,
                column.getHeightMaps());
    }

    private static BlockPosition position(Vector3i position) {
        return new BlockPosition(position.getX(), position.getY(), position.getZ());
    }
}
