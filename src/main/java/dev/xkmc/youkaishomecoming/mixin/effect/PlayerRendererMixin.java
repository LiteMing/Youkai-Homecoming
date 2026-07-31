package dev.xkmc.youkaishomecoming.mixin.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.xkmc.youkaishomecoming.init.registrate.YHEffects;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {

	@Redirect(
			method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;getSwimAmount(F)F")
	)
	private float youkaishomecoming$disableBeatenSwimRotation(AbstractClientPlayer player, float partialTick) {
		return player.hasEffect(YHEffects.BEATEN.get()) ? 0 : player.getSwimAmount(partialTick);
	}

	@Inject(
			method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V",
			at = @At("TAIL")
	)
	private void youkaishomecoming$applyBeatenPose(AbstractClientPlayer player, PoseStack pose, float ageInTicks,
			float rotationYaw, float partialTick, CallbackInfo ci) {
		if (player.hasEffect(YHEffects.BEATEN.get())) {
			applyProneRotation(pose);
		}
	}

	private static void applyProneRotation(PoseStack pose) {
		pose.translate(0, 0.2F, 0);
		pose.mulPose(Axis.XP.rotationDegrees(90));
		pose.translate(0, -0.85F, 0);
	}
}
