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
		switch (youkai.getBeatenPhase()) {
			case YoukaiEntity.BEATEN_DEFEAT -> applyDefeatRecoil(youkai, pose, partialTick);
			case YoukaiEntity.BEATEN_FALLING -> applyFallingRotation(youkai, pose, partialTick);
			case YoukaiEntity.BEATEN_PRONE -> applyProneRotation(pose);
		}
	}

	private static void applyDefeatRecoil(YoukaiEntity youkai, PoseStack pose, float partialTick) {
		float age = youkai.getBeatenPhaseTicks() + partialTick;
		float progress = Mth.clamp(age / YoukaiEntity.DEFEAT_ANIMATION_TICKS, 0, 1);
		float recoil = Mth.sin(Mth.PI * Mth.clamp(progress * 2.4f, 0, 1)) * (1 - progress);
		float tremor = Mth.sin(age * 4.7f) * (1 - progress);
		pose.translate(tremor * 0.012f, recoil * 0.055f, 0);
		pose.mulPose(Axis.ZP.rotationDegrees(tremor * 1.6f));
		pose.scale(1 + recoil * 0.055f, 1 - recoil * 0.08f, 1 + recoil * 0.055f);
	}

	private static void applyFallingRotation(YoukaiEntity youkai, PoseStack pose, float partialTick) {
		float progress = Mth.clamp((youkai.getBeatenPhaseTicks() + partialTick) /
				YoukaiEntity.FALL_SPIN_TICKS, 0, 1);
		float eased = progress * progress * (3 - 2 * progress);
		float angle = 180 * eased;
		// past 90 deg the head drops below the feet origin; lift it back so the final
		// upside-down pose stands head-to-ground, feet-up instead of clipping into the floor
		float lift = 0.9F * Mth.clamp((eased - 0.5F) * 2, 0, 1);
		pose.translate(0, 0.18f * eased, 0);
		pose.mulPose(Axis.XP.rotationDegrees(angle));
		pose.translate(0, -0.72f * eased - lift, 0);
	}

	private static void applyProneRotation(PoseStack pose) {
		pose.translate(0, 0.2F, 0);
		pose.mulPose(Axis.XP.rotationDegrees(90));
		pose.translate(0, -0.85F, 0);
	}
}
