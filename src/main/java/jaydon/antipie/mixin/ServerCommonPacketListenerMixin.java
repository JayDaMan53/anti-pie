package jaydon.antipie.mixin;

import io.netty.channel.ChannelFutureListener;
import jaydon.antipie.VisibilityManager;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerMixin {
	@Unique
	private boolean antiPie$bypass;

	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
			at = @At("HEAD"), cancellable = true)
	private void antiPie$filter(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
		if (antiPie$bypass || !((Object) this instanceof ServerGamePacketListenerImpl gameListener)) return;

		Packet<?> filtered = VisibilityManager.filter(gameListener.player, packet);
		if (filtered == packet) return;

		ci.cancel();
		if (filtered != null) {
			antiPie$bypass = true;
			try {
				((ServerCommonPacketListenerImpl) (Object) this).send(filtered, listener);
			} finally {
				antiPie$bypass = false;
			}
		}
	}
}
