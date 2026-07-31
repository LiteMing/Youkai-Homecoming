package dev.xkmc.youkaishomecoming.mixin.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.xkmc.youkaishomecoming.compat.ysm.YSMClientCompat;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
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
		float progress = switch (youkai.getBeatenPhase()) {
			case YoukaiEntity.BEATEN_DEFEAT -> Mth.clamp(
					(youkai.getBeatenPhaseTicks() + partialTick) / YoukaiEntity.DEFEAT_ANIMATION_TICKS, 0, 1);
			case YoukaiEntity.BEATEN_FALLING, YoukaiEntity.BEATEN_PRONE -> 1;
			default -> 0;
		};
		if (progress <= 0) return;
		progress = progress * progress * (3 - 2 * progress);
		applyProneRotation(pose, progress);
	}

	static void applyProneRotation(PoseStack pose, float progress) {
		pose.translate(0, 0.2F * progress, 0);
		pose.mulPose(Axis.XP.rotationDegrees(90 * progress));
		pose.translate(0, -0.85F * progress, 0);
	}
}
