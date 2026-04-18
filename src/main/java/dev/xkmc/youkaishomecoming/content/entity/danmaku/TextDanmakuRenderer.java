package dev.xkmc.youkaishomecoming.content.entity.danmaku;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.xkmc.fastprojectileapi.render.core.ProjectileRenderHelper;
import dev.xkmc.fastprojectileapi.render.core.ProjectileRenderer;
import dev.xkmc.youkaishomecoming.content.capability.GrazeHelper;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Quaternionf;

@OnlyIn(Dist.CLIENT)
public class TextDanmakuRenderer<T extends TextDanmakuEntity> extends EntityRenderer<T> implements ProjectileRenderer<T> {

	public TextDanmakuRenderer(EntityRendererProvider.Context pContext) {
		super(pContext);
	}

	@Override
	public double fading(dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile e) {
		if (ProjectileRenderHelper.cameraOrientationOverride != null) return 1;
		if (entityRenderDispatcher.camera.getEntity() == e.getOwner()) {
			return YHModConfig.CLIENT.selfDanmakuFading.get();
		}
		return GrazeHelper.globalInvulTime > 0 ? YHModConfig.CLIENT.selfDanmakuFading.get() : 1;
	}

	@Override
	public Quaternionf cameraOrientation() {
		var override = ProjectileRenderHelper.cameraOrientationOverride;
		return override != null ? override : entityRenderDispatcher.cameraOrientation();
	}

	@Override
	protected int getBlockLightLevel(T e, BlockPos pPos) {
		return e.fullBright() ? 15 : super.getBlockLightLevel(e, pPos);
	}

	@Override
	public boolean shouldRender(T e, Frustum pCamera, double pCamX, double pCamY, double pCamZ) {
		return true;
	}

	@Override
	public Vec3 getRenderOffset(T e, float f) {
		return new Vec3(0, e.getBbHeight() / 2, 0);
	}

	@Override
	public void render(T e, float yaw, float pTick, PoseStack pose, MultiBufferSource buffer, int light) {
		renderText(e, pTick, pose, buffer, light);
	}

	@Override
	public void render(T e, float pTick, PoseStack pose) {
		MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
		renderText(e, pTick, pose, bufferSource, LightTexture.FULL_BRIGHT);
	}

	private void renderText(T e, float pTick, PoseStack pose, MultiBufferSource buffer, int light) {
		if (e.text == null || e.text.isEmpty()) return;
		if (e.tickCount < 2) return;

		Font font = Minecraft.getInstance().font;
		float textWidth = Math.max(font.width(e.text), 1);
		float effLen = e.effectiveLength(pTick);
		if (effLen <= 0) return;

		pose.pushPose();

		Quaternionf camRot = cameraOrientation();
		pose.mulPose(camRot);

		float scale = effLen / textWidth;
		float openFactor = e.percentOpen(pTick);
		boolean preview = ProjectileRenderHelper.cameraOrientationOverride != null;
		// Vanilla name-tag pattern: scale(-s, -s, s) flips X+Y for correct text orientation.
		// In preview, OrthographicViewport already applies negative Y scale, so skip the Y flip.
		float yScale = preview ? scale * openFactor : -scale * openFactor;
		pose.scale(-scale, yScale, scale);

		float x = -textWidth / 2f;
		float y = -font.lineHeight / 2f;

		font.drawInBatch(
				e.text,
				x, y,
				e.textColor,
				false,
				pose.last().pose(),
				buffer,
				Font.DisplayMode.SEE_THROUGH,
				0,
				light
		);

		pose.popPose();
	}

	@Override
	public ResourceLocation getTextureLocation(T pEntity) {
		return TextureAtlas.LOCATION_BLOCKS;
	}

}
