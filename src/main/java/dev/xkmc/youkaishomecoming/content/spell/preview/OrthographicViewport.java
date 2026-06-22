package dev.xkmc.youkaishomecoming.content.spell.preview;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.fastprojectileapi.render.core.DanmakuRenderStates;
import dev.xkmc.fastprojectileapi.render.core.ProjTypeHolder;
import dev.xkmc.fastprojectileapi.render.core.ProjectileRenderHelper;
import dev.xkmc.fastprojectileapi.render.core.ProjectileRenderer;
import dev.xkmc.fastprojectileapi.render.type.AnimatedProjectileType;
import dev.xkmc.fastprojectileapi.render.type.RotatingProjectileType;
import dev.xkmc.fastprojectileapi.render.type.SimpleProjectileType;
import dev.xkmc.fastprojectileapi.spellcircle.SpellCircleLayer;
import dev.xkmc.l2serial.util.Wrappers;
import dev.xkmc.youkaishomecoming.compat.ysm.YSMClientCompat;
import dev.xkmc.youkaishomecoming.content.spell.shooter.ShooterEntity;
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
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemDanmakuRenderer;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.TextDanmakuRenderer;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DanmakuItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Renders the spell preview in an orthographic or perspective viewport within a Screen.
 * Works within the existing GUI projection by transforming the PoseStack
 * to map world coordinates into screen pixels.
 * Supports both preset view angles (FRONT/SIDE/TOP) and free orbit rotation.
 * Perspective mode provides FPS-style camera with WASD/Space/Shift movement + mouse look.
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

	// --- Perspective mode ---
	private boolean perspectiveMode = false;
	/** Camera position in world space (perspective mode) */
	private double camX = 0, camY = 2, camZ = -15;
	/** Camera pitch/yaw in degrees (perspective mode) */
	private float camPitch = 0, camYaw = 0;
	/** Whether the mouse is captured for free-look (perspective mode) */
	private boolean perspectiveCaptured = false;
	/** Whether right-mouse orbit is active */
	private boolean perspectiveOrbiting = false;
	/** Whether middle-mouse panning is active */
	private boolean perspectivePanning = false;
	/** Whether the target should follow the camera position in perspective mode */
	private boolean targetBoundToCamera = false;
	/** Marker visibility toggles */
	private boolean showCasterMarker = true;
	private boolean showTargetMarker = true;
	private float moveSpeed = 0.5f;

	/** Action index to highlight in the viewport (-1 = none). */
	private int highlightedActionIndex = -1;
	private static final float MIN_MOVE_SPEED = 0.05f;
	private static final float MAX_MOVE_SPEED = 5.0f;
	private static final float LOOK_SENSITIVITY = 0.15f;
	private static final float PERSP_FOV = 70f; // degrees

	public void setBounds(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	/** Whether the current preset is flipped (viewing from opposite side) */
	private boolean presetFlipped = false;

	public void setViewAngle(ViewAngle angle) {
		if (this.presetAngle == angle && !presetFlipped) {
			// Same preset clicked again without pan: flip to opposite side
			presetFlipped = true;
			this.freeXRot = -angle.getXRot();
			this.freeYRot = angle.getYRot() + 180;
		} else if (this.presetAngle == angle && presetFlipped) {
			// Already flipped: return to normal
			presetFlipped = false;
			this.freeXRot = angle.getXRot();
			this.freeYRot = angle.getYRot();
		} else {
			// Different preset: set normally
			presetFlipped = false;
			this.freeXRot = angle.getXRot();
			this.freeYRot = angle.getYRot();
		}
		this.presetAngle = angle;
		this.viewX = 0;
		this.viewY = 0;
	}

	public boolean isPresetFlipped() {
		return presetFlipped;
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

	// --- Perspective mode API ---

	public boolean isPerspectiveMode() {
		return perspectiveMode;
	}

	public void setPerspectiveMode(boolean perspective) {
		if (this.perspectiveMode == perspective) return;
		this.perspectiveMode = perspective;
		if (perspective) {
			// Default: place camera behind and look toward +Z (toward caster at origin)
			this.camPitch = 0;
			this.camYaw = 0;
			this.camX = 0;
			this.camY = 2;
			this.camZ = -15;
		}
	}

	/**
	 * Set camera position to the dummy target position when entering perspective.
	 * Camera looks toward the caster (at origin).
	 */
	public void setCameraToTarget(Vec3 targetPos) {
		this.camX = targetPos.x;
		this.camY = targetPos.y + 1.6; // eye height
		this.camZ = targetPos.z;
		// Compute yaw to look toward origin (caster position)
		double dx = -targetPos.x;
		double dz = -targetPos.z;
		this.camYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		this.camPitch = 0;
	}

	public Vec3 getCameraPos() {
		return new Vec3(camX, camY, camZ);
	}

	/** Free-look: mouse movement rotates camera (when captured) */
	public void perspectiveLook(float dxPixels, float dyPixels) {
		camYaw += dxPixels * LOOK_SENSITIVITY;
		camPitch += dyPixels * LOOK_SENSITIVITY;
		camPitch = Mth.clamp(camPitch, -89.9f, 89.9f);
	}

	public void setPerspectiveCaptured(boolean captured) {
		this.perspectiveCaptured = captured;
	}

	public boolean isPerspectiveCaptured() {
		return perspectiveCaptured;
	}

	public void setPerspectiveOrbiting(boolean orbiting) {
		this.perspectiveOrbiting = orbiting;
	}

	public boolean isPerspectiveOrbiting() {
		return perspectiveOrbiting;
	}

	public void setPerspectivePanning(boolean panning) {
		this.perspectivePanning = panning;
	}

	public boolean isPerspectivePanning() {
		return perspectivePanning;
	}

	/**
	 * Right-drag orbit: rotate yaw/pitch around a fixed point in front of camera.
	 * Camera orbits around a point at a fixed distance along the look direction.
	 */
	public void perspectiveOrbit(float dxPixels, float dyPixels) {
		float orbitDist = 10f; // distance to orbit center
		// Compute orbit center in world space
		float yawRad = (float) Math.toRadians(camYaw);
		float pitchRad = (float) Math.toRadians(camPitch);
		// Look direction (from perspectiveMove derivation)
		double lx = -Math.sin(yawRad) * Math.cos(pitchRad);
		double ly = -Math.sin(pitchRad);
		double lz = Math.cos(yawRad) * Math.cos(pitchRad);
		double cx = camX + lx * orbitDist;
		double cy = camY + ly * orbitDist;
		double cz = camZ + lz * orbitDist;

		// Rotate angles
		camYaw += dxPixels * LOOK_SENSITIVITY;
		camPitch += dyPixels * LOOK_SENSITIVITY;
		camPitch = Mth.clamp(camPitch, -89.9f, 89.9f);

		// Recompute camera position to maintain orbit center
		float newYawRad = (float) Math.toRadians(camYaw);
		float newPitchRad = (float) Math.toRadians(camPitch);
		double nlx = -Math.sin(newYawRad) * Math.cos(newPitchRad);
		double nly = -Math.sin(newPitchRad);
		double nlz = Math.cos(newYawRad) * Math.cos(newPitchRad);
		camX = cx - nlx * orbitDist;
		camY = cy - nly * orbitDist;
		camZ = cz - nlz * orbitDist;
	}

	/**
	 * Middle-drag pan: move camera on the view plane (perpendicular to look direction).
	 */
	public void perspectivePan(float dxPixels, float dyPixels) {
		float yawRad = (float) Math.toRadians(camYaw);
		float pitchRad = (float) Math.toRadians(camPitch);

		// Right direction in world (view +X): from inverse view matrix
		// R^(-1) col0 with y = camYaw+180: (-cos(camYaw), 0, -sin(camYaw))
		double rx = -Math.cos(yawRad);
		double rz = -Math.sin(yawRad);

		// Up direction in world (view +Y): from inverse view matrix
		float sinX = (float) Math.sin(pitchRad), cosX = (float) Math.cos(pitchRad);
		float sinY = (float) Math.sin(Math.toRadians(camYaw + 180));
		float cosY = (float) Math.cos(Math.toRadians(camYaw + 180));
		double ux = sinY * sinX;
		double uy = cosX;
		double uz = -cosY * sinX;

		float sensitivity = moveSpeed * 0.05f;
		double ddx = dxPixels * sensitivity;
		double ddy = -dyPixels * sensitivity; // screen Y inverted

		camX += rx * ddx + ux * ddy;
		camY += uy * ddy;
		camZ += rz * ddx + uz * ddy;
	}

	/** Adjust fly speed with scroll wheel. */
	public void perspectiveAdjustSpeed(float delta) {
		float factor = delta > 0 ? 1.2f : 1.0f / 1.2f;
		moveSpeed = Math.max(MIN_MOVE_SPEED, Math.min(MAX_MOVE_SPEED, moveSpeed * factor));
	}

	public float getMoveSpeed() {
		return moveSpeed;
	}

	public boolean isTargetBoundToCamera() {
		return targetBoundToCamera;
	}

	public void setTargetBoundToCamera(boolean bound) {
		this.targetBoundToCamera = bound;
	}

	public boolean isShowCasterMarker() { return showCasterMarker; }
	public void setShowCasterMarker(boolean show) { this.showCasterMarker = show; }
	public boolean isShowTargetMarker() { return showTargetMarker; }
	public void setShowTargetMarker(boolean show) { this.showTargetMarker = show; }

	public int getHighlightedActionIndex() { return highlightedActionIndex; }
	public void setHighlightedActionIndex(int index) { this.highlightedActionIndex = index; }

	/** Set rotation gizmo state for rendering. Currently a visual-only hint (no-op on rendering). */
	public void setRotationGizmo(boolean active, float axisX, float axisY, float axisZ) {
		// Reserved for future rotation gizmo rendering
	}

	/** Pending group offset delta accumulated during drag (consumed by callback). */
	private Vec3 pendingGroupOffset = Vec3.ZERO;
	public void addPendingGroupOffset(Vec3 delta) { this.pendingGroupOffset = this.pendingGroupOffset.add(delta); }
	public Vec3 consumePendingGroupOffset() { Vec3 v = pendingGroupOffset; pendingGroupOffset = Vec3.ZERO; return v; }

	public float getZoom() { return zoom; }

	/**
	 * Convert screen coordinates to approximate world position (for hit testing).
	 * Returns the world position at the view plane (depth = 0 in view space).
	 */
	public Vec3 screenToWorld(double screenX, double screenY) {
		if (width <= 0 || height <= 0) return null;
		// Reverse the render transform: screen → view → world
		float xRot = currentXRot();
		float yRot = currentYRot();

		// Screen to view space (before rotation)
		double vx = (screenX - (x + width / 2.0)) / zoom + viewX;
		double vy = -((screenY - (y + height / 2.0)) / zoom) + viewY; // Y inverted

		// Apply inverse rotation to get world coords
		// The render applies: R_x(xRot) * R_y(yRot) to world coords
		// Inverse: R_y(-yRot) * R_x(-xRot) applied to view coords
		double xRad = Math.toRadians(-xRot);
		double yRad = Math.toRadians(-yRot);
		double cosX = Math.cos(xRad), sinX = Math.sin(xRad);
		double cosY = Math.cos(yRad), sinY = Math.sin(yRad);

		// View space: (vx, vy, 0) → apply R_x(-xRot)
		double rx = vx;
		double ry = vy * cosX;
		double rz = vy * sinX;
		// Then R_y(-yRot)
		double wx = rx * cosY + rz * sinY;
		double wy = ry;
		double wz = -rx * sinY + rz * cosY;

		return new Vec3(wx, wy, wz);
	}

	/**
	 * Convert a world position to screen coordinates (for hit testing in orthographic mode).
	 * Returns a Vec3 where x/y are screen pixel coordinates and z is the view-space depth
	 * (useful for front-to-back picking).
	 */
	public Vec3 worldToScreen(Vec3 worldPos) {
		if (width <= 0 || height <= 0) return new Vec3(0, 0, 0);
		float xRot = currentXRot();
		float yRot = currentYRot();
		float xRad = (float) Math.toRadians(xRot);
		float yRad = (float) Math.toRadians(yRot);

		float cosX = (float) Math.cos(xRad), sinX = (float) Math.sin(xRad);
		float cosY = (float) Math.cos(yRad), sinY = (float) Math.sin(yRad);

		// View right vector (screen X) in world space
		float rx = cosY, ry = 0, rz = sinY;
		// View up vector (screen Y) in world space
		float ux = sinY * sinX, uy = cosX, uz = -cosY * sinX;
		// View forward vector (into screen) in world space
		float fx = -sinY * cosX, fy = sinX, fz = cosY * cosX;

		float wx = (float) worldPos.x, wy = (float) worldPos.y, wz = (float) worldPos.z;

		// Project world position onto view axes
		double viewXCoord = rx * wx + ry * wy + rz * wz;
		double viewYCoord = ux * wx + uy * wy + uz * wz;
		double viewZCoord = fx * wx + fy * wy + fz * wz;

		// View → Screen
		double screenX = (viewXCoord - viewX) * zoom + (x + width / 2.0);
		double screenY = -(viewYCoord - viewY) * zoom + (y + height / 2.0);

		return new Vec3(screenX, screenY, viewZCoord);
	}

	/**
	 * Move the camera in perspective mode using WASD/Space/Shift keys.
	 * Called each tick from the screen.
	 *
	 * Rendering applies R_x(camPitch) * R_y(camYaw+180).
	 * The camera look direction (view -Z) in world space is:
	 *   forward = (-sin(camYaw)*cos(camPitch), -sin(camPitch), cos(camYaw)*cos(camPitch))
	 * The camera right direction (view +X) in world space is:
	 *   right = (-cos(camYaw), 0, -sin(camYaw))
	 * For horizontal WASD movement we ignore pitch in forward.
	 */
	public void perspectiveMove(boolean forward, boolean backward, boolean left, boolean right,
								boolean up, boolean down) {
		if (!perspectiveMode) return;
		float yawRad = (float) Math.toRadians(camYaw);

		// Forward direction (horizontal, where camera looks)
		double fx = -Math.sin(yawRad);
		double fz = Math.cos(yawRad);

		// Right direction (perpendicular to forward)
		double rx = -Math.cos(yawRad);
		double rz = -Math.sin(yawRad);

		double dx = 0, dy = 0, dz = 0;
		if (forward) { dx += fx; dz += fz; }
		if (backward) { dx -= fx; dz -= fz; }
		if (left) { dx -= rx; dz -= rz; }
		if (right) { dx += rx; dz += rz; }
		if (up) { dy += 1; }
		if (down) { dy -= 1; }

		// Normalize horizontal movement
		double hlen = Math.sqrt(dx * dx + dz * dz);
		if (hlen > 1e-4) { dx /= hlen; dz /= hlen; }

		camX += dx * moveSpeed;
		camY += dy * moveSpeed;
		camZ += dz * moveSpeed;
	}

	/**
	 * Rotate the view freely by mouse drag delta (in screen pixels).
	 * This exits preset mode and enters free orbit mode.
	 */
	public void rotate(float dxPixels, float dyPixels) {
		if (presetAngle != null) {
			// Initialize free rotation from current preset (respecting flip)
			freeXRot = currentXRot();
			freeYRot = currentYRot();
			presetAngle = null;
			presetFlipped = false;
		}
		float sensitivity = 0.5f;
		freeYRot += dxPixels * sensitivity;
		freeXRot += dyPixels * sensitivity;
		freeXRot = Mth.clamp(freeXRot, -90, 90);
	}

	public String getViewLabel() {
		if (perspectiveMode) {
			String bind = targetBoundToCamera ? " [Bind]" : "";
			return String.format("Persp (%.1f, %.1f, %.1f) Spd:%.2f%s", camX, camY, camZ, moveSpeed, bind);
		}
		if (presetAngle != null) {
			return presetFlipped ? presetAngle.getLabel() + " (Back)" : presetAngle.getLabel();
		}
		return String.format("Free (%.0f, %.0f)", freeXRot, freeYRot);
	}

	public void pan(float dx, float dy) {
		viewX -= dx / zoom;
		viewY += dy / zoom;
	}

	/** 重置视角偏移，使原点（caster 位置）居中 */
	public void resetPan() {
		viewX = 0;
		viewY = 0;
	}

	/**
	 * 将视角偏移设置为使指定世界坐标居中显示。
	 * 需要将世界坐标投影到视角平面空间。
	 */
	public void focusOnWorldPos(Vec3 worldPos) {
		float xRot = currentXRot();
		float yRot = currentYRot();
		float xRad = (float) Math.toRadians(xRot);
		float yRad = (float) Math.toRadians(yRot);

		float cosX = (float) Math.cos(xRad), sinX = (float) Math.sin(xRad);
		float cosY = (float) Math.cos(yRad), sinY = (float) Math.sin(yRad);

		// View right vector (screen X) in world space
		float rx = cosY, ry = 0, rz = sinY;
		// View up vector (screen Y) in world space
		float ux = sinY * sinX, uy = cosX, uz = -cosY * sinX;

		// Project world position onto view axes
		float wx = (float) worldPos.x, wy = (float) worldPos.y, wz = (float) worldPos.z;
		viewX = rx * wx + ry * wy + rz * wz;
		viewY = ux * wx + uy * wy + uz * wz;
	}

	public void zoom(float delta) {
		float factor = delta > 0 ? 1.1f : 0.9f;
		zoom = Mth.clamp(zoom * factor, MIN_ZOOM, MAX_ZOOM);
	}

	private float currentXRot() {
		if (presetAngle != null) {
			return presetFlipped ? -presetAngle.getXRot() : presetAngle.getXRot();
		}
		return freeXRot;
	}

	private float currentYRot() {
		if (presetAngle != null) {
			return presetFlipped ? presetAngle.getYRot() + 180 : presetAngle.getYRot();
		}
		return freeYRot;
	}

	public void render(GuiGraphics guiGraphics, VirtualSpellScene scene, float partialTick) {
		if (width <= 0 || height <= 0) return;

		if (perspectiveMode) {
			renderPerspective(guiGraphics, scene, partialTick);
		} else {
			renderOrthographic(guiGraphics, scene, partialTick);
		}
	}

	private void renderOrthographic(GuiGraphics guiGraphics, VirtualSpellScene scene, float partialTick) {
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

		// Apply pan offset in view space (before rotation so it moves along screen axes)
		poseStack.translate(-viewX, -viewY, 0);

		// Apply view rotation
		ViewAngle.applyRotation(poseStack, xRot, yRot);

		// 5. Set camera orientation override for billboard rendering
		Quaternionf previewOrientation = ViewAngle.computeOrientation(xRot, yRot);
		ProjectileRenderHelper.cameraOrientationOverride = previewOrientation;

		// 6. Enable depth testing for 3D content
		RenderSystem.enableDepthTest();

		// 7. Render grid and axes
		renderGrid(poseStack);
		renderAxes(poseStack);

		renderYsmPreviewCaster(scene, poseStack, buffer, partialTick);

		// 8. Render markers
		renderMarkers(poseStack, scene);

		// 9. Render all entities
		// Set highlight index before rendering so renderDanmakuDirect can apply tint
		highlightedActionIndex = scene.getHolder().getHighlightedActionIndex();
		// Fast path: for danmaku entities, skip EntityRenderDispatcher.getRenderer() lookup
		// (which costs 6.41% frame time per spark profiling) by caching the renderer once.
		dispatcher.setRenderShadow(false);

		// PB3: Extract view matrix once for billboard types.
		// Billboard create() only needs view-space position + scale, which we can compute
		// directly from viewMat × worldPos without any PoseStack push/translate/scale/pop.
		// This eliminates ~17.5% frame time spent on per-entity PoseStack operations.
		Matrix4f viewMat = poseStack.last().pose();
		float viewScale = (float) Math.cbrt(Math.abs(viewMat.determinant3x3()));

		ItemDanmakuRenderer<?> cachedDanmakuRenderer = null;
		for (Entity entity : scene.getHolder().getLocalEntities()) {
			if (entity instanceof ShooterEntity shooter) {
				renderShooterPreview(shooter, poseStack, buffer, partialTick, previewOrientation);
				continue;
			}
			if (entity instanceof ItemDanmakuEntity danmaku) {
				// Fast path: skip dispatcher.getRenderer() for danmaku (all same EntityType)
				if (cachedDanmakuRenderer == null) {
					var r = dispatcher.getRenderer(entity);
					if (r instanceof ItemDanmakuRenderer<?> dr) cachedDanmakuRenderer = dr;
				}
				if (cachedDanmakuRenderer != null) {
					renderDanmakuDirect(cachedDanmakuRenderer, danmaku, viewMat, viewScale, poseStack, partialTick);
					continue;
				}
			}
			renderEntity(dispatcher, entity, poseStack, buffer, partialTick);
		}

		// 10. Flush the deferred danmaku render queue and all remaining buffers
		ProjectileRenderHelper.flushPreviewQueue(buffer);
		buffer.endBatch();

		// 10.5. Render highlight overlay for selected action's danmaku (axis cross at centroid)
		if (highlightedActionIndex >= 0) {
			renderHighlightOverlay(poseStack, scene, highlightedActionIndex, partialTick);
		}

		// 11. Cleanup
		dispatcher.setRenderShadow(true);
		ProjectileRenderHelper.cameraOrientationOverride = null;

		poseStack.popPose();
		guiGraphics.disableScissor();
	}

	/**
	 * Render in perspective (FPS-style) mode.
	 * Uses glViewport to map the perspective projection to the viewport sub-area,
	 * clears depth in that region, then renders with a proper view matrix.
	 */
	private void renderPerspective(GuiGraphics guiGraphics, VirtualSpellScene scene, float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
		MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
		var window = mc.getWindow();

		// 1. Draw dark background using GUI pipeline first
		guiGraphics.fill(x, y, x + width, y + height, 0xFF1a1a2e);

		// 2. Flush all pending GUI draw calls before switching projection
		buffer.endBatch();

		// 3. Save current state
		Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());

		// 4. Set glViewport to the viewport sub-area (in framebuffer pixels)
		double guiScale = window.getGuiScale();
		int fbX = (int) (x * guiScale);
		int fbY = (int) ((window.getGuiScaledHeight() - y - height) * guiScale); // flip Y
		int fbW = (int) (width * guiScale);
		int fbH = (int) (height * guiScale);
		com.mojang.blaze3d.platform.GlStateManager._viewport(fbX, fbY, fbW, fbH);

		// 5. Clear depth buffer in this region so 3D content isn't occluded by GUI
		org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
		org.lwjgl.opengl.GL11.glScissor(fbX, fbY, fbW, fbH);
		org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);
		org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);

		// 6. Set perspective projection
		float aspect = (float) width / height;
		float near = 0.05f;
		float far = clipDepth * 4;
		Matrix4f perspMatrix = new Matrix4f().perspective(
				(float) Math.toRadians(PERSP_FOV), aspect, near, far);
		RenderSystem.setProjectionMatrix(perspMatrix, com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);

		// 7. Set RenderSystem modelview to identity (we bake the view transform into vertices)
		RenderSystem.getModelViewStack().pushPose();
		RenderSystem.getModelViewStack().setIdentity();
		RenderSystem.applyModelViewMatrix();

		// 8. Build view matrix on a fresh PoseStack (not the GUI one)
		PoseStack poseStack = new PoseStack();

		// Camera rotation: pitch then yaw
		poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(camPitch));
		poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(camYaw + 180));

		// Translate world so camera is at origin
		poseStack.translate(-camX, -camY, -camZ);

		// 9. Camera orientation for billboard rendering
		Quaternionf previewOrientation = new Quaternionf()
				.rotationYXZ(
						(float) Math.toRadians(-camYaw - 180),
						(float) Math.toRadians(camPitch),
						0);
		ProjectileRenderHelper.cameraOrientationOverride = previewOrientation;

		// 10. Enable depth testing
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(true);

		// 11. Render grid and axes
		renderGrid(poseStack);
		renderAxes(poseStack);

		renderYsmPreviewCaster(scene, poseStack, buffer, partialTick);

		// 12. Render markers
		renderMarkers(poseStack, scene);

		// 13. Render all entities (same fast path as orthographic)
		dispatcher.setRenderShadow(false);

		// PB3: Extract view matrix for billboard fast path (same as orthographic)
		Matrix4f viewMatP = poseStack.last().pose();
		float viewScaleP = (float) Math.cbrt(Math.abs(viewMatP.determinant3x3()));

		ItemDanmakuRenderer<?> cachedDanmakuRendererP = null;
		for (Entity entity : scene.getHolder().getLocalEntities()) {
			if (entity instanceof ShooterEntity shooter) {
				renderShooterPreview(shooter, poseStack, buffer, partialTick, previewOrientation);
				continue;
			}
			if (entity instanceof ItemDanmakuEntity danmaku) {
				if (cachedDanmakuRendererP == null) {
					var r = dispatcher.getRenderer(entity);
					if (r instanceof ItemDanmakuRenderer<?> dr) cachedDanmakuRendererP = dr;
				}
				if (cachedDanmakuRendererP != null) {
					renderDanmakuDirect(cachedDanmakuRendererP, danmaku, viewMatP, viewScaleP, poseStack, partialTick);
					continue;
				}
			}
			renderEntity(dispatcher, entity, poseStack, buffer, partialTick);
		}

		// 14. Flush
		ProjectileRenderHelper.flushPreviewQueue(buffer);
		buffer.endBatch();

		// 15. Cleanup
		dispatcher.setRenderShadow(true);
		ProjectileRenderHelper.cameraOrientationOverride = null;

		// 16. Restore modelview stack
		RenderSystem.getModelViewStack().popPose();
		RenderSystem.applyModelViewMatrix();

		// 17. Restore glViewport to full window
		com.mojang.blaze3d.platform.GlStateManager._viewport(0, 0,
				window.getWidth(), window.getHeight());

		// 18. Restore GUI projection
		RenderSystem.setProjectionMatrix(savedProjection, com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
	}

	private void renderYsmPreviewCaster(VirtualSpellScene scene, PoseStack poseStack, MultiBufferSource buffer, float partialTick) {
		if (!scene.isYsmPreviewCasterEnabled()) {
			return;
		}
		var holder = scene.getHolder();
		holder.syncFakeCasterFacing();
		var caster = holder.getFakeCaster();
		double ex = Mth.lerp(partialTick, caster.xOld, caster.getX());
		double ey = Mth.lerp(partialTick, caster.yOld, caster.getY());
		double ez = Mth.lerp(partialTick, caster.zOld, caster.getZ());
		float yaw = Mth.rotLerp(partialTick, caster.yBodyRotO, caster.yBodyRot);
		poseStack.pushPose();
		poseStack.translate(ex, ey, ez);
		YSMClientCompat.renderPreviewCaster(holder, yaw, partialTick, poseStack, buffer, LightTexture.FULL_BRIGHT);
		poseStack.popPose();
	}

	private void renderShooterPreview(ShooterEntity shooter, PoseStack poseStack,
			MultiBufferSource buffer, float partialTick, Quaternionf previewOrientation) {
		if (shooter.tickCount <= 0) return;

		double ex = Mth.lerp(partialTick, shooter.xOld, shooter.getX());
		double ey = Mth.lerp(partialTick, shooter.yOld, shooter.getY());
		double ez = Mth.lerp(partialTick, shooter.zOld, shooter.getZ());

		poseStack.pushPose();
		poseStack.translate(ex, ey, ez);
		SpellCircleLayer.renderImpl(poseStack, buffer, LightTexture.FULL_BRIGHT, shooter, partialTick, previewOrientation);
		YSMClientCompat.delegateRender(shooter, shooter.getYRot(), partialTick, poseStack, buffer, LightTexture.FULL_BRIGHT);
		poseStack.popPose();
	}

	/**
	 * PB3: Direct render path for danmaku — eliminates PoseStack entirely for billboard types.
	 * <p>
	 * For billboard types (Simple/Rotating/Animated), computes view-space position directly
	 * via viewMat × worldPos and constructs Ins without any PoseStack push/translate/scale/pop
	 * or determinant extraction. This eliminates ~17.5% frame time (per spark profiling,
	 * renderDanmakuFast was 22.5% with create() only 4.96%).
	 * <p>
	 * For non-billboard types (Swinging/Cross/Butterfly), falls back to the PoseStack path
	 * since they need full rotation chain construction in create().
	 */
	/**
	 * PB3: Direct render path for danmaku — eliminates PoseStack entirely for billboard types.
	 * <p>
	 * For billboard types (Simple/Rotating/Animated), computes view-space position directly
	 * via viewMat × worldPos and constructs Ins without any PoseStack push/translate/scale/pop
	 * or determinant extraction. This eliminates ~17.5% frame time (per spark profiling,
	 * renderDanmakuFast was 22.5% with create() only 4.96%).
	 * <p>
	 * For non-billboard types (Swinging/Cross/Butterfly), falls back to the PoseStack path
	 * since they need full rotation chain construction in create().
	 *
	 * @param poseStack needed only for non-billboard fallback; billboard path ignores it
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private void renderDanmakuDirect(ItemDanmakuRenderer renderer, ItemDanmakuEntity entity,
			Matrix4f viewMat, float viewScale, PoseStack poseStack, float partialTick) {
		if (entity.tickCount <= 0) return;
		if (!(entity.getItem().getItem() instanceof DanmakuItem danmaku)) return;

		var typeHolder = danmaku.getTypeForRender();
		var type = typeHolder.getType();

		// Check if this danmaku should be highlighted
		boolean highlighted = highlightedActionIndex >= 0 && entity.sourceActionIndex == highlightedActionIndex;

		// Billboard types: bypass PoseStack entirely, pure scalar math
		if (type instanceof SimpleProjectileType st) {
			float wx = (float) Mth.lerp(partialTick, entity.xOld, entity.getX());
			float wy = (float) (Mth.lerp(partialTick, entity.yOld, entity.getY()) + entity.getBbHeight() / 2.0);
			float wz = (float) Mth.lerp(partialTick, entity.zOld, entity.getZ());
			float vx = viewMat.m00() * wx + viewMat.m10() * wy + viewMat.m20() * wz + viewMat.m30();
			float vy = viewMat.m01() * wx + viewMat.m11() * wy + viewMat.m21() * wz + viewMat.m31();
			float vz = viewMat.m02() * wx + viewMat.m12() * wy + viewMat.m22() * wz + viewMat.m32();
			float scale = viewScale * entity.scale();
			int col = DanmakuRenderStates.fading(st.display(), -1, renderer, entity);
			if (highlighted) col = applyHighlightTint(col);
			((ProjTypeHolder) typeHolder).accept(new SimpleProjectileType.Ins(vx, vy, vz, scale, col));
		} else if (type instanceof RotatingProjectileType rt) {
			float wx = (float) Mth.lerp(partialTick, entity.xOld, entity.getX());
			float wy = (float) (Mth.lerp(partialTick, entity.yOld, entity.getY()) + entity.getBbHeight() / 2.0);
			float wz = (float) Mth.lerp(partialTick, entity.zOld, entity.getZ());
			float vx = viewMat.m00() * wx + viewMat.m10() * wy + viewMat.m20() * wz + viewMat.m30();
			float vy = viewMat.m01() * wx + viewMat.m11() * wy + viewMat.m21() * wz + viewMat.m31();
			float vz = viewMat.m02() * wx + viewMat.m12() * wy + viewMat.m22() * wz + viewMat.m32();
			float scale = viewScale * entity.scale();
			float zAngle = (float) Math.toRadians((entity.tickCount + partialTick) * 360f / (float) rt.rot());
			int col = DanmakuRenderStates.fading(rt.display(), -1, renderer, entity);
			if (highlighted) col = applyHighlightTint(col);
			((ProjTypeHolder) typeHolder).accept(new RotatingProjectileType.Ins(vx, vy, vz, scale, zAngle, col));
		} else if (type instanceof AnimatedProjectileType at) {
			float wx = (float) Mth.lerp(partialTick, entity.xOld, entity.getX());
			float wy = (float) (Mth.lerp(partialTick, entity.yOld, entity.getY()) + entity.getBbHeight() / 2.0);
			float wz = (float) Mth.lerp(partialTick, entity.zOld, entity.getZ());
			float vx = viewMat.m00() * wx + viewMat.m10() * wy + viewMat.m20() * wz + viewMat.m30();
			float vy = viewMat.m01() * wx + viewMat.m11() * wy + viewMat.m21() * wz + viewMat.m31();
			float vz = viewMat.m02() * wx + viewMat.m12() * wy + viewMat.m22() * wz + viewMat.m32();
			float scale = viewScale * entity.scale();
			int frame = (entity.tickCount / at.ticksPerFrame()) % at.frameCount();
			int col = DanmakuRenderStates.fading(at.display(), -1, renderer, entity);
			if (highlighted) col = applyHighlightTint(col);
			((ProjTypeHolder) typeHolder).accept(new AnimatedProjectileType.Ins(vx, vy, vz, scale, col, frame));
		} else {
			// Non-billboard types (Swinging/Cross/Butterfly): fall back to PoseStack path.
			// These need full rotation chain in create() and can't be simplified to position+scale.
			double ex = Mth.lerp(partialTick, entity.xOld, entity.getX());
			double ey = Mth.lerp(partialTick, entity.yOld, entity.getY());
			double ez = Mth.lerp(partialTick, entity.zOld, entity.getZ());
			double offsetY = entity.getBbHeight() / 2.0;
			poseStack.pushPose();
			poseStack.translate(ex, ey + offsetY, ez);
			float scale = entity.scale();
			poseStack.scale(scale, scale, scale);
			typeHolder.create(renderer, entity, poseStack, partialTick);
			poseStack.popPose();
		}
	}

	@SuppressWarnings("unchecked")
	private <E extends Entity> void renderEntity(
			EntityRenderDispatcher dispatcher, E entity,
			PoseStack poseStack, MultiBufferSource buffer, float partialTick) {
		// Skip entities that haven't been ticked yet — their old/new positions are
		// both at spawn (caster) causing a ghost frame at the origin.
		if (entity.tickCount <= 0) return;

		EntityRenderer<E> renderer = (EntityRenderer<E>) dispatcher.getRenderer(entity);
		if (renderer == null) return;

		double ex = Mth.lerp(partialTick, entity.xOld, entity.getX());
		double ey = Mth.lerp(partialTick, entity.yOld, entity.getY());
		double ez = Mth.lerp(partialTick, entity.zOld, entity.getZ());

		Vec3 offset = renderer.getRenderOffset(entity, partialTick);

		poseStack.pushPose();
		poseStack.translate(ex + offset.x, ey + offset.y, ez + offset.z);

		if (renderer instanceof TextDanmakuRenderer<?>) {
			renderer.render(entity, entity.getYRot(), partialTick, poseStack, buffer,
					LightTexture.FULL_BRIGHT);
		} else if (renderer instanceof ProjectileRenderer<?> pr) {
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
		if (!showCasterMarker && !showTargetMarker) return;

		var tesselator = Tesselator.getInstance();
		var builder = tesselator.getBuilder();
		Matrix4f mat = poseStack.last().pose();

		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		RenderSystem.lineWidth(3.0f);

		builder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

		// Caster marker - red cross + diamond
		if (showCasterMarker) {
			Vec3 cp = scene.getHolder().getFakeCaster().position();
			float cx = (float) cp.x, cy = (float) cp.y, cz = (float) cp.z;
			float cs = 0.8f;
			drawCross3D(builder, mat, cx, cy, cz, cs, 1f, 0.3f, 0.3f, 1f);
			drawDiamond(builder, mat, cx, cy, cz, cs, 1f, 0.2f, 0.2f, 1f);
		}

		// Target marker - yellow cross + diamond
		if (showTargetMarker) {
			Vec3 tp = scene.getHolder().getFakeTarget().position();
			float tx = (float) tp.x, ty = (float) tp.y, tz = (float) tp.z;
			float ts = 1.0f;
			drawCross3D(builder, mat, tx, ty, tz, ts, 1f, 1f, 0.2f, 1f);
			drawDiamond(builder, mat, tx, ty, tz, ts, 1f, 0.8f, 0f, 1f);
		}

		tesselator.end();
		RenderSystem.lineWidth(1.0f);
	}

	/** Draw a diamond outline in both XY and XZ planes. */
	private static void drawDiamond(BufferBuilder builder, Matrix4f mat,
									float x, float y, float z, float s,
									float r, float g, float b, float a) {
		// Diamond in XY plane
		builder.vertex(mat, x, y + s, z).color(r, g, b, a).endVertex();
		builder.vertex(mat, x + s, y, z).color(r, g, b, a).endVertex();
		builder.vertex(mat, x + s, y, z).color(r, g, b, a).endVertex();
		builder.vertex(mat, x, y - s, z).color(r, g, b, a).endVertex();
		builder.vertex(mat, x, y - s, z).color(r, g, b, a).endVertex();
		builder.vertex(mat, x - s, y, z).color(r, g, b, a).endVertex();
		builder.vertex(mat, x - s, y, z).color(r, g, b, a).endVertex();
		builder.vertex(mat, x, y + s, z).color(r, g, b, a).endVertex();
		// Diamond in XZ plane
		builder.vertex(mat, x, y, z + s).color(r, g, b, a).endVertex();
		builder.vertex(mat, x + s, y, z).color(r, g, b, a).endVertex();
		builder.vertex(mat, x + s, y, z).color(r, g, b, a).endVertex();
		builder.vertex(mat, x, y, z - s).color(r, g, b, a).endVertex();
		builder.vertex(mat, x, y, z - s).color(r, g, b, a).endVertex();
		builder.vertex(mat, x - s, y, z).color(r, g, b, a).endVertex();
		builder.vertex(mat, x - s, y, z).color(r, g, b, a).endVertex();
		builder.vertex(mat, x, y, z + s).color(r, g, b, a).endVertex();
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
	 * Render highlight indicators for danmaku belonging to the selected action.
	 * Draws an axis cross at the centroid of highlighted entities.
	 */
	private void renderHighlightOverlay(PoseStack poseStack, VirtualSpellScene scene, int actionIndex, float partialTick) {
		var tesselator = Tesselator.getInstance();
		var builder = tesselator.getBuilder();
		Matrix4f mat = poseStack.last().pose();

		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		RenderSystem.lineWidth(2.0f);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();

		builder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

		// Compute centroid of highlighted danmaku
		Vec3 centroid = Vec3.ZERO;
		int count = 0;
		for (Entity entity : scene.getHolder().getLocalEntities()) {
			if (entity instanceof ItemDanmakuEntity danmaku && danmaku.sourceActionIndex == actionIndex) {
				centroid = centroid.add(entity.position());
				count++;
			}
		}

		// Draw origin axis cross at the centroid
		if (count > 0) {
			centroid = centroid.scale(1.0 / count);
			float cx = (float) centroid.x, cy = (float) centroid.y, cz = (float) centroid.z;
			float axisLen = 2.0f;
			// X axis - red
			builder.vertex(mat, cx, cy, cz).color(1f, 0.2f, 0.2f, 1f).endVertex();
			builder.vertex(mat, cx + axisLen, cy, cz).color(1f, 0.2f, 0.2f, 1f).endVertex();
			// Y axis - green
			builder.vertex(mat, cx, cy, cz).color(0.2f, 1f, 0.2f, 1f).endVertex();
			builder.vertex(mat, cx, cy + axisLen, cz).color(0.2f, 1f, 0.2f, 1f).endVertex();
			// Z axis - blue
			builder.vertex(mat, cx, cy, cz).color(0.2f, 0.2f, 1f, 1f).endVertex();
			builder.vertex(mat, cx, cy, cz + axisLen).color(0.2f, 0.2f, 1f, 1f).endVertex();
			// Center cross
			float ds = 0.3f;
			drawCross3D(builder, mat, cx, cy, cz, ds, 1f, 1f, 1f, 1f);
		}

		tesselator.end();
		RenderSystem.lineWidth(1.0f);
		RenderSystem.disableBlend();
	}

	/**
	 * Apply a green highlight tint to a danmaku color.
	 * Blends the original color toward bright green-white.
	 */
	private static int applyHighlightTint(int col) {
		int a = (col >> 24) & 0xFF;
		int r = (col >> 16) & 0xFF;
		int g = (col >> 8) & 0xFF;
		int b = col & 0xFF;
		// Blend toward green-white (lerp 40% toward highlight color)
		r = r + (255 - r) * 2 / 5;
		g = g + (255 - g) * 3 / 5;
		b = b + (255 - b) * 1 / 5;
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	/**
	 * Convert a screen-space drag delta (pixels) into a world-space delta vector.
	 * Used for moving the target by dragging in the viewport.
	 *
	 * The rendering pipeline transforms world→view as: R = R_x(xRot) * R_y(yRot).
	 * The inverse R^(-1) = R_y(-yRot) * R_x(-xRot) maps view axes back to world:
	 *   view +X (screen right) → world (cos(y), 0, sin(y))
	 *   view +Y (screen up)    → world (sin(y)*sin(x), cos(x), -cos(y)*sin(x))
	 */
	public Vec3 screenDeltaToWorldDelta(float dxPixels, float dyPixels) {
		float xRot = currentXRot();
		float yRot = currentYRot();
		float xRad = (float) Math.toRadians(xRot);
		float yRad = (float) Math.toRadians(yRot);

		float cosX = (float) Math.cos(xRad), sinX = (float) Math.sin(xRad);
		float cosY = (float) Math.cos(yRad), sinY = (float) Math.sin(yRad);

		// View +X (screen right) in world space
		float rx = cosY, ry = 0, rz = sinY;

		// View +Y (screen up) in world space
		float ux = sinY * sinX, uy = cosX, uz = -cosY * sinX;

		float dx = dxPixels / zoom;
		float dy = -dyPixels / zoom; // screen Y inverted (screen down = positive dyPixels)

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
