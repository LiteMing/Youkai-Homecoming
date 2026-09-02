package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.spell.preview.OrthographicViewport;
import dev.xkmc.youkaishomecoming.content.spell.preview.SpellEditorLocalization;
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
	private boolean movingBlockTarget = false;
	private boolean movingCaster = false;
	private boolean dragging = false;  // 中键平移
	private boolean rotating = false;  // 右键旋转

	// Group transform interaction state
	private boolean groupDragging = false;   // 右键拖动选中组（修改 origin offset）
	private double groupDragDistance = 0;
	private double groupPendingDx = 0, groupPendingDy = 0;
	private boolean originEditMode = false;
	private boolean groupRotating = false;   // 右键拖动选中组（修改 group rotation）
	private boolean rotateMode = false;      // R键旋转模式
	private int rotateAxis = 1;              // 旋转轴: 0=X, 1=Y, 2=Z
	private boolean groupRotationAvailable = false;
	/** True after we've already pushed one undo snapshot for the current drag gesture. */
	private boolean dragUndoPushed = false;
	private java.util.function.Consumer<Vec3> onGroupOffsetChanged; // delta in world coords
	private java.util.function.DoubleConsumer onGroupAngleChanged;  // angle delta in degrees
	private Runnable onGroupDragBegin;       // 回调：拖拽刚开始（用于 push undo 一次）
	private java.util.function.BooleanSupplier onBeginOriginEdit;
	private Runnable onApplyOriginEdit;
	private Runnable onCancelOriginEdit;
	private Runnable onGroupDeselect;        // 回调：取消选择
	private Runnable onTriggerSnapshotConfirm; // 回调：点击取景框拍照确认
	private java.util.function.IntConsumer onClickSelectAction; // 回调：点击弹幕选中 action (传入 action index)
	private java.util.function.BooleanSupplier isEditBoxFocusedSupplier; // 回调：检查是否有 EditBox 聚焦

	// Rotation gizmo state: when a SpawnShooterAction with rotationMover is selected,
	// this stores the rotation axis for gizmo rendering and drag interaction.
	private boolean rotationGizmoActive = false;
	private double rotationGizmoAxisX, rotationGizmoAxisY, rotationGizmoAxisZ;
	private java.util.function.DoubleConsumer onRotationSpeedChanged; // degrees_per_tick delta
	private MagicCircleDockPanel magicCircleEditor;
	private boolean magicCircleItemDragging = false;
	private boolean magicCircleItemRotating = false;

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
		// Sync rotation gizmo state to the viewport for rendering
		viewport.setRotationGizmo(rotationGizmoActive,
				(float) rotationGizmoAxisX, (float) rotationGizmoAxisY, (float) rotationGizmoAxisZ);
		viewport.render(graphics, scene, partialTick);

		// World rendering leaves depth testing enabled. Keep all viewport interaction
		// controls on a single GUI overlay plane above bullets/lasers so a large
		// projectile cannot cover the button or its hover label.
		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 400);
		renderOriginEditControls(graphics, mouseX, mouseY);

		// Persistent control legend + live hover/drag feedback so it is always clear
		// what a drag will move (view / caster / target / danmaku).
		renderLegend(graphics);
		renderInteractionFeedback(graphics, mouseX, mouseY);
		renderSnapButtonIfGuideActive(graphics, mouseX, mouseY);
		graphics.pose().popPose();
	}

	private void renderOriginEditControls(GuiGraphics graphics, int mouseX, int mouseY) {
		if (isMagicCirclePreviewEditing() || viewport.isPerspectiveMode() || !hasHighlightedGroup()) return;
		int toolbarW = originEditMode ? 104 : rotateMode ? 116 : groupRotationAvailable ? 132 : 68;
		int[] anchor = transformToolbarPosition(toolbarW);
		renderTransformAxes(graphics, anchor[2], anchor[3]);
		int bx = anchor[0], by = anchor[1];
		if (originEditMode) {
			drawOriginEditButton(graphics, bx, by, 48, "Apply", true, mouseX, mouseY);
			drawOriginEditButton(graphics, bx + 52, by, 52, "Cancel", true, mouseX, mouseY);
		} else if (rotateMode) {
			drawOriginEditButton(graphics, bx, by, 24, "X", true, mouseX, mouseY);
			drawOriginEditButton(graphics, bx + 28, by, 24, "Y", true, mouseX, mouseY);
			drawOriginEditButton(graphics, bx + 56, by, 24, "Z", true, mouseX, mouseY);
			drawOriginEditButton(graphics, bx + 84, by, 32, "Done", true, mouseX, mouseY);
		} else {
			if (scene.isPlaying()) return;
			drawOriginEditButton(graphics, bx, by, 68, "Move Origin", true, mouseX, mouseY);
			if (groupRotationAvailable) {
				drawOriginEditButton(graphics, bx + 72, by, 60, "Rotate Group", true, mouseX, mouseY);
			}
		}
	}

	private int[] transformToolbarPosition(int toolbarW) {
		double sx = x + w * 0.5;
		double sy = y + h * 0.5;
		int selected = scene.getHolder().getHighlightedActionIndex();
		int count = 0;
		for (var entity : scene.getHolder().getLocalEntities()) {
			int source = entity instanceof ItemDanmakuEntity danmaku ? danmaku.sourceActionIndex
					: entity instanceof dev.xkmc.youkaishomecoming.content.entity.danmaku.YHBaseLaserEntity laser
					? laser.sourceActionIndex : -1;
			if (source != selected) continue;
			Vec3 anchor = entity.position();
			if (entity instanceof dev.xkmc.youkaishomecoming.content.entity.danmaku.YHBaseLaserEntity laser) {
				double visibleLength = laser.earlyTerminate < 0
						? laser.getLength() : Math.min(laser.getLength(), laser.earlyTerminate);
				anchor = laser.position().add(0, laser.getBbHeight() / 2, 0)
						.add(laser.getForward().scale(visibleLength * 0.5));
			}
			Vec3 screen = viewport.worldToScreen(anchor);
			sx += screen.x;
			sy += screen.y;
			count++;
		}
		if (count > 0) {
			sx = (sx - (x + w * 0.5)) / count;
			sy = (sy - (y + h * 0.5)) / count;
		}
		int axisX = (int) Math.round(sx);
		int axisY = (int) Math.round(sy);
		int bx = Math.max(x + 4, Math.min(x + w - toolbarW - 4, axisX + 18));
		int by = Math.max(y + 4, Math.min(y + h - 22, axisY - 9));
		return new int[]{bx, by, axisX, axisY};
	}

	private void renderTransformAxes(GuiGraphics graphics, int axisX, int axisY) {
		graphics.fill(axisX, axisY, axisX + 14, axisY + 2, 0xFFDD5555);
		graphics.fill(axisX, axisY - 14, axisX + 2, axisY, 0xFF55DD77);
		for (int i = 0; i < 10; i++) {
			graphics.fill(axisX - i, axisY + i, axisX - i + 2, axisY + i + 2, 0xFF5588EE);
		}
	}

	private void drawOriginEditButton(GuiGraphics graphics, int bx, int by, int bw, String label,
			boolean enabled, int mouseX, int mouseY) {
		int bh = 18;
		boolean hovered = enabled && mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
		int fill = !enabled ? 0x99303030 : hovered ? 0xDD3F6F55 : 0xCC25372E;
		int border = enabled ? 0xFF66DD99 : 0xFF666666;
		graphics.fill(bx, by, bx + bw, by + bh, fill);
		graphics.fill(bx, by, bx + bw, by + 1, border);
		graphics.fill(bx, by + bh - 1, bx + bw, by + bh, border);
		String text = SpellEditorLocalization.t(label);
		var font = Minecraft.getInstance().font;
		graphics.drawString(font, text, bx + Math.max(3, (bw - font.width(text)) / 2),
				by + 5, enabled ? 0xFFFFFFFF : 0xFF888888, false);
	}

	private boolean clickOriginEditControls(double mouseX, double mouseY) {
		if (isMagicCirclePreviewEditing() || viewport.isPerspectiveMode() || !hasHighlightedGroup()) return false;
		int toolbarW = originEditMode ? 104 : rotateMode ? 116 : groupRotationAvailable ? 132 : 68;
		int[] anchor = transformToolbarPosition(toolbarW);
		int bx = anchor[0], by = anchor[1];
		if (originEditMode) {
			if (inside(mouseX, mouseY, bx, by, 48, 18)) {
				finishOriginEdit(true);
				return true;
			}
			if (inside(mouseX, mouseY, bx + 52, by, 52, 18)) {
				finishOriginEdit(false);
				return true;
			}
		} else if (rotateMode) {
			if (inside(mouseX, mouseY, bx, by, 24, 18)) { rotateAxis = 0; return true; }
			if (inside(mouseX, mouseY, bx + 28, by, 24, 18)) { rotateAxis = 1; return true; }
			if (inside(mouseX, mouseY, bx + 56, by, 24, 18)) { rotateAxis = 2; return true; }
			if (inside(mouseX, mouseY, bx + 84, by, 32, 18)) { rotateMode = false; return true; }
		} else if (!scene.isPlaying()) {
			if (inside(mouseX, mouseY, bx, by, 68, 18)) {
				if (onBeginOriginEdit != null && onBeginOriginEdit.getAsBoolean()) originEditMode = true;
				return true;
			}
			if (groupRotationAvailable && inside(mouseX, mouseY, bx + 72, by, 60, 18)) {
				rotateMode = true;
				rotateAxis = 1;
				return true;
			}
		}
		return false;
	}

	private static boolean inside(double mouseX, double mouseY, int bx, int by, int bw, int bh) {
		return mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
	}

	private void finishOriginEdit(boolean apply) {
		groupDragging = false;
		groupPendingDx = groupPendingDy = 0;
		originEditMode = false;
		if (apply) {
			if (onApplyOriginEdit != null) onApplyOriginEdit.run();
		} else if (onCancelOriginEdit != null) {
			onCancelOriginEdit.run();
		}
	}

	private void renderSnapButtonIfGuideActive(GuiGraphics graphics, int mouseX, int mouseY) {
		if (!viewport.isCardFrameGuideActive()) return;
		var font = Minecraft.getInstance().font;

		int frameW = viewport.getWidth() * 128 > viewport.getHeight() * 84
				? Math.round((viewport.getHeight() * 84f) / 128f)
				: viewport.getWidth();
		int fx = x + (w - frameW) / 2;
		int fy = y + 10;

		// 在取景框右上角悬浮醒目的拍照按钮 [📸 拍摄卡面]
		int btnW = 88;
		int btnH = 20;
		int btnX = fx + frameW - btnW - 4;
		int btnY = fy + 4;

		boolean hovered = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 300);
		graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, hovered ? 0xDDFFD700 : 0xBB222233);
		graphics.fill(btnX, btnY, btnX + btnW, btnY + 1, 0xFFFFD700);
		graphics.fill(btnX, btnY + btnH - 1, btnX + btnW, btnY + btnH, 0xFFFFD700);
		graphics.fill(btnX, btnY, btnX + 1, btnY + btnH, 0xFFFFD700);
		graphics.fill(btnX + btnW - 1, btnY, btnX + btnW, btnY + btnH, 0xFFFFD700);

		String text = SpellEditorLocalization.t("📸 确认拍摄");
		int tx = btnX + (btnW - font.width(text)) / 2;
		int ty = btnY + (btnH - font.lineHeight) / 2;
		graphics.drawString(font, text, tx, ty, hovered ? 0xFF000000 : 0xFFFFD700, false);
		graphics.pose().popPose();
	}

	// ---- Interaction feedback (legend + hover/drag indicators) ----

	/** Always-visible control legend; its contents reflect the current interaction state. */
	private void renderLegend(GuiGraphics graphics) {
		var font = Minecraft.getInstance().font;
		String l1, l2 = null;
		int c1, c2 = 0;
		if (isMagicCirclePreviewEditing()) {
			if (magicCircleItemDragging) {
				l1 = "Moving item layer";
				c1 = 0xFF66FF88;
			} else if (magicCircleItemRotating) {
				l1 = "Rotating item layer";
				c1 = 0xFFFFCC66;
			} else {
				l1 = "Magic Circle  LMB drag item: move / RMB drag item: rotate";
				c1 = 0xFFCCCCCC;
				l2 = "MMB pan / RMB empty orbit / wheel zoom";
				c2 = 0xFFAAAAAA;
			}
		} else if (viewport.isPerspectiveMode()) {
			if (hasHighlightedGroup()) {
				l1 = "SELECTED — switch to orthographic to edit";
				c1 = 0xFFFFAA44;
			} else {
				l1 = "Perspective  LMB look · RMB orbit · MMB pan · wheel speed";
				c1 = 0xFFBBBBBB;
			}
		} else if (rotateMode) {
			c1 = axisColor(rotateAxis);
			c2 = c1;
			l1 = "ROTATE " + axisName(rotateAxis) + "  LMB drag: rotate";
			l2 = "X/Y/Z axis · Esc/R exit · RMB orbit";
		} else if (originEditMode) {
			l1 = "ORIGIN EDIT  LMB drag bullets · Apply or Cancel";
			c1 = 0xFF66FF88;
			l2 = "Paused frame updates directly; playback stays stopped";
			c2 = 0xFF99CCAA;
		} else if (hasHighlightedGroup()) {
			l1 = scene.isPlaying() ? "SELECTED  Pause preview to edit origin" : "SELECTED  Use Edit Origin to enable dragging";
			c1 = 0xFF66FF88;
			l2 = "R rotate · RMB orbit · MMB pan · wheel zoom";
			c2 = 0xFF99CCAA;
		} else {
			l1 = "LMB select bullet · drag caster/entity/block target";
			c1 = 0xFFCCCCCC;
			l2 = "RMB orbit · MMB pan · wheel zoom";
			c2 = 0xFFAAAAAA;
		}
		int lines = l2 == null ? 1 : 2;
		int ly = y + h - 4 - lines * 10;
		graphics.drawString(font, SpellEditorLocalization.t(l1), x + 4, ly, c1, true);
		if (l2 != null) graphics.drawString(font, SpellEditorLocalization.t(l2), x + 4, ly + 10, c2, true);
	}

	/**
	 * While dragging, label what is being moved. Otherwise, highlight the object the
	 * cursor is hovering (caster/target marker or danmaku) with a ring + label near the cursor.
	 */
	private void renderInteractionFeedback(GuiGraphics graphics, int mouseX, int mouseY) {
		var font = Minecraft.getInstance().font;
		String dragLabel = activeDragLabel();
		if (dragLabel != null) {
			graphics.drawString(font, SpellEditorLocalization.t(dragLabel), mouseX + 8, mouseY - 4, 0xFFFFFFFF, true);
			return;
		}
		if (!viewport.isMouseOver(mouseX, mouseY)) return;
		if (isMagicCirclePreviewEditing()) {
			renderMagicCircleInteractionFeedback(graphics, mouseX, mouseY);
			return;
		}
		if (viewport.isPerspectiveMode()) return;
		int hm = hitTestMarker(mouseX, mouseY);
		if (hm == 0) {
			markerRing(graphics, scene.getHolder().getFakeCaster().position(), 7, 0xFFFF5555);
			graphics.drawString(font, SpellEditorLocalization.t("Caster — drag to move"), mouseX + 8, mouseY - 4, 0xFFFF7777, true);
			return;
		}
		if (hm == 1) {
			markerRing(graphics, scene.getHolder().getFakeTarget().position(), 7, 0xFFFFEE55);
			graphics.drawString(font, SpellEditorLocalization.t("Entity Target — drag to move"), mouseX + 8, mouseY - 4, 0xFFFFEE77, true);
			return;
		}
		if (hm == 2) {
			markerRing(graphics, scene.getBlockTargetHandlePos(), 7, 0xFF55CCFF);
			graphics.drawString(font, SpellEditorLocalization.t("Block Target — drag to move"), mouseX + 8, mouseY - 4, 0xFF77DDFF, true);
			return;
		}
		ProjectilePick pick = pickProjectile(mouseX, mouseY);
		if (pick != null) {
			drawRing(graphics, pick.screenPoint().x, pick.screenPoint().y, 5, 0xFF66DDFF);
			boolean selected = pick.actionIndex() >= 0
					&& pick.actionIndex() == scene.getHolder().getHighlightedActionIndex();
			String noun = pick.entity() instanceof dev.xkmc.youkaishomecoming.content.entity.danmaku.YHBaseLaserEntity
					? "Laser" : "Danmaku";
			String label = selected && originEditMode ? noun + " — drag to move origin" : noun + " — click to select";
			graphics.drawString(font, SpellEditorLocalization.t(label), mouseX + 8, mouseY - 4, 0xFF99E6FF, true);
		}
	}

	/** The label for whatever is currently being dragged, or null if no drag is active. */
	private String activeDragLabel() {
		if (magicCircleItemDragging) return "Moving item layer";
		if (magicCircleItemRotating) return "Rotating item layer";
		if (movingCaster) return "Moving Caster";
		if (movingTarget) return "Moving Target";
		if (movingBlockTarget) return "Moving Block Target";
		if (groupDragging) return "Moving origin";
		if (groupRotating) return "Rotating " + axisName(rotateAxis);
		if (rotating || viewport.isPerspectiveOrbiting()) return "Orbit view";
		if (dragging || viewport.isPerspectivePanning()) return "Pan view";
		return null;
	}

	private void markerRing(GuiGraphics graphics, Vec3 worldPos, int radius, int color) {
		Vec3 sp = viewport.worldToScreen(worldPos);
		drawRing(graphics, sp.x, sp.y, radius, color);
	}

	private void drawRing(GuiGraphics graphics, double sx, double sy, int radius, int color) {
		int x0 = (int) Math.round(sx) - radius;
		int y0 = (int) Math.round(sy) - radius;
		graphics.renderOutline(x0, y0, radius * 2, radius * 2, color);
	}

	private void renderMagicCircleInteractionFeedback(GuiGraphics graphics, int mouseX, int mouseY) {
		if (magicCircleEditor == null) {
			return;
		}
		var font = Minecraft.getInstance().font;
		int selected = magicCircleEditor.getSelectedItemIndex();
		if (selected >= 0) {
			Vec3 sp = magicCircleItemScreenPosition(selected);
			drawRing(graphics, sp.x, sp.y, magicCircleItemRadius(selected) + 2, 0xFFFFDD55);
		}
		int hover = hitTestMagicCircleItem(mouseX, mouseY);
		if (hover >= 0) {
			Vec3 sp = magicCircleItemScreenPosition(hover);
			drawRing(graphics, sp.x, sp.y, magicCircleItemRadius(hover), 0xFF66DDFF);
			String label = hover == selected ? "Item layer - LMB move / RMB rotate" : "Item layer - click to select";
			graphics.drawString(font, SpellEditorLocalization.t(label), mouseX + 8, mouseY - 4, 0xFF99E6FF, true);
		}
	}

	private boolean mouseClickedMagicCircle(double mouseX, double mouseY, int button) {
		if (magicCircleEditor == null) {
			return false;
		}
		if (button == 0) {
			int hit = hitTestMagicCircleItem(mouseX, mouseY);
			if (hit >= 0) {
				magicCircleEditor.selectItem(hit);
				magicCircleItemDragging = true;
			}
			return true;
		}
		if (button == 1) {
			int hit = hitTestMagicCircleItem(mouseX, mouseY);
			if (hit >= 0) {
				magicCircleEditor.selectItem(hit);
				magicCircleItemRotating = true;
				return true;
			}
			rotating = true;
			return true;
		}
		if (button == 2) {
			dragging = true;
			return true;
		}
		return true;
	}

	private boolean isMagicCirclePreviewEditing() {
		return viewport.isMagicCirclePreviewActive() && magicCircleEditor != null;
	}

	private int hitTestMagicCircleItem(double screenX, double screenY) {
		if (!isMagicCirclePreviewEditing() || magicCircleEditor == null) {
			return -1;
		}
		double bestDistSq = Double.POSITIVE_INFINITY;
		int best = -1;
		for (int i = magicCircleEditor.getItemCount() - 1; i >= 0; i--) {
			Vec3 sp = magicCircleItemScreenPosition(i);
			double dx = sp.x - screenX;
			double dy = sp.y - screenY;
			double radius = magicCircleItemRadius(i);
			double distSq = dx * dx + dy * dy;
			if (distSq <= radius * radius && distSq < bestDistSq) {
				bestDistSq = distSq;
				best = i;
			}
		}
		return best;
	}

	private Vec3 magicCircleItemScreenPosition(int index) {
		if (magicCircleEditor == null) {
			return Vec3.ZERO;
		}
		Vec3 pos = magicCircleEditor.getItemPosition(index);
		return viewport.magicCircleLocalToScreen(pos.x, pos.y, pos.z);
	}

	private int magicCircleItemRadius(int index) {
		if (magicCircleEditor == null) {
			return 8;
		}
		double localRadius = Math.max(6.0, Math.abs(magicCircleEditor.getItemScale(index)) * 0.5);
		return (int) Math.round(Math.max(8.0, viewport.magicCircleLocalUnitsToPixels(localRadius)));
	}

	private static String axisName(int axis) {
		return switch (axis) { case 0 -> "X"; case 1 -> "Y"; default -> "Z"; };
	}

	private static int axisColor(int axis) {
		return switch (axis) { case 0 -> 0xFFFF4444; case 1 -> 0xFF44FF44; default -> 0xFF4444FF; };
	}

	// ---- 鼠标事件 ----

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!viewport.isMouseOver(mouseX, mouseY)) return false;
		if (button == 0 && clickOriginEditControls(mouseX, mouseY)) return true;

		if (isMagicCirclePreviewEditing()) {
			return mouseClickedMagicCircle(mouseX, mouseY, button);
		}

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
				// 检查是否点击了取景框旁的确认拍照按钮
				if (viewport.isCardFrameGuideActive()) {
					int frameW = viewport.getWidth() * 128 > viewport.getHeight() * 84
							? Math.round((viewport.getHeight() * 84f) / 128f)
							: viewport.getWidth();
					int fx = x + (w - frameW) / 2;
					int fy = y + 10;
					int btnW = 88;
					int btnH = 20;
					int btnX = fx + frameW - btnW - 4;
					int btnY = fy + 4;
					if (mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH) {
						if (onTriggerSnapshotConfirm != null) {
							onTriggerSnapshotConfirm.run();
						}
						return true;
					}
				}
				// Rotate mode: LMB drag rotates the selected group around the active axis.
				if (rotateMode) {
					if (hasHighlightedGroup()) {
						groupRotating = true;
						dragUndoPushed = false;
					}
					return true;
				}

				// Hit-test markers first (caster/target) — they sit on top in the editor.
				int hitMarker = hitTestMarker(mouseX, mouseY);
				if (hitMarker == 0) {
					movingCaster = true;
					return true;
				}
				if (hitMarker == 1) {
					movingTarget = true;
					return true;
				}
				if (hitMarker == 2) {
					movingBlockTarget = true;
					return true;
				}

				// Click a danmaku: select its action. If it is already the selected action,
				// begin an origin drag instead (grab the bullets and drag to move the origin).
				int hitAction = hitTestDanmaku(mouseX, mouseY);
				if (hitAction >= 0) {
					if (hitAction == scene.getHolder().getHighlightedActionIndex() && originEditMode) {
						// Origin editing is only valid against a stable paused frame. A
						// running preview must not reset/replay while the pointer moves.
						if (!scene.isPlaying()) {
							groupDragging = true;
							groupDragDistance = 0;
							groupPendingDx = groupPendingDy = 0;
							dragUndoPushed = false;
						}
					} else if (originEditMode) {
						return true;
					} else if (onClickSelectAction != null) {
						onClickSelectAction.accept(hitAction);
					}
					return true;
				}

				// Empty space: deselect if something is selected. It no longer moves the
				// target (drag the target marker for that) — that overload was the main
				// source of "what am I dragging?" confusion.
				if (hasHighlightedGroup()) {
					if (originEditMode) return true;
					// If an editbox was focused, just consume the click to unfocus it
					// (Screen-level code handles the actual unfocusing).
					if (isEditBoxFocusedSupplier != null && isEditBoxFocusedSupplier.getAsBoolean()) {
						return true;
					}
					if (onGroupDeselect != null) onGroupDeselect.run();
				}
				return true;
			}
			if (button == 2) {
				dragging = true; // MMB = pan
				return true;
			}
			if (button == 1) {
				rotating = true; // RMB always orbits the camera (no longer stolen by selection)
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (magicCircleItemDragging) {
			if (magicCircleEditor != null) {
				int selected = magicCircleEditor.getSelectedItemIndex();
				double localZ = selected < 0 ? 0 : magicCircleEditor.getItemPosition(selected).z;
				Vec3 delta = viewport.screenDeltaToMagicCircleLocalDelta((float) deltaX, (float) deltaY, localZ);
				magicCircleEditor.moveSelectedItem(delta.x, delta.y);
			}
			return true;
		}
		if (magicCircleItemRotating) {
			if (magicCircleEditor != null) {
				magicCircleEditor.rotateSelectedItem(deltaX * 0.5);
			}
			return true;
		}
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
		if (movingBlockTarget) {
			var delta = viewport.screenDeltaToWorldDelta((float) deltaX, (float) deltaY);
			scene.moveBlockTarget(delta);
			return true;
		}
		if (movingCaster) {
			var delta = viewport.screenDeltaToWorldDelta((float) deltaX, (float) deltaY);
			scene.moveCaster(delta);
			return true;
		}
		if (groupDragging) {
			if (scene.isPlaying()) {
				groupDragging = false;
				return true;
			}
			// Move origin offset: convert screen delta to world delta
			groupPendingDx += deltaX;
			groupPendingDy += deltaY;
			groupDragDistance += Math.hypot(deltaX, deltaY);
			if (groupDragDistance < 4.0) return true;
			var delta = viewport.screenDeltaToWorldDelta((float) groupPendingDx, (float) groupPendingDy);
			groupPendingDx = groupPendingDy = 0;
			if (onGroupOffsetChanged != null) onGroupOffsetChanged.accept(delta);
			return true;
		}
		if (groupRotating) {
			// Rotate group_rotation around the active axis. In rotate mode, allow any button drag;
			// outside rotate mode this branch only runs when button==1 (set in mouseClicked).
			double angleDelta = deltaX * 0.5; // 0.5 degrees per pixel
			ensureDragUndoPushed();
			if (rotationGizmoActive && onRotationSpeedChanged != null) {
				// When rotation gizmo is active, modify degrees_per_tick instead of group rotation
				onRotationSpeedChanged.accept(angleDelta * 0.1); // finer control: 0.05 deg/tick per pixel
			} else if (onGroupAngleChanged != null) {
				onGroupAngleChanged.accept(angleDelta);
			}
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

	private void ensureDragUndoPushed() {
		if (!dragUndoPushed) {
			if (onGroupDragBegin != null) onGroupDragBegin.run();
			dragUndoPushed = true;
		}
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (magicCircleItemDragging && button == 0) {
			magicCircleItemDragging = false;
			return true;
		}
		if (magicCircleItemRotating && button == 1) {
			magicCircleItemRotating = false;
			return true;
		}
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
		if (movingBlockTarget && button == 0) {
			movingBlockTarget = false;
			return true;
		}
		if (movingCaster && button == 0) {
			movingCaster = false;
			return true;
		}
		if (groupDragging && button == 0) {
			groupDragging = false;
			dragUndoPushed = false;
			groupPendingDx = groupPendingDy = 0;
			return true;
		}
		if (groupRotating && button == 0) {
			groupRotating = false;
			dragUndoPushed = false;
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

		}
		if (viewport.isPerspectiveMode() && viewport.isTargetBoundToCamera()) {
			Vec3 camPos = viewport.getCameraPos();
			scene.setTargetPos(new Vec3(camPos.x, camPos.y - 1.6, camPos.z));
			scene.setTargetFacing(viewport.getCameraLookDirection());
		}
	}

	// ---- 公共访问 ----

	public void setTriggerSnapshotConfirmCallback(Runnable callback) {
		this.onTriggerSnapshotConfirm = callback;
	}

	public OrthographicViewport getViewport() {
		return viewport;
	}

	public VirtualSpellScene getScene() {
		return scene;
	}

	public void setGroupTransformCallbacks(java.util.function.Consumer<Vec3> onOffsetChanged,
										   java.util.function.DoubleConsumer onAngleChanged,
										   Runnable onDragBegin,
										   Runnable onDeselect,
										   java.util.function.IntConsumer onClickSelect) {
		this.onGroupOffsetChanged = onOffsetChanged;
		this.onGroupAngleChanged = onAngleChanged;
		this.onGroupDragBegin = onDragBegin;
		this.onGroupDeselect = onDeselect;
		this.onClickSelectAction = onClickSelect;
	}

	public void setOriginEditCallbacks(java.util.function.BooleanSupplier begin, Runnable apply, Runnable cancel) {
		this.onBeginOriginEdit = begin;
		this.onApplyOriginEdit = apply;
		this.onCancelOriginEdit = cancel;
	}

	public boolean isOriginEditMode() {
		return originEditMode;
	}

	public void cancelOriginEdit() {
		if (originEditMode) finishOriginEdit(false);
	}

	/** Set a supplier that returns true when an EditBox in the editor is focused. */
	public void setEditBoxFocusedSupplier(java.util.function.BooleanSupplier supplier) {
		this.isEditBoxFocusedSupplier = supplier;
	}

	/**
	 * Set the rotation gizmo state. When active, the viewport displays a rotation axis
	 * indicator and drag interactions modify the rotation speed instead of group rotation.
	 *
	 * @param active whether the rotation gizmo should be shown
	 * @param axisX  rotation axis X component (normalized)
	 * @param axisY  rotation axis Y component (normalized)
	 * @param axisZ  rotation axis Z component (normalized)
	 */
	public void setRotationGizmo(boolean active, double axisX, double axisY, double axisZ) {
		this.rotationGizmoActive = active;
		this.rotationGizmoAxisX = axisX;
		this.rotationGizmoAxisY = axisY;
		this.rotationGizmoAxisZ = axisZ;
	}

	public void setGroupRotationAvailable(boolean available) {
		groupRotationAvailable = available;
		if (!available) rotateMode = false;
	}

	/** Set the callback for rotation speed changes (degrees_per_tick delta). */
	public void setOnRotationSpeedChanged(java.util.function.DoubleConsumer callback) {
		this.onRotationSpeedChanged = callback;
	}

	public void setMagicCircleEditor(MagicCircleDockPanel editor) {
		this.magicCircleEditor = editor;
	}

	/** Whether the rotation gizmo is currently active (selected action has a rotation mover). */
	public boolean isRotationGizmoActive() {
		return rotationGizmoActive;
	}

	/** Check if a group action is currently highlighted (for interaction mode switching). */
	private boolean hasHighlightedGroup() {
		return scene.getHolder().getHighlightedActionIndex() >= 0;
	}

	/**
	 * Hit-test: detect caster/target marker under the given screen coordinates.
	 * Returns 0 = caster, 1 = entity target, 2 = block target, -1 = none.
	 * visibility toggle is off in OrthographicViewport. Perspective mode is unsupported.
	 */
	private int hitTestMarker(double screenX, double screenY) {
		if (viewport.isPerspectiveMode()) return -1;

		double pixelTolSq = 12.0 * 12.0; // markers are larger than danmaku, allow a bit more slack
		double bestDistSq = pixelTolSq;
		int bestKind = -1;

		if (viewport.isShowCasterMarker()) {
			Vec3 cp = scene.getHolder().getFakeCaster().position();
			Vec3 sp = viewport.worldToScreen(cp);
			double dx = sp.x - screenX, dy = sp.y - screenY;
			double distSq = dx * dx + dy * dy;
			if (distSq <= bestDistSq) {
				bestDistSq = distSq;
				bestKind = 0;
			}
		}
		if (viewport.isShowTargetMarker()) {
			Vec3 tp = scene.getHolder().getFakeTarget().position();
			Vec3 sp = viewport.worldToScreen(tp);
			double dx = sp.x - screenX, dy = sp.y - screenY;
			double distSq = dx * dx + dy * dy;
			if (distSq <= bestDistSq) {
				bestDistSq = distSq;
				bestKind = 1;
			}
			Vec3 bp = scene.getBlockTargetHandlePos();
			sp = viewport.worldToScreen(bp);
			dx = sp.x - screenX;
			dy = sp.y - screenY;
			distSq = dx * dx + dy * dy;
			if (distSq <= bestDistSq) {
				bestKind = 2;
			}
		}
		return bestKind;
	}

	/**
	 * Hit-test: find which danmaku entity is under the given screen coordinates.
	 * Projects each danmaku's world position to screen pixels and compares 2D distance,
	 * so picking works at any view angle (not just when bullets lie on the view plane).
	 * Among all candidates within the pixel tolerance, picks the front-most (smallest viewspace z).
	 * Returns the sourceActionIndex of the hit entity, or -1 if nothing hit.
	 */
	private int hitTestDanmaku(double screenX, double screenY) {
		ProjectilePick pick = pickProjectile(screenX, screenY);
		return pick == null ? -1 : pick.actionIndex();
	}

	/**
	 * Like {@link #hitTestDanmaku} but returns the front-most danmaku entity itself
	 * (or null), so the hover layer can highlight it precisely.
	 */
	private ProjectilePick pickProjectile(double screenX, double screenY) {
		if (viewport.isPerspectiveMode()) return null; // worldToScreen is ortho-only

		double pixelTolSq = 8.0 * 8.0;
		double bestDepth = Double.POSITIVE_INFINITY;
		ProjectilePick best = null;

		for (var entity : scene.getHolder().getLocalEntities()) {
			if (entity instanceof ItemDanmakuEntity danmaku) {
				Vec3 sp = viewport.worldToScreen(entity.position());
				double dx = sp.x - screenX;
				double dy = sp.y - screenY;
				double distSq = dx * dx + dy * dy;
				if (distSq <= pixelTolSq && sp.z < bestDepth) {
					bestDepth = sp.z;
					best = new ProjectilePick(entity, danmaku.sourceActionIndex, sp);
				}
			} else if (entity instanceof dev.xkmc.youkaishomecoming.content.entity.danmaku.YHBaseLaserEntity laser) {
				Vec3 start = laser.position().add(0, laser.getBbHeight() / 2, 0);
				double visibleLength = laser.earlyTerminate < 0
						? laser.getLength() : Math.min(laser.getLength(), laser.earlyTerminate);
				Vec3 end = start.add(laser.getForward().scale(visibleLength));
				Vec3 screenStart = viewport.worldToScreen(start);
				Vec3 screenEnd = viewport.worldToScreen(end);
				double dx = screenEnd.x - screenStart.x;
				double dy = screenEnd.y - screenStart.y;
				double lengthSqr = dx * dx + dy * dy;
				double t = lengthSqr < 1.0e-8 ? 0.0 : Math.max(0.0, Math.min(1.0,
						((screenX - screenStart.x) * dx + (screenY - screenStart.y) * dy) / lengthSqr));
				Vec3 closest = new Vec3(screenStart.x + dx * t, screenStart.y + dy * t,
						screenStart.z + (screenEnd.z - screenStart.z) * t);
				double pickDx = closest.x - screenX;
				double pickDy = closest.y - screenY;
				double distSq = pickDx * pickDx + pickDy * pickDy;
				if (distSq <= pixelTolSq && closest.z < bestDepth) {
					bestDepth = closest.z;
					best = new ProjectilePick(entity, laser.sourceActionIndex, closest);
				}
			}
		}
		return best;
	}

	private record ProjectilePick(net.minecraft.world.entity.Entity entity, int actionIndex, Vec3 screenPoint) {
	}

	/**
	 * Handle key presses for rotation mode.
	 * R = enter rotate mode, X/Y/Z = switch axis, Escape = cancel.
	 */
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (!hasHighlightedGroup()) return false;

		// R = toggle rotate mode
		if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_R) {
			if (scene.isPlaying() || !groupRotationAvailable) return false;
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
