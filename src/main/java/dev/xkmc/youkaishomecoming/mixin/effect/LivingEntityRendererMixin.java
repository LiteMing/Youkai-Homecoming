package dev.xkmc.youkaishomecoming.mixin.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.xkmc.youkaishomecoming.compat.ysm.YSMClientCompat;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

	@Inject(method = "setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V", at = @At("TAIL"))
	private void youkaishomecoming$applyBeatenPose(LivingEntity entity, PoseStack pose, float ageInTicks,
			float rotationYaw, float partialTick, CallbackInfo ci) {
		if (!(entity instanceof YoukaiEntity youkai) || YSMClientCompat.isDelegatingRender()) return;
		if (youkai.getBeatenPhase() == YoukaiEntity.BEATEN_PRONE) {
			applyProneRotation(pose);
		}
	}

	private static void applyProneRotation(PoseStack pose) {
		pose.translate(0, 0.2F, 0);
		pose.mulPose(Axis.XP.rotationDegrees(90));
		pose.translate(0, -0.85F, 0);
	}
}
