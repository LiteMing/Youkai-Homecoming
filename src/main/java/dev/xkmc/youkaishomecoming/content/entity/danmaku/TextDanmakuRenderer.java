package dev.xkmc.youkaishomecoming.content.entity.danmaku;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.xkmc.fastprojectileapi.render.core.ProjectileRenderHelper;
import dev.xkmc.fastprojectileapi.render.core.ProjectileRenderer;
import dev.xkmc.youkaishomecoming.content.capability.GrazeHelper;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import com.mojang.math.Axis;
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
import org.joml.Vector3f;

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
		MultiBufferSource out = buffer;
		MultiBufferSource.BufferSource isolated = null;
		if (ProjectileRenderHelper.cameraOrientationOverride == null && entityRenderDispatcher.shouldRenderHitBoxes()) {
			isolated = MultiBufferSource.immediate(new BufferBuilder(256));
			out = isolated;
		}
		renderText(e, pTick, pose, out, light);
		if (isolated != null) {
			isolated.endBatch();
		}
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
		if (e.perChar) {
			String visible = choosePerCharText(e, pose);
			renderBillboardText(e, visible, font, pTick, pose, buffer, light);
			return;
		}
		renderSignText(e, e.text, font, pTick, pose, buffer, light);
	}

	private String choosePerCharText(T e, PoseStack pose) {
		if (e.backText == null || e.backText.isEmpty()) return e.text;
		Vec3 forward = e.getForward();
		// Use the current view transform directly so preview/world rendering agree on
		// whether the danmaku line runs left-to-right or right-to-left on screen.
		Vector3f screenForward = pose.last().normal().transform(
				new Vector3f((float) forward.x, (float) forward.y, (float) forward.z));
		return screenForward.x >= 0 ? e.text : e.backText;
	}

	private void renderBillboardText(T e, String text, Font font, float pTick, PoseStack pose, MultiBufferSource buffer, int light) {
		float effLen = e.effectiveLength(pTick);
		if (effLen <= 0) return;

		pose.pushPose();
		if (ProjectileRenderHelper.cameraOrientationOverride == null) {
			pose.mulPose(cameraOrientation());
		}

		float openFactor = e.percentOpen(pTick);
		float glyphScale = getGlyphScale(e, font);
		float slotAdvance = getSlotAdvance(text, effLen, glyphScale);
		pose.scale(-glyphScale, -glyphScale * openFactor, glyphScale);
		drawTextCentered(font, text, slotAdvance, e.textColor, pose, buffer, light);
		pose.popPose();
	}

	private void renderSignText(T e, String text, Font font, float pTick, PoseStack pose, MultiBufferSource buffer, int light) {
		float effLen = e.effectiveLength(pTick);
		if (effLen <= 0) return;

		float openFactor = e.percentOpen(pTick);
		float yaw = e.getViewYRot(pTick);
		float pitch = e.getViewXRot(pTick);
		float glyphScale = getGlyphScale(e, font);
		float slotAdvance = getSlotAdvance(text, effLen, glyphScale);

		// Front face: local +X follows the full flight direction, including pitch.
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(-yaw - 90));
		pose.mulPose(Axis.ZP.rotationDegrees(-pitch));
		pose.mulPose(Axis.XP.rotationDegrees(e.roll));
		pose.scale(glyphScale, -glyphScale * openFactor, glyphScale);
		drawTextAnchored(font, text, 0, slotAdvance, e.textColor, pose, buffer, light);
		pose.popPose();

		// Back face: mirror the plane while preserving the same world-space extent.
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(90 - yaw));
		pose.mulPose(Axis.ZP.rotationDegrees(pitch));
		pose.mulPose(Axis.XP.rotationDegrees(-e.roll));
		pose.scale(glyphScale, -glyphScale * openFactor, glyphScale);
		drawTextAnchored(font, text, -slotAdvance * countCodePoints(text), slotAdvance, e.textColor, pose, buffer, light);
		pose.popPose();
	}

	private float getGlyphScale(T e, Font font) {
		return Math.max(e.getBbHeight(), e.getBbWidth()) / font.lineHeight;
	}

	private float getSlotAdvance(String text, float worldLength, float glyphScale) {
		int count = countCodePoints(text);
		if (count <= 0) return 0;
		return worldLength / Math.max(glyphScale, 0.0001f) / count;
	}

	private int countCodePoints(String text) {
		return text.codePointCount(0, text.length());
	}

	private String[] splitCodePoints(String text) {
		int n = countCodePoints(text);
		String[] ans = new String[n];
		int idx = 0;
		for (int pos = 0; pos < text.length();) {
			int cp = text.codePointAt(pos);
			ans[idx++] = new String(Character.toChars(cp));
			pos += Character.charCount(cp);
		}
		return ans;
	}

	private void drawTextCentered(Font font, String text, float slotAdvance, int color, PoseStack pose, MultiBufferSource buffer, int light) {
		drawGlyphRun(font, text, -slotAdvance * countCodePoints(text) / 2f, slotAdvance, color, pose, buffer, light);
	}

	private void drawTextAnchored(Font font, String text, float startX, float slotAdvance, int color, PoseStack pose, MultiBufferSource buffer, int light) {
		drawGlyphRun(font, text, startX, slotAdvance, color, pose, buffer, light);
	}

	private void drawGlyphRun(Font font, String text, float startX, float slotAdvance, int color, PoseStack pose, MultiBufferSource buffer, int light) {
		String[] glyphs = splitCodePoints(text);
		if (glyphs.length == 0) return;
		float drawY = -font.lineHeight / 2f;
		for (int i = 0; i < glyphs.length; i++) {
			String glyph = glyphs[i];
			float glyphWidth = font.width(glyph);
			float x = startX + slotAdvance * i + (slotAdvance - glyphWidth) / 2f;
			drawText(font, glyph, x, drawY, color, pose, buffer, light);
		}
	}

	private void drawText(Font font, String text, float x, float y, int color, PoseStack pose, MultiBufferSource buffer, int light) {
		font.drawInBatch(
				text,
				x, y,
				color,
				false,
				pose.last().pose(),
				buffer,
				Font.DisplayMode.NORMAL,
				0,
				light
		);
	}

	@Override
	public ResourceLocation getTextureLocation(T pEntity) {
		return TextureAtlas.LOCATION_BLOCKS;
	}

}
