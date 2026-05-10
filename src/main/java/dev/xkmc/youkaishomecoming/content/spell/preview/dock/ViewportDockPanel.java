package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

import dev.xkmc.youkaishomecoming.content.spell.preview.OrthographicViewport;
import dev.xkmc.youkaishomecoming.content.spell.preview.VirtualSpellScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 可停靠的 3D 视口面板。包装 {@link OrthographicViewport}，
 * 从 SpellPreviewScreen 提取视口相关的交互逻辑。
 */
@OnlyIn(Dist.CLIENT)
public class ViewportDockPanel implements DockPanel {

	private final OrthographicViewport viewport;
	private final VirtualSpellScene scene;

	private int x, y, w, h;

	// 正交模式下的交互状态
	private boolean movingTarget = false;
	private boolean dragging = false;  // 中键平移
	private boolean rotating = false;  // 右键旋转

	// Group transform interaction state
	private boolean groupDragging = false;   // 左键拖动选中组（修改 origin offset）
	private boolean groupRotating = false;   // 右键拖动选中组（修改 angle_offset）
	private boolean rotateMode = false;      // R键旋转模式
	private int rotateAxis = 1;              // 旋转轴: 0=X, 1=Y, 2=Z
	private Runnable onGroupOffsetChanged;   // 回调：origin offset 变化
	private java.util.function.DoubleConsumer onGroupAngleChanged; // 回调：angle_offset 变化
	private Runnable onGroupDeselect;        // 回调：取消选择
	private java.util.function.IntConsumer onClickSelectAction; // 回调：点击弹幕选中 action (传入 action index)

	// 透视模式鼠标追踪
	private double lastMouseX, lastMouseY;

	public ViewportDockPanel(OrthographicViewport viewport, VirtualSpellScene scene) {
		this.viewport = viewport;
		this.scene = scene;
	}

	// ---- DockPanel 基础实现 ----

	@Override
	public String dockTitle() {
		return "Viewport";
	}

	@Override
	public String dockId() {
		return "viewport";
	}

	@Override
	public void setBounds(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		viewport.setBounds(x, y, w, h);
	}

	@Override
	public int getX() { return x; }

	@Override
	public int getY() { return y; }

	@Override
	public int getWidth() { return w; }

	@Override
	public int getHeight() { return h; }

	// ---- 渲染 ----

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		viewport.render(graphics, scene, partialTick);

		// Render mode indicator overlay
		if (hasHighlightedGroup()) {
			var font = Minecraft.getInstance().font;
			String modeText;
			int modeColor;
			if (rotateMode) {
				String axisName = switch (rotateAxis) { case 0 -> "X"; case 1 -> "Y"; default -> "Z"; };
				int axisColor = switch (rotateAxis) { case 0 -> 0xFFFF4444; case 1 -> 0xFF44FF44; default -> 0xFF4444FF; };
				modeText = "ROTATE " + axisName;
				modeColor = axisColor;
			} else {
				modeText = "SELECTED [R=Rotate]";
				modeColor = 0xFF66FF88;
			}
			graphics.drawString(font, modeText, x + 4, y + h - 12, modeColor, true);
		}
	}

	// ---- 鼠标事件 ----

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!viewport.isMouseOver(mouseX, mouseY)) return false;

		if (viewport.isPerspectiveMode()) {
			if (!viewport.isPerspectiveCaptured()) {
				if (button == 0) {
					viewport.setPerspectiveCaptured(true);
					lastMouseX = mouseX;
					lastMouseY = mouseY;
					org.lwjgl.glfw.GLFW.glfwSetInputMode(
							Minecraft.getInstance().getWindow().getWindow(),
							org.lwjgl.glfw.GLFW.GLFW_CURSOR,
							org.lwjgl.glfw.GLFW.GLFW_CURSOR_DISABLED);
					return true;
				}
			}
			if (button == 1) {
				viewport.setPerspectiveOrbiting(true);
				return true;
			}
			if (button == 2) {
				viewport.setPerspectivePanning(true);
				return true;
			}
		} else {
			// 正交模式
			if (button == 0) {
				// If in rotate mode, click confirms and exits
				if (rotateMode) {
					rotateMode = false;
					return true;
				}

				// Try to click-select a danmaku entity
				int hitAction = hitTestDanmaku(mouseX, mouseY);
				if (hitAction >= 0) {
					// Clicked on a danmaku → select its action
					if (onClickSelectAction != null) onClickSelectAction.accept(hitAction);
					return true;
				}

				// Clicked on empty space
				if (hasHighlightedGroup()) {
					// Deselect
					if (onGroupDeselect != null) onGroupDeselect.run();
					return true;
				}

				// No selection → move target
				movingTarget = true;
				return true;
			}
			if (button == 2) {
				dragging = true;
				return true;
			}
			if (button == 1) {
				if (hasHighlightedGroup() && rotateMode) {
					groupRotating = true; // Only rotate in rotate mode
				} else if (hasHighlightedGroup()) {
					groupDragging = true; // Right drag = move offset when not in rotate mode
				} else {
					rotating = true;
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (viewport.isPerspectiveOrbiting()) {
			viewport.perspectiveOrbit((float) deltaX, (float) deltaY);
			return true;
		}
		if (viewport.isPerspectivePanning()) {
			viewport.perspectivePan((float) deltaX, (float) deltaY);
			return true;
		}
		if (rotateMode && hasHighlightedGroup()) {
			// In rotate mode, any drag rotates the group
			double angleDelta = deltaX * 0.5;
			if (onGroupAngleChanged != null) onGroupAngleChanged.accept(angleDelta);
			return true;
		}
		if (movingTarget) {
			var delta = viewport.screenDeltaToWorldDelta((float) deltaX, (float) deltaY);
			scene.moveTarget(delta);
			return true;
		}
		if (groupDragging) {
			// Move origin offset: convert screen delta to world delta
			var delta = viewport.screenDeltaToWorldDelta((float) deltaX, (float) deltaY);
			// Store delta in viewport for the callback to read
			viewport.addPendingGroupOffset(delta);
			if (onGroupOffsetChanged != null) onGroupOffsetChanged.run();
			return true;
		}
		if (groupRotating) {
			// Rotate angle_offset: horizontal drag = angle change
			double angleDelta = deltaX * 0.5; // 0.5 degrees per pixel
			if (onGroupAngleChanged != null) onGroupAngleChanged.accept(angleDelta);
			return true;
		}
		if (dragging) {
			viewport.pan((float) deltaX, (float) deltaY);
			return true;
		}
		if (rotating) {
			viewport.rotate((float) deltaX, (float) deltaY);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (viewport.isPerspectiveOrbiting() && button == 1) {
			viewport.setPerspectiveOrbiting(false);
			return true;
		}
		if (viewport.isPerspectivePanning() && button == 2) {
			viewport.setPerspectivePanning(false);
			return true;
		}
		if (movingTarget && button == 0) {
			movingTarget = false;
			return true;
		}
		if (groupDragging && (button == 0 || button == 1)) {
			groupDragging = false;
			return true;
		}
		if (groupRotating && button == 1) {
			groupRotating = false;
			return true;
		}
		if (dragging && button == 2) {
			dragging = false;
			return true;
		}
		if (rotating && button == 1) {
			rotating = false;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (!viewport.isMouseOver(mouseX, mouseY)) return false;

		if (viewport.isPerspectiveCaptured()) {
			viewport.perspectiveAdjustSpeed((float) delta);
			return true;
		}
		if (viewport.isPerspectiveMode()) {
			viewport.perspectiveAdjustSpeed((float) delta);
		} else {
			viewport.zoom((float) delta);
		}
		return true;
	}

	// ---- 透视模式鼠标移动（由 Screen.mouseMoved 调用） ----

	/**
	 * 处理透视捕获模式下的鼠标移动（自由视角旋转）。
	 * 需要由 Screen.mouseMoved() 手动调用。
	 *
	 * @return true 如果事件被消费
	 */
	public boolean mouseMoved(double mouseX, double mouseY) {
		if (viewport.isPerspectiveCaptured()) {
			double dx = mouseX - lastMouseX;
			double dy = mouseY - lastMouseY;
			lastMouseX = mouseX;
			lastMouseY = mouseY;
			viewport.perspectiveLook((float) dx, (float) dy);
			return true;
		}
		return false;
	}

	// ---- 每 tick 更新（透视 WASD 移动） ----

	/**
	 * 每 tick 调用一次，处理透视模式下的 WASD 摄像机移动和目标同步。
	 *
	 * @param anyEditBoxFocused 当前是否有 EditBox 聚焦
	 */
	public void tick(boolean anyEditBoxFocused) {
		if (viewport.isPerspectiveCaptured() && !anyEditBoxFocused) {
			long window = Minecraft.getInstance().getWindow().getWindow();
			boolean forward = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_W) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
			boolean backward = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_S) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
			boolean left = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_A) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
			boolean right = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_D) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
			boolean up = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
			boolean down = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
			viewport.perspectiveMove(forward, backward, left, right, up, down);

			if (viewport.isTargetBoundToCamera()) {
				Vec3 camPos = viewport.getCameraPos();
				scene.setTargetPos(new Vec3(camPos.x, camPos.y - 1.6, camPos.z));
			}
		}
	}

	// ---- 公共访问 ----

	public OrthographicViewport getViewport() {
		return viewport;
	}

	public VirtualSpellScene getScene() {
		return scene;
	}

	public void setGroupTransformCallbacks(Runnable onOffsetChanged, java.util.function.DoubleConsumer onAngleChanged,
										   Runnable onDeselect, java.util.function.IntConsumer onClickSelect) {
		this.onGroupOffsetChanged = onOffsetChanged;
		this.onGroupAngleChanged = onAngleChanged;
		this.onGroupDeselect = onDeselect;
		this.onClickSelectAction = onClickSelect;
	}

	/** Check if a group action is currently highlighted (for interaction mode switching). */
	private boolean hasHighlightedGroup() {
		return scene.getHolder().getHighlightedActionIndex() >= 0;
	}

	/**
	 * Hit-test: find which danmaku entity is under the given screen coordinates.
	 * Returns the sourceActionIndex of the hit entity, or -1 if nothing hit.
	 */
	private int hitTestDanmaku(double screenX, double screenY) {
		// Convert screen coords to world coords using viewport's inverse transform
		Vec3 worldPos = viewport.screenToWorld(screenX, screenY);
		if (worldPos == null) return -1;

		float hitRadius = 1.0f / viewport.getZoom() * 5; // 5 pixels tolerance
		double bestDistSq = hitRadius * hitRadius;
		int bestAction = -1;

		for (var entity : scene.getHolder().getLocalEntities()) {
			if (entity instanceof dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemDanmakuEntity danmaku) {
				Vec3 pos = entity.position();
				// Project both to 2D (ignore depth axis based on view angle)
				Vec3 diff = pos.subtract(worldPos);
				double distSq = diff.x * diff.x + diff.y * diff.y + diff.z * diff.z;
				if (distSq < bestDistSq) {
					bestDistSq = distSq;
					bestAction = danmaku.sourceActionIndex;
				}
			}
		}
		return bestAction;
	}

	/**
	 * Handle key presses for rotation mode.
	 * R = enter rotate mode, X/Y/Z = switch axis, Escape = cancel.
	 */
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (!hasHighlightedGroup()) return false;

		// R = toggle rotate mode
		if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_R) {
			rotateMode = !rotateMode;
			rotateAxis = 1; // default Y
			return true;
		}

		if (rotateMode) {
			// X/Y/Z = switch axis
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_X) { rotateAxis = 0; return true; }
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_Y) { rotateAxis = 1; return true; }
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_Z) { rotateAxis = 2; return true; }
			// Escape = cancel rotate mode
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { rotateMode = false; return true; }
		}

		return false;
	}

	/** Get the current rotate mode axis (0=X, 1=Y, 2=Z). -1 if not in rotate mode. */
	public int getRotateAxis() {
		return rotateMode ? rotateAxis : -1;
	}

	public boolean isRotateMode() {
		return rotateMode;
	}
}
