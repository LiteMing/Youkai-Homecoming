package dev.xkmc.youkaishomecoming.mixin.effect;

import dev.xkmc.youkaishomecoming.init.registrate.YHEffects;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerBeatenElytraMixin {

	@Inject(method = "tryToStartFallFlying", at = @At("HEAD"), cancellable = true)
	private void youkaishomecoming$blockElytraWhenBeaten(CallbackInfoReturnable<Boolean> cir) {
		if (((Player) (Object) this).hasEffect(YHEffects.BEATEN.get())) {
			cir.setReturnValue(false);
		}
	}

}
