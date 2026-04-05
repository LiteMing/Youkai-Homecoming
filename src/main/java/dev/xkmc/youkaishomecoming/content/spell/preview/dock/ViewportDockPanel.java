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

		// 视角标签和目标信息
		var font = Minecraft.getInstance().font;
		var tp = scene.getTargetPos();
		String viewLabel = "View: " + viewport.getViewLabel();
		String targetInfo = String.format("Target: (%.1f, %.1f, %.1f)", tp.x, tp.y, tp.z);
		String hitInfo = "  hits:" + scene.getHitCount();

		graphics.drawString(font, viewLabel, x + 4, y + h - 22, 0xFF888888, false);
		graphics.drawString(font, targetInfo, x + 4, y + h - 12, 0xFFBBBB44, false);
		graphics.drawString(font, hitInfo, x + 4 + font.width(targetInfo), y + h - 12, 0xFFFF8844, false);
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
				movingTarget = true;
				return true;
			}
			if (button == 2) {
				dragging = true;
				return true;
			}
			if (button == 1) {
				rotating = true;
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
		if (movingTarget) {
			var delta = viewport.screenDeltaToWorldDelta((float) deltaX, (float) deltaY);
			scene.moveTarget(delta);
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
}
