package dev.xkmc.youkaishomecoming.mixin.compat;

import dev.xkmc.youkaishomecoming.compat.ysm.YsmPlayerHintBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep the native player renderer, equipment layers and model settings intact. */
@Pseudo
@Mixin(targets = "com.elfmcys.yesstevemodel.client.animation.predicate.PlayerBaseAnimationPredicate", remap = false)
public abstract class YsmPlayerHintMixin {

	@Inject(method = "predicate", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
	private void youkaishomecoming$spellHint(@Coerce Object event, @Coerce Object evaluator,
			CallbackInfoReturnable<Object> callback) {
		Object state = YsmPlayerHintBridge.playHint(event);
		if (state != null) callback.setReturnValue(state);
	}
}
