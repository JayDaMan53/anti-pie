package jaydon.antipie.mixin.accessor;

import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ClientboundLevelChunkPacketData.class)
public interface ChunkDataAccessor {
	@Accessor("blockEntitiesData")
	List<Object> antiPie$getBlockEntitiesData();
}
