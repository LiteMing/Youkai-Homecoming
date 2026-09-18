package dev.xkmc.youkaishomecoming.mixin.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.xkmc.youkaishomecoming.compat.ysm.YSMClientCompat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds OYSM's native item layer to external living models without linking YH
 * against OYSM classes. The pseudo mixin is ignored when OYSM is not installed.
 */
@Pseudo
@Mixin(targets = "com.elfmcys.yesstevemodel.geckolib3.geo.GeoReplacedEntityRenderer", remap = false)
public abstract class YsmExternalHeldItemLayerMixin {

	@Inject(method = "renderEntityWithTexture", require = 0, at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V", remap = true))
	private void youkaishomecoming$renderHeldItems(@Coerce Object animatable, ResourceLocation texture,
			float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffer, int light, CallbackInfo ci) {
		YSMClientCompat.renderExternalHeldItems(animatable, pose, buffer, light);
	}
}
