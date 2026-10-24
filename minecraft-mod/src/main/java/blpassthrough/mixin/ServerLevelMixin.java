package blpassthrough.mixin;

import blpassthrough.Passthrough;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
	@Inject(method = "sendBlockUpdated", at = @At("HEAD"))
	private void blpassthrough$mirrorBlock(BlockPos pos, BlockState old, BlockState current, int updateFlags, CallbackInfo ci) {
		if (old != current) {
			Passthrough.get().worldBridge().onBlockChanged((ServerLevel) (Object) this, pos, old, current);
		}
	}
}
