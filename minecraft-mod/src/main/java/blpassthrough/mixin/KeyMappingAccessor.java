package blpassthrough.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
	@Accessor("clickCount")
	int blpassthrough$getClickCount();

	@Accessor("clickCount")
	void blpassthrough$setClickCount(int clickCount);
}
