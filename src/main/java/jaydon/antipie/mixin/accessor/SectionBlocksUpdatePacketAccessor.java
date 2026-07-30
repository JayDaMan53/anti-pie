package jaydon.antipie.mixin.accessor;

import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundSectionBlocksUpdatePacket.class)
public interface SectionBlocksUpdatePacketAccessor {
	@Accessor("sectionPos")
	SectionPos antiPie$getSectionPos();

	@Accessor("positions")
	short[] antiPie$getPositions();

	@Accessor("states")
	BlockState[] antiPie$getStates();
}
