package dev.xkmc.youkaishomecoming.content.spell.preview;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.xkmc.fastprojectileapi.render.core.ProjectileRenderHelper;
import dev.xkmc.fastprojectileapi.spellcircle.SpellComponent;
import dev.xkmc.fastprojectileapi.spellcircle.SpellRenderState;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemDanmakuRenderer;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemLaserEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.TextDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.spell.action.ShowSpellTitleAction;
import dev.xkmc.youkaishomecoming.content.spell.shooter.ShooterEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.io.ByteArrayOutputStream;

/**
 * 离屏纯净渲染器：在 84x128 独立的 FrameBuffer (TextureTarget) 中
 * 按照当前视口相机矩阵渲染纯净弹幕场景，并合成卡牌边框遮罩，导出 PNG 字节流。
 */
@OnlyIn(Dist.CLIENT)
public final class SpellSnapshotRenderer {

	private static final int WIDTH = SpellCardFrameGenerator.CARD_WIDTH;
	private static final int HEIGHT = SpellCardFrameGenerator.CARD_HEIGHT;
	private static final ResourceLocation SPELL_CIRCLE_TEX = new ResourceLocation("youkaishomecoming", "textures/entities/spell_circle.png");

	private SpellSnapshotRenderer() {
	}

	/**
	 * 拍摄 84x128 符卡卡面快照并导出为 PNG 二进制。
	 */
	@Nullable
	public static byte[] captureSnapshot(VirtualSpellScene scene, OrthographicViewport viewport, float partialTick) {
		RenderTarget target = null;
		NativeImage rawSnapshot = null;
		NativeImage frame = null;
		NativeImage result = null;

		try {
			// 1. 创建 84x128 TextureTarget 离屏缓冲
			target = new TextureTarget(WIDTH, HEIGHT, true, Minecraft.ON_OSX);
			target.setClearColor(0.1f, 0.1f, 0.18f, 1.0f); // 类似视口的深蓝紫黑底色
			target.clear(Minecraft.ON_OSX);

			// 2. 绑定离屏目标并设置正交投影
			target.bindWrite(true);
			RenderSystem.viewport(0, 0, WIDTH, HEIGHT);

			Matrix4f ortho = new Matrix4f().setOrtho(
					-WIDTH / 2.0f, WIDTH / 2.0f,
					-HEIGHT / 2.0f, HEIGHT / 2.0f,
					-1000.0f, 3000.0f);
			RenderSystem.setProjectionMatrix(ortho, com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);

			PoseStack poseStack = new PoseStack();
			poseStack.pushPose();

			// 按照整个视口画面范围进行长边裁切 (Center-Crop to 84:128) 并等比缩放填满快照
			int viewW = Math.max(1, viewport.getWidth());
			int viewH = Math.max(1, viewport.getHeight());
			float scaleFactor = Math.max((float) WIDTH / viewW, (float) HEIGHT / viewH);
			float snapZoom = viewport.getZoom() * scaleFactor;

			poseStack.scale(snapZoom, -snapZoom, snapZoom);
			poseStack.translate(-viewport.getViewX(), -viewport.getViewY(), 0);

			float xRot = viewport.currentXRot();
			float yRot = viewport.currentYRot();
			ViewAngle.applyRotation(poseStack, xRot, yRot);

			Quaternionf previewOrientation = ViewAngle.computeOrientation(xRot, yRot);
			ProjectileRenderHelper.cameraOrientationOverride = previewOrientation;

			RenderSystem.enableDepthTest();
			RenderSystem.depthMask(true);

			Minecraft mc = Minecraft.getInstance();
			MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
			EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();

			// 3. 渲染施法者魔法阵（如有）
			var caster = scene.getHolder().getFakeCaster();
			if (caster != null) {
				double ex = net.minecraft.util.Mth.lerp(partialTick, caster.xOld, caster.getX());
				double ey = net.minecraft.util.Mth.lerp(partialTick, caster.yOld, caster.getY());
				double ez = net.minecraft.util.Mth.lerp(partialTick, caster.zOld, caster.getZ());
				poseStack.pushPose();
				poseStack.translate(ex, ey, ez);
				dev.xkmc.fastprojectileapi.spellcircle.SpellCircleLayer.renderImpl(poseStack, buffer, LightTexture.FULL_BRIGHT,
						caster, partialTick, previewOrientation);
				poseStack.popPose();
			}

			// 4. 渲染纯净实体与弹幕（包含延迟弹幕队列）
			dispatcher.setRenderShadow(false);
			for (Entity entity : scene.getHolder().getLocalEntities()) {
				if (entity.tickCount <= 0 && !(entity instanceof ShooterEntity)) continue;
				var r = dispatcher.getRenderer(entity);
				if (r != null) {
					dispatcher.render(entity, entity.getX(), entity.getY(), entity.getZ(),
							entity.getYRot(), partialTick, poseStack, buffer, LightTexture.FULL_BRIGHT);
				}
			}

			// 关键：冲刷弹幕专用预览渲染队列
			ProjectileRenderHelper.flushPreviewQueue(buffer);
			buffer.endBatch();
			ProjectileRenderHelper.cameraOrientationOverride = null;
			poseStack.popPose();

			// 5. 抓取 FBO 像素到 NativeImage
			rawSnapshot = new NativeImage(WIDTH, HEIGHT, false);
			RenderSystem.bindTexture(target.getColorTextureId());
			rawSnapshot.downloadTexture(0, false);
			// 保持正向（PoseStack 的 -snapZoom 已适配 GUI 方向）

			// 6. 读取边框并在 CPU 侧进行 Alpha 混合覆盖合成
			frame = SpellCardFrameGenerator.getOrCreateFrame();
			result = new NativeImage(WIDTH, HEIGHT, false);

			for (int py = 0; py < HEIGHT; py++) {
				for (int px = 0; px < WIDTH; px++) {
					int frameColor = frame.getPixelRGBA(px, py);
					int frameA = (frameColor >>> 24) & 0xFF;

					if (frameA == 255) {
						// 纯色边框完全覆盖
						result.setPixelRGBA(px, py, frameColor);
					} else if (frameA == 0) {
						// 视窗内部透出快照画面
						result.setPixelRGBA(px, py, rawSnapshot.getPixelRGBA(px, py));
					} else {
						// 半透明羽化区域进行 Alpha 混合 (NativeImage 为 ABGR 存储)
						int snapColor = rawSnapshot.getPixelRGBA(px, py);
						float alpha = frameA / 255.0f;
						float invA = 1.0f - alpha;

						int r = (int) (((frameColor) & 0xFF) * alpha + ((snapColor) & 0xFF) * invA);
						int g = (int) (((frameColor >>> 8) & 0xFF) * alpha + ((snapColor >>> 8) & 0xFF) * invA);
						int b = (int) (((frameColor >>> 16) & 0xFF) * alpha + ((snapColor >>> 16) & 0xFF) * invA);
						int blended = (0xFF << 24) | (b << 16) | (g << 8) | r;
						result.setPixelRGBA(px, py, blended);
					}
				}
			}

			// 7. 导出为 PNG 字节流
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] pngBytes = result.asByteArray();
			return pngBytes != null ? pngBytes : baos.toByteArray();

		} catch (Exception e) {
			e.printStackTrace();
			return null;
		} finally {
			// 恢复主窗口 FBO
			Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
			if (target != null) target.destroyBuffers();
			if (rawSnapshot != null) rawSnapshot.close();
			if (frame != null) frame.close();
			if (result != null) result.close();
		}
	}
}
