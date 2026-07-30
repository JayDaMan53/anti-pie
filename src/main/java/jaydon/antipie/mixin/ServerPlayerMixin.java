package jaydon.antipie.mixin;

import jaydon.antipie.VisibilityManager;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void antiPie$revealVisibleBlockEntities(CallbackInfo ci) {
		VisibilityManager.tick((ServerPlayer) (Object) this);
	}
}
