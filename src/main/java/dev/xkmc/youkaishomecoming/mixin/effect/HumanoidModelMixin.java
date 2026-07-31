package dev.xkmc.youkaishomecoming.mixin.effect;

import dev.xkmc.youkaishomecoming.init.registrate.YHEffects;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {

	@Shadow public ModelPart body;
	@Shadow public ModelPart rightArm;
	@Shadow public ModelPart leftArm;
	@Shadow public ModelPart rightLeg;
	@Shadow public ModelPart leftLeg;

	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void youkaishomecoming$fixBeatenLimbs(LivingEntity entity, float limbSwing, float limbSwingAmount,
			float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
		if (!(entity instanceof Player) || !entity.hasEffect(YHEffects.BEATEN.get())) return;
		resetRotation(body);
		resetRotation(rightArm);
		resetRotation(leftArm);
		resetRotation(rightLeg);
		resetRotation(leftLeg);
	}

	private static void resetRotation(ModelPart part) {
		part.xRot = 0;
		part.yRot = 0;
		part.zRot = 0;
	}
}
