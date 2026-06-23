package dev.xkmc.youkaishomecoming.content.spell.shooter;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.xkmc.fastprojectileapi.spellcircle.SpellCircleLayer;
import dev.xkmc.youkaishomecoming.compat.ysm.YSMClientCompat;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public class ShooterRenderer<T extends ShooterEntity> extends EntityRenderer<T> {

	public static final ResourceLocation TEX = YoukaisHomecoming.loc("textures/entities/rumia.png");

	public ShooterRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@Override
	public void render(T e, float yaw, float pTick, PoseStack pose, MultiBufferSource buffer, int light) {
		SpellCircleLayer.renderImpl(pose, buffer, light, e, pTick, entityRenderDispatcher.cameraOrientation());
		if (YSMClientCompat.delegateRender(e, yaw, pTick, pose, buffer, light)) {
			return;
		}
		renderFallbackBody(pose, buffer, light, entityRenderDispatcher.cameraOrientation());
		super.render(e, yaw, pTick, pose, buffer, light);
	}

	public static void renderFallbackBody(PoseStack pose, MultiBufferSource buffer, int light, Quaternionf cameraOrientation) {
		pose.pushPose();
		pose.translate(0, 0.45, 0);
		pose.mulPose(cameraOrientation);
		pose.scale(0.65f, 0.65f, 0.65f);
		VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(TEX));
		Matrix4f mat = pose.last().pose();
		Matrix3f normal = pose.last().normal();
		vertex(vc, mat, normal, -0.45f, -0.45f, 0, 0, 1, light);
		vertex(vc, mat, normal, 0.45f, -0.45f, 0, 1, 1, light);
		vertex(vc, mat, normal, 0.45f, 0.45f, 0, 1, 0, light);
		vertex(vc, mat, normal, -0.45f, 0.45f, 0, 0, 0, light);
		pose.popPose();
	}

	private static void vertex(VertexConsumer vc, Matrix4f mat, Matrix3f normal,
							   float x, float y, float z, float u, float v, int light) {
		vc.vertex(mat, x, y, z)
				.color(255, 255, 255, 220)
				.uv(u, v)
				.overlayCoords(OverlayTexture.NO_OVERLAY)
				.uv2(light)
				.normal(normal, 0, 1, 0)
				.endVertex();
	}

	@Override
	public ResourceLocation getTextureLocation(T pEntity) {
		return TEX;
	}

}
