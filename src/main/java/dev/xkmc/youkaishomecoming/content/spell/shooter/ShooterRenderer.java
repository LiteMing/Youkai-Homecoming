package dev.xkmc.youkaishomecoming.content.spell.shooter;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.xkmc.fastprojectileapi.render.core.ProjectileRenderHelper;
import dev.xkmc.fastprojectileapi.spellcircle.SpellCircleLayer;
import dev.xkmc.youkaishomecoming.compat.ysm.YSMClientCompat;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;

public class ShooterRenderer<T extends ShooterEntity> extends EntityRenderer<T> {

	public static final ResourceLocation TEX = YoukaisHomecoming.loc("textures/entities/rumia.png");

	public ShooterRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@Override
	public void render(T e, float yaw, float pTick, PoseStack pose, MultiBufferSource buffer, int light) {
		Quaternionf cameraOrientation = cameraOrientation();
		SpellCircleLayer.renderImpl(pose, buffer, light, e, pTick, cameraOrientation);
		if (YSMClientCompat.delegateRender(e, yaw, pTick, pose, buffer, light)) {
			return;
		}
		super.render(e, yaw, pTick, pose, buffer, light);
	}

	private Quaternionf cameraOrientation() {
		var override = ProjectileRenderHelper.cameraOrientationOverride;
		return override != null ? override : entityRenderDispatcher.cameraOrientation();
	}

	@Override
	public ResourceLocation getTextureLocation(T pEntity) {
		return TEX;
	}

}
