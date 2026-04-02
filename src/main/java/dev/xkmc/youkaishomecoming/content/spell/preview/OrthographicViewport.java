package dev.xkmc.youkaishomecoming.content.spell.preview;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.fastprojectileapi.render.core.ProjectileRenderHelper;
import dev.xkmc.fastprojectileapi.render.core.ProjectileRenderer;
import dev.xkmc.l2serial.util.Wrappers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Renders the spell preview in an orthographic viewport within a Screen.
 * Works within the existing GUI projection by transforming the PoseStack
 * to map world coordinates into screen pixels.
 * Supports both preset view angles (FRONT/SIDE/TOP) and free orbit rotation.
 */
@OnlyIn(Dist.CLIENT)
public class OrthographicViewport {

	private int x, y, width, height;
	private float viewX = 0, viewY = 0;
	private float zoom = 20f; // pixels per block

	// View angle: either a preset or free rotation
	@Nullable
	private ViewAngle presetAngle = ViewAngle.FRONT;
	private float freeXRot = 0; // pitch (degrees)
	private float freeYRot = 180; // yaw (degrees)

	private static final float MIN_ZOOM = 5f;
	private static final float MAX_ZOOM = 100f;
	private float gridExtent = 50f;
	private float clipDepth = 200f; // Z translate for depth range

	public void setBounds(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	public void setViewAngle(ViewAngle angle) {
		this.presetAngle = angle;
		this.freeXRot = angle.getXRot();
		this.freeYRot = angle.getYRot();
		this.viewX = 0;
		this.viewY = 0;
	}

	public void setGridExtent(float extent) {
		this.gridExtent = extent;
	}

	public float getGridExtent() {
		return gridExtent;
	}

	public void setClipDepth(float depth) {
		this.clipDepth = depth;
	}

	public float getClipDepth() {
		return clipDepth;
	}

	/**
	 * Rotate the view freely by mouse drag delta (in screen pixels).
	 * This exits preset mode and enters free orbit mode.
	 */
	public void rotate(float dxPixels, float dyPixels) {
		if (presetAngle != null) {
			// Initialize free rotation from current preset
			freeXRot = presetAngle.getXRot();
			freeYRot = presetAngle.getYRot();
			presetAngle = null;
		}
		float sensitivity = 0.5f;
		freeYRot += dxPixels * sensitivity;
		freeXRot += dyPixels * sensitivity;
		freeXRot = Mth.clamp(freeXRot, -90, 90);
	}

	public String getViewLabel() {
		if (presetAngle != null) return presetAngle.getLabel();
		return String.format("Free (%.0f, %.0f)", freeXRot, freeYRot);
	}

	public void pan(float dx, float dy) {
		viewX -= dx / zoom;
		viewY += dy / zoom;
	}

	public void zoom(float delta) {
		float factor = delta > 0 ? 1.1f : 0.9f;
		zoom = Mth.clamp(zoom * factor, MIN_ZOOM, MAX_ZOOM);
	}

	private float currentXRot() {
		return presetAngle != null ? presetAngle.getXRot() : freeXRot;
	}

	private float currentYRot() {
		return presetAngle != null ? presetAngle.getYRot() : freeYRot;
	}

	public void render(GuiGraphics guiGraphics, VirtualSpellScene scene, float partialTick) {
		if (width <= 0 || height <= 0) return;

		Minecraft mc = Minecraft.getInstance();
		EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
		MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

		float xRot = currentXRot();
		float yRot = currentYRot();

		// 1. Scissor
		guiGraphics.enableScissor(x, y, x + width, y + height);

		// 2. Dark background
		guiGraphics.fill(x, y, x + width, y + height, 0xFF1a1a2e);

		// 3. Flush any pending GUI draw calls before 3D rendering
		buffer.endBatch();

		// 4. Set up PoseStack: transform world coords -> screen coords
		PoseStack poseStack = guiGraphics.pose();
		poseStack.pushPose();

		// Move to viewport center in screen coords
		poseStack.translate(x + width / 2.0, y + height / 2.0, clipDepth);

		// Scale: Y inverted (screen Y goes down, world Y goes up)
		poseStack.scale(zoom, -zoom, zoom);

		// Apply view rotation
		ViewAngle.applyRotation(poseStack, xRot, yRot);

		// Apply pan offset in world space
		poseStack.translate(-viewX, -viewY, 0);

		// 5. Set camera orientation override for billboard rendering
		ProjectileRenderHelper.cameraOrientationOverride = ViewAngle.computeOrientation(xRot, yRot);

		// 6. Enable depth testing for 3D content
		RenderSystem.enableDepthTest();

		// 7. Render grid and axes
		renderGrid(poseStack);
		renderAxes(poseStack);

		// 8. Render markers
		renderMarkers(poseStack, scene);

		// 9. Render all entities
		dispatcher.setRenderShadow(false);

		for (Entity entity : scene.getHolder().getLocalEntities()) {
			renderEntity(dispatcher, entity, poseStack, buffer, partialTick);
		}

		// 10. Flush the deferred danmaku render queue and all remaining buffers
		ProjectileRenderHelper.flushPreviewQueue(buffer);
		buffer.endBatch();

		// 11. Cleanup
		dispatcher.setRenderShadow(true);
		ProjectileRenderHelper.cameraOrientationOverride = null;

		poseStack.popPose();
		guiGraphics.disableScissor();
	}

	@SuppressWarnings("unchecked")
	private <E extends Entity> void renderEntity(
			EntityRenderDispatcher dispatcher, E entity,
			PoseStack poseStack, MultiBufferSource buffer, float partialTick) {
		EntityRenderer<E> renderer = (EntityRenderer<E>) dispatcher.getRenderer(entity);
		if (renderer == null) return;

		double ex = Mth.lerp(partialTick, entity.xOld, entity.getX());
		double ey = Mth.lerp(partialTick, entity.yOld, entity.getY());
		double ez = Mth.lerp(partialTick, entity.zOld, entity.getZ());

		Vec3 offset = renderer.getRenderOffset(entity, partialTick);

		poseStack.pushPose();
		poseStack.translate(ex + offset.x, ey + offset.y, ez + offset.z);

		if (renderer instanceof ProjectileRenderer<?> pr) {
			ProjectileRenderer<SimplifiedProjectile> projRenderer = Wrappers.cast(pr);
			projRenderer.render((SimplifiedProjectile) entity, partialTick, poseStack);
		} else {
			renderer.render(entity, entity.getYRot(), partialTick, poseStack, buffer,
					LightTexture.FULL_BRIGHT);
		}

		poseStack.popPose();
	}

	private void renderGrid(PoseStack poseStack) {
		var tesselator = Tesselator.getInstance();
		var builder = tesselator.getBuilder();
		Matrix4f mat = poseStack.last().pose();

		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.lineWidth(1.0f);

		builder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
		drawGridLines(builder, mat, 1, 0.3f, 0.3f, 0.4f, 0.2f);
		drawGridLines(builder, mat, 5, 0.5f, 0.5f, 0.6f, 0.4f);
		tesselator.end();
	}

	private void drawGridLines(BufferBuilder builder, Matrix4f mat, int interval,
							   float r, float g, float b, float a) {
		int count = (int) (gridExtent / interval);
		for (int i = -count; i <= count; i++) {
			float pos = i * interval;
			builder.vertex(mat, -gridExtent, pos, 0).color(r, g, b, a).endVertex();
			builder.vertex(mat, gridExtent, pos, 0).color(r, g, b, a).endVertex();
			builder.vertex(mat, pos, -gridExtent, 0).color(r, g, b, a).endVertex();
			builder.vertex(mat, pos, gridExtent, 0).color(r, g, b, a).endVertex();
		}
	}

	private void renderAxes(PoseStack poseStack) {
		var tesselator = Tesselator.getInstance();
		var builder = tesselator.getBuilder();
		Matrix4f mat = poseStack.last().pose();

		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		RenderSystem.lineWidth(2.0f);

		builder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
		float ext = gridExtent;
		builder.vertex(mat, -ext, 0, 0).color(1f, 0.2f, 0.2f, 0.8f).endVertex();
		builder.vertex(mat, ext, 0, 0).color(1f, 0.2f, 0.2f, 0.8f).endVertex();
		builder.vertex(mat, 0, -ext, 0).color(0.2f, 1f, 0.2f, 0.8f).endVertex();
		builder.vertex(mat, 0, ext, 0).color(0.2f, 1f, 0.2f, 0.8f).endVertex();
		builder.vertex(mat, 0, 0, -ext).color(0.2f, 0.2f, 1f, 0.8f).endVertex();
		builder.vertex(mat, 0, 0, ext).color(0.2f, 0.2f, 1f, 0.8f).endVertex();
		tesselator.end();
		RenderSystem.lineWidth(1.0f);
	}

	private void renderMarkers(PoseStack poseStack, VirtualSpellScene scene) {
		var tesselator = Tesselator.getInstance();
		var builder = tesselator.getBuilder();
		Matrix4f mat = poseStack.last().pose();

		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		RenderSystem.lineWidth(3.0f);

		builder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

		// Caster marker - white cross (small)
		Vec3 cp = scene.getHolder().getFakeCaster().position();
		float cx = (float) cp.x, cy = (float) cp.y, cz = (float) cp.z;
		float s = 0.5f;
		drawCross3D(builder, mat, cx, cy, cz, s, 1f, 1f, 1f, 1f);

		// Target marker - yellow, larger, with diamond outline for visibility
		Vec3 tp = scene.getHolder().getFakeTarget().position();
		float tx = (float) tp.x, ty = (float) tp.y, tz = (float) tp.z;
		float ts = 1.0f;
		drawCross3D(builder, mat, tx, ty, tz, ts, 1f, 1f, 0.2f, 1f);
		// Diamond outline in XY plane
		builder.vertex(mat, tx, ty + ts, tz).color(1f, 0.8f, 0f, 1f).endVertex();
		builder.vertex(mat, tx + ts, ty, tz).color(1f, 0.8f, 0f, 1f).endVertex();
		builder.vertex(mat, tx + ts, ty, tz).color(1f, 0.8f, 0f, 1f).endVertex();
		builder.vertex(mat, tx, ty - ts, tz).color(1f, 0.8f, 0f, 1f).endVertex();
		builder.vertex(mat, tx, ty - ts, tz).color(1f, 0.8f, 0f, 1f).endVertex();
		builder.vertex(mat, tx - ts, ty, tz).color(1f, 0.8f, 0f, 1f).endVertex();
		builder.vertex(mat, tx - ts, ty, tz).color(1f, 0.8f, 0f, 1f).endVertex();
		builder.vertex(mat, tx, ty + ts, tz).color(1f, 0.8f, 0f, 1f).endVertex();
		// Diamond in XZ plane
		builder.vertex(mat, tx, ty, tz + ts).color(1f, 0.8f, 0f, 1f).endVertex();
		builder.vertex(mat, tx + ts, ty, tz).color(1f, 0.8f, 0f, 1f).endVertex();
		builder.vertex(mat, tx + ts, ty, tz).color(1f, 0.8f, 0f, 1f).endVertex();
		builder.vertex(mat, tx, ty, tz - ts).color(1f, 0.8f, 0f, 1f).endVertex();
		builder.vertex(mat, tx, ty, tz - ts).color(1f, 0.8f, 0f, 1f).endVertex();
		builder.vertex(mat, tx - ts, ty, tz).color(1f, 0.8f, 0f, 1f).endVertex();
		builder.vertex(mat, tx - ts, ty, tz).color(1f, 0.8f, 0f, 1f).endVertex();
		builder.vertex(mat, tx, ty, tz + ts).color(1f, 0.8f, 0f, 1f).endVertex();

		tesselator.end();
		RenderSystem.lineWidth(1.0f);
	}

	private static void drawCross3D(BufferBuilder builder, Matrix4f mat,
									 float x, float y, float z, float s,
									 float r, float g, float b, float a) {
		builder.vertex(mat, x - s, y, z).color(r, g, b, a).endVertex();
		builder.vertex(mat, x + s, y, z).color(r, g, b, a).endVertex();
		builder.vertex(mat, x, y - s, z).color(r, g, b, a).endVertex();
		builder.vertex(mat, x, y + s, z).color(r, g, b, a).endVertex();
		builder.vertex(mat, x, y, z - s).color(r, g, b, a).endVertex();
		builder.vertex(mat, x, y, z + s).color(r, g, b, a).endVertex();
	}

	/**
	 * Convert a screen-space drag delta (pixels) into a world-space delta vector.
	 * Used for moving the target by dragging in the viewport.
	 */
	public Vec3 screenDeltaToWorldDelta(float dxPixels, float dyPixels) {
		float xRot = currentXRot();
		float yRot = currentYRot();
		float xRad = (float) Math.toRadians(xRot);
		float yRad = (float) Math.toRadians(yRot);

		// View plane right vector in world space
		float rx = -(float) Math.cos(yRad);
		float ry = 0;
		float rz = (float) Math.sin(yRad);

		// View plane up vector in world space (accounts for pitch)
		float ux = (float) (Math.sin(xRad) * Math.sin(yRad));
		float uy = (float) Math.cos(xRad);
		float uz = (float) (Math.sin(xRad) * Math.cos(yRad));

		float dx = dxPixels / zoom;
		float dy = -dyPixels / zoom; // screen Y inverted

		return new Vec3(
				rx * dx + ux * dy,
				ry * dx + uy * dy,
				rz * dx + uz * dy
		);
	}

	public boolean isMouseOver(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}
}
