package jaydon.antipie.mixin.accessor;

import net.minecraft.world.level.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData$BlockEntityInfo")
public interface ChunkDataBlockEntityInfoAccessor {
	@Accessor("packedXZ")
	int antiPie$getPackedXZ();

	@Accessor("y")
	int antiPie$getY();

	@Accessor("type")
	BlockEntityType<?> antiPie$getType();
}
