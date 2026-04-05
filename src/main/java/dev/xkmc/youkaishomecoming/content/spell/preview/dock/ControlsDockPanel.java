package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

import dev.xkmc.youkaishomecoming.content.spell.preview.OrthographicViewport;
import dev.xkmc.youkaishomecoming.content.spell.preview.VirtualSpellScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 可停靠的播放控制面板。从 SpellPreviewScreen.init() 中提取
 * 所有底部控制按钮（播放/速度/距离/HP/Phase/Range/Target 属性等）。
 */
@OnlyIn(Dist.CLIENT)
public class ControlsDockPanel implements DockPanel {

	private static final int BUTTON_HEIGHT = 16;
	private static final int BUTTON_SPACING = 2;

	private final VirtualSpellScene scene;
	private final OrthographicViewport viewport;
	private final Runnable rebuildCallback;
	private final Consumer<Integer> cyclePhaseCallback;

	private int x, y, w, h;
	private final List<Button> buttons = new ArrayList<>();
	private Consumer<Button> addWidgetCallback;
	private Consumer<GuiEventListener> removeWidgetCallback;

	public ControlsDockPanel(VirtualSpellScene scene,
							 OrthographicViewport viewport,
							 Runnable rebuildCallback,
							 Consumer<Integer> cyclePhaseCallback) {
		this.scene = scene;
		this.viewport = viewport;
		this.rebuildCallback = rebuildCallback;
		this.cyclePhaseCallback = cyclePhaseCallback;
	}

	/**
	 * 设置 widget 注册/注销回调。必须在 init() 前调用。
	 */
	public void setWidgetCallbacks(Consumer<Button> addWidget, Consumer<GuiEventListener> removeWidget) {
		this.addWidgetCallback = addWidget;
		this.removeWidgetCallback = removeWidget;
	}

	/**
	 * 创建所有控制按钮并注册到 Screen。
	 * 在 Screen.init() 或 setBounds 后调用。
	 */
	public void buildButtons() {
		clearButtons();

		int row1Y = y + 4;
		int row2Y = row1Y + BUTTON_HEIGHT + BUTTON_SPACING;
		int row3Y = row2Y + BUTTON_HEIGHT + BUTTON_SPACING;
		int row4Y = row3Y + BUTTON_HEIGHT + BUTTON_SPACING;
		int row5Y = row4Y + BUTTON_HEIGHT + BUTTON_SPACING;
		int row6Y = row5Y + BUTTON_HEIGHT + BUTTON_SPACING;
		int row7Y = row6Y + BUTTON_HEIGHT + BUTTON_SPACING;

		int bx;

		// Row 1: Playback controls
		bx = x + 4;
		bx = addButton(bx, row1Y, 40, "\u25B6/\u275A\u275A", btn -> scene.togglePlayPause());
		bx = addButton(bx, row1Y, 20, "\u25A0", btn -> scene.reset());
		addButton(bx, row1Y, 20, "\u25B8", btn -> scene.step());

		// Row 2: Speed buttons
		bx = x + 4;
		for (int i = 0; i < VirtualSpellScene.SPEED_OPTIONS.length; i++) {
			float speed = VirtualSpellScene.SPEED_OPTIONS[i];
			String label = speed < 1 ? speed + "x" : ((int) speed) + "x";
			final int idx = i;
			bx = addButton(bx, row2Y, 36, label, btn -> scene.setSpeedIndex(idx));
		}

		// Row 3: Distance + HP
		bx = x + 4;
		bx = addButton(bx, row3Y, 30, "Dist:", btn -> {});
		for (float dist : VirtualSpellScene.DISTANCE_OPTIONS) {
			final float d = dist;
			bx = addButton(bx, row3Y, 24, String.valueOf((int) dist), btn -> scene.setTargetDistance(d));
		}
		bx += 10;
		bx = addButton(bx, row3Y, 24, "HP:", btn -> {});
		for (float hp : VirtualSpellScene.HP_OPTIONS) {
			String hpLabel = ((int) (hp * 100)) + "%";
			final float h = hp;
			bx = addButton(bx, row3Y, 30, hpLabel, btn -> scene.setHealthRatio(h));
		}

		// Row 4: Phase selection
		bx = x + 4;
		bx = addButton(bx, row4Y, 40, "Phase:", btn -> {});
		bx = addButton(bx, row4Y, 16, "<", btn -> cyclePhaseCallback.accept(-1));
		addButton(bx + 100, row4Y, 16, ">", btn -> cyclePhaseCallback.accept(1));

		// Row 5: Range + Marker toggles
		bx = x + 4;
		int[] rangeOptions = {50, 100, 200, 500};
		bx = addButton(bx, row5Y, 40, "Range:", btn -> {});
		for (int range : rangeOptions) {
			final float r = range;
			bx = addButton(bx, row5Y, 30, String.valueOf(range), btn -> {
				viewport.setGridExtent(r);
				viewport.setClipDepth(r * 4);
			});
		}
		bx += 10;
		String casterMkLabel = viewport.isShowCasterMarker() ? "Caster:\u00A7cON" : "Caster:OFF";
		bx = addButton(bx, row5Y, 52, casterMkLabel, btn -> {
			viewport.setShowCasterMarker(!viewport.isShowCasterMarker());
			rebuildCallback.run();
		});
		String targetMkLabel = viewport.isShowTargetMarker() ? "Target:\u00A7eON" : "Target:OFF";
		addButton(bx, row5Y, 52, targetMkLabel, btn -> {
			viewport.setShowTargetMarker(!viewport.isShowTargetMarker());
			rebuildCallback.run();
		});

		// Row 6: Target properties
		bx = x + 4;
		bx = addButton(bx, row6Y, 42, "Target:", btn -> {});
		String groundLabel = scene.isTargetOnGround() ? "Ground:Y" : "Ground:N";
		bx = addButton(bx, row6Y, 52, groundLabel, btn -> {
			scene.setTargetOnGround(!scene.isTargetOnGround());
			rebuildCallback.run();
		});
		String flyLabel = scene.isTargetFlying() ? "Fly:Y" : "Fly:N";
		bx = addButton(bx, row6Y, 36, flyLabel, btn -> {
			scene.setTargetFlying(!scene.isTargetFlying());
			rebuildCallback.run();
		});
		String elytraLabel = scene.isTargetFallFlying() ? "Elytra:Y" : "Elytra:N";
		bx = addButton(bx, row6Y, 48, elytraLabel, btn -> {
			scene.setTargetFallFlying(!scene.isTargetFallFlying());
			rebuildCallback.run();
		});
		bx = addButton(bx, row6Y, 38, "TgtHP:", btn -> {});
		for (float hp : new float[]{0.25f, 0.5f, 0.75f, 1.0f}) {
			String thpLabel = ((int) (hp * 100)) + "%";
			final float h = hp;
			bx = addButton(bx, row6Y, 30, thpLabel, btn -> {
				scene.setTargetHealthRatio(h);
				rebuildCallback.run();
			});
		}

		// Row 7: Target Height
		bx = x + 4;
		bx = addButton(bx, row7Y, 32, "TgtY:", btn -> {});
		for (double hgt : new double[]{0, 1, 2, 5, 10, 20}) {
			String hLabel = String.valueOf((int) hgt);
			final double finalH = hgt;
			bx = addButton(bx, row7Y, 22, hLabel, btn -> {
				scene.setTargetHeight(finalH);
				rebuildCallback.run();
			});
		}
	}

	private int addButton(int bx, int by, int bw, String label, Button.OnPress action) {
		Button btn = Button.builder(Component.literal(label), action)
				.bounds(bx, by, bw, BUTTON_HEIGHT).build();
		buttons.add(btn);
		if (addWidgetCallback != null) {
			addWidgetCallback.accept(btn);
		}
		return bx + bw + BUTTON_SPACING;
	}

	public void clearButtons() {
		if (removeWidgetCallback != null) {
			for (Button btn : buttons) {
				removeWidgetCallback.accept(btn);
			}
		}
		buttons.clear();
	}

	// ---- DockPanel 基础实现 ----

	@Override
	public String dockTitle() {
		return "Controls";
	}

	@Override
	public String dockId() {
		return "controls";
	}

	@Override
	public void setBounds(int x, int y, int w, int h) {
		boolean moved = (this.x != x || this.y != y || this.w != w || this.h != h);
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		// 布局变化后重建按钮（按钮使用绝对坐标）
		if (moved && addWidgetCallback != null && !buttons.isEmpty()) {
			buildButtons();
		}
	}

	@Override
	public int getX() { return x; }

	@Override
	public int getY() { return y; }

	@Override
	public int getWidth() { return w; }

	@Override
	public int getHeight() { return h; }

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// 控制面板背景
		graphics.fill(x, y, x + w, y + h, 0xCC000000);

		// 播放状态信息
		Font font = Minecraft.getInstance().font;
		int row1Y = y + 4;
		String status = (scene.isPlaying() ? "\u25B6 " : "\u275A\u275A ") +
				"tick:" + scene.getTotalTick() +
				"  phase:" + scene.getCurrentPhaseId().getPath() +
				"  entities:" + scene.getEntityCount() +
				"  hits:" + scene.getHitCount() +
				"  speed:" + scene.getCurrentSpeed() + "x";
		graphics.drawString(font, status, x + 90, row1Y + 4, 0xFFCCCCCC, false);

		// 注意：按钮由 Screen 的 super.render() 统一渲染
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// 按钮的点击由 Screen widget 系统处理
		return false;
	}

	@Override
	public void onActivated() {
		for (Button btn : buttons) {
			btn.visible = true;
		}
	}

	@Override
	public void onDeactivated() {
		for (Button btn : buttons) {
			btn.visible = false;
		}
	}
}
