package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

import dev.xkmc.youkaishomecoming.content.spell.preview.OrthographicViewport;
import dev.xkmc.youkaishomecoming.content.spell.preview.PreviewCardHolder;
import dev.xkmc.youkaishomecoming.content.spell.preview.VirtualSpellScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 可停靠的播放控制面板。从 SpellPreviewScreen.init() 中提取
 * 所有底部控制按钮（播放/速度/距离/HP/Phase/Range/Target 属性等）。
 */
@OnlyIn(Dist.CLIENT)
public class ControlsDockPanel implements DockPanel {

	private static final int BUTTON_HEIGHT = 16;
	private static final int BUTTON_SPACING = 2;
	private static final int DROPDOWN_ITEM_H = 18;
	private static final int DROPDOWN_MAX_VISIBLE = 10;

	private final VirtualSpellScene scene;
	private final OrthographicViewport viewport;
	private final Runnable rebuildCallback;
	private final Runnable resetPhaseCallback;
	private final Supplier<List<ResourceLocation>> spellOptionsSupplier;
	private final Supplier<ResourceLocation> currentSpellIdSupplier;
	private final Supplier<String> currentSpellLabelSupplier;
	private final Function<ResourceLocation, String> spellDisplayFormatter;
	private final Consumer<ResourceLocation> switchSpellCallback;
	private final Runnable newSpellCallback;
	private final Runnable deleteSpellCallback;
	private final Supplier<Boolean> canDeleteSpellSupplier;
	private final Supplier<Boolean> spellDraftModeSupplier;
	private final Consumer<String> renameSpellCallback;
	private final Consumer<Integer> cyclePhaseCallback;
	private final Supplier<String> currentPhaseNameSupplier;
	private final Consumer<String> renamePhaseCallback;
	private final Runnable addPhaseCallback;
	private final Runnable deletePhaseCallback;
	private final Supplier<Boolean> canDeletePhaseSupplier;

	private int x, y, w, h;
	private final List<Button> buttons = new ArrayList<>();
	private final List<EditBox> editBoxes = new ArrayList<>();
	private Button spellDropdownButton;
	private Button spellNewButton;
	private Button spellDeleteButton;
	private DropdownOverlay spellDropdown;
	private int spellDropdownHoverIndex = -1;
	private int spellDropdownScrollOffset = 0;
	private ConfirmOverlay spellDeleteConfirm;
	private int spellDeleteConfirmHoverIndex = -1;
	private Consumer<AbstractWidget> addWidgetCallback;
	private Consumer<GuiEventListener> removeWidgetCallback;

	private record DropdownOverlay(
			List<ResourceLocation> values,
			String[] options,
			int selectedIndex
	) {}

	private record ConfirmOverlay(String[] options) {}

	public ControlsDockPanel(VirtualSpellScene scene,
							 OrthographicViewport viewport,
							 Runnable rebuildCallback,
							 Runnable resetPhaseCallback,
							 Supplier<List<ResourceLocation>> spellOptionsSupplier,
							 Supplier<ResourceLocation> currentSpellIdSupplier,
							 Supplier<String> currentSpellLabelSupplier,
							 Function<ResourceLocation, String> spellDisplayFormatter,
							 Consumer<ResourceLocation> switchSpellCallback,
							 Runnable newSpellCallback,
							 Runnable deleteSpellCallback,
							 Supplier<Boolean> canDeleteSpellSupplier,
							 Supplier<Boolean> spellDraftModeSupplier,
							 Consumer<String> renameSpellCallback,
							 Consumer<Integer> cyclePhaseCallback,
							 Supplier<String> currentPhaseNameSupplier,
							 Consumer<String> renamePhaseCallback,
							 Runnable addPhaseCallback,
							 Runnable deletePhaseCallback,
							 Supplier<Boolean> canDeletePhaseSupplier) {
		this.scene = scene;
		this.viewport = viewport;
		this.rebuildCallback = rebuildCallback;
		this.resetPhaseCallback = resetPhaseCallback;
		this.spellOptionsSupplier = spellOptionsSupplier;
		this.currentSpellIdSupplier = currentSpellIdSupplier;
		this.currentSpellLabelSupplier = currentSpellLabelSupplier;
		this.spellDisplayFormatter = spellDisplayFormatter;
		this.switchSpellCallback = switchSpellCallback;
		this.newSpellCallback = newSpellCallback;
		this.deleteSpellCallback = deleteSpellCallback;
		this.canDeleteSpellSupplier = canDeleteSpellSupplier;
		this.spellDraftModeSupplier = spellDraftModeSupplier;
		this.renameSpellCallback = renameSpellCallback;
		this.cyclePhaseCallback = cyclePhaseCallback;
		this.currentPhaseNameSupplier = currentPhaseNameSupplier;
		this.renamePhaseCallback = renamePhaseCallback;
		this.addPhaseCallback = addPhaseCallback;
		this.deletePhaseCallback = deletePhaseCallback;
		this.canDeletePhaseSupplier = canDeletePhaseSupplier;
	}

	/**
	 * 设置 widget 注册/注销回调。必须在 init() 前调用。
	 */
	public void setWidgetCallbacks(Consumer<AbstractWidget> addWidget, Consumer<GuiEventListener> removeWidget) {
		this.addWidgetCallback = addWidget;
		this.removeWidgetCallback = removeWidget;
	}

	/**
	 * 创建所有控制按钮并注册到 Screen。
	 * 在 Screen.init() 或 setBounds 后调用。
	 */
	public void buildButtons() {
		clearButtons();
		boolean draftMode = isDraftMode();

		int row1Y = y + 4;
		int row2Y = row1Y + BUTTON_HEIGHT + BUTTON_SPACING;
		int row3Y = row2Y + BUTTON_HEIGHT + BUTTON_SPACING;
		int row4Y = row3Y + BUTTON_HEIGHT + BUTTON_SPACING;
		int row5Y = row4Y + BUTTON_HEIGHT + BUTTON_SPACING;
		int row6Y = row5Y + BUTTON_HEIGHT + BUTTON_SPACING;
		int row7Y = row6Y + BUTTON_HEIGHT + BUTTON_SPACING;
		int row8Y = row7Y + BUTTON_HEIGHT + BUTTON_SPACING;

		int bx;

		bx = x + 4;
		if (draftMode) {
			addSpellControls(bx, row1Y, true);
			return;
		}

		// Row 1: Playback controls
		bx = addButton(bx, row1Y, 40, "\u25B6/\u275A\u275A", btn -> scene.togglePlayPause());
		bx = addButton(bx, row1Y, 20, "\u25A0", btn -> resetPhaseCallback.run());
		bx = addButton(bx, row1Y, 20, "\u25B8", btn -> scene.step());
		bx += 8;
		addSpellControls(bx, row1Y, false);

		// Row 2: Speed buttons + Safety limit
		bx = x + 4;
		for (int i = 0; i < VirtualSpellScene.SPEED_OPTIONS.length; i++) {
			float speed = VirtualSpellScene.SPEED_OPTIONS[i];
			String label = speed < 1 ? speed + "x" : ((int) speed) + "x";
			final int idx = i;
			bx = addButton(bx, row2Y, 36, label, btn -> scene.setSpeedIndex(idx));
		}
		bx += 10;
		int curLimit = PreviewCardHolder.getMaxEntityCount();
		String limitHint = curLimit >= 1000 ? "Limit:" + (curLimit / 1000) + "k" : "Limit:" + curLimit;
		bx = addEditBox(bx, row2Y, 52, limitHint, val -> {
			try {
				String s = val.toLowerCase().replace("k", "000").trim();
				PreviewCardHolder.setMaxEntityCount(Integer.parseInt(s));
			} catch (NumberFormatException ignored) {}
		});
		for (int lim : new int[]{10_000, 50_000, 100_000, 500_000}) {
			String limLabel = lim >= 1000 ? (lim / 1000) + "k" : String.valueOf(lim);
			final int fl = lim;
			bx = addButton(bx, row2Y, 30, limLabel, btn -> {
				PreviewCardHolder.setMaxEntityCount(fl);
				rebuildCallback.run();
			});
		}

		// Row 3: Distance + HP
		bx = x + 4;
		bx = addEditBox(bx, row3Y, 46, "Dist:" + (int) scene.getTargetDistance(), val -> {
			try { scene.setTargetDistance(Float.parseFloat(val)); } catch (NumberFormatException ignored) {}
		});
		for (float dist : VirtualSpellScene.DISTANCE_OPTIONS) {
			final float d = dist;
			bx = addButton(bx, row3Y, 24, String.valueOf((int) dist), btn -> scene.setTargetDistance(d));
		}
		bx += 10;
		bx = addEditBox(bx, row3Y, 46, "HP:" + (int) (scene.getHealthRatio() * 100) + "%", val -> {
			try {
				String s = val.replace("%", "").trim();
				float v = Float.parseFloat(s);
				if (v > 1) v = v / 100f;
				scene.setHealthRatio(v);
			} catch (NumberFormatException ignored) {}
		});
		for (float hp : VirtualSpellScene.HP_OPTIONS) {
			String hpLabel = ((int) (hp * 100)) + "%";
			final float h = hp;
			bx = addButton(bx, row3Y, 30, hpLabel, btn -> scene.setHealthRatio(h));
		}

		// Row 4: Phase selection
		bx = x + 4;
		bx = addButton(bx, row4Y, 40, "Label:", btn -> {});
		bx = addButton(bx, row4Y, 16, "<", btn -> cyclePhaseCallback.accept(-1));
		bx = addTextEditBox(bx, row4Y, 84,
				currentPhaseNameSupplier.get(),
				"Display Name", 48,
				s -> !s.contains("\n") && !s.contains("\r"),
				renamePhaseCallback);
		bx = addButton(bx, row4Y, 16, ">", btn -> cyclePhaseCallback.accept(1));
		bx = addButton(bx, row4Y, 20, "+", btn -> addPhaseCallback.run());
		Button deleteButton = Button.builder(Component.literal("-"), btn -> deletePhaseCallback.run())
				.bounds(bx, row4Y, 20, BUTTON_HEIGHT).build();
		deleteButton.active = canDeletePhaseSupplier.get();
		buttons.add(deleteButton);
		if (addWidgetCallback != null) {
			addWidgetCallback.accept(deleteButton);
		}

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
		bx = addEditBox(bx, row6Y, 48, "THP:" + (int) (scene.getTargetHealthRatio() * 100) + "%", val -> {
			try {
				String s = val.replace("%", "").trim();
				float v = Float.parseFloat(s);
				if (v > 1) v = v / 100f;
				scene.setTargetHealthRatio(v);
				rebuildCallback.run();
			} catch (NumberFormatException ignored) {}
		});
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
		bx = addEditBox(bx, row7Y, 46, "Height:" + (int) scene.getTargetHeight(), val -> {
			try {
				scene.setTargetHeight(Double.parseDouble(val));
				rebuildCallback.run();
			} catch (NumberFormatException ignored) {}
		});
		for (double hgt : new double[]{0, 1, 2, 5, 10, 20}) {
			String hLabel = String.valueOf((int) hgt);
			final double finalH = hgt;
			bx = addButton(bx, row7Y, 22, hLabel, btn -> {
				scene.setTargetHeight(finalH);
				rebuildCallback.run();
			});
		}

		// Row 8: Focus + Reset position
		bx = x + 4;
		bx = addButton(bx, row8Y, 52, "FocusTgt", btn -> {
			viewport.focusOnWorldPos(scene.getTargetPos());
		});
		bx = addButton(bx, row8Y, 58, "FocusCstr", btn -> {
			viewport.focusOnWorldPos(scene.getCasterPos());
		});
		bx += 10;
		bx = addButton(bx, row8Y, 52, "RstTgtPos", btn -> {
			scene.resetTargetPos();
			rebuildCallback.run();
		});
		addButton(bx, row8Y, 56, "RstCstrPos", btn -> {
			scene.resetCasterPos();
			rebuildCallback.run();
		});
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

	private void addSpellControls(int bx, int by, boolean draftMode) {
		int labelW = 36;
		int dropdownW = Math.max(96, Math.min(220, w / 4));
		int nextX = addButton(bx, by, labelW, "Spell:", btn -> {});
		String currentLabel = currentSpellLabelSupplier.get();
		String displayText = fitToWidth(currentLabel, dropdownW - 14);
		Button btn = Button.builder(Component.literal(displayText + " \u25BC"), b -> openSpellDropdown())
				.bounds(nextX, by, dropdownW, BUTTON_HEIGHT).build();
		spellDropdownButton = btn;
		buttons.add(btn);
		if (addWidgetCallback != null) {
			addWidgetCallback.accept(btn);
		}
		int newX = nextX + dropdownW + BUTTON_SPACING;
		Button newBtn = Button.builder(Component.literal("+"), b -> newSpellCallback.run())
				.bounds(newX, by, 20, BUTTON_HEIGHT).build();
		newBtn.active = !draftMode;
		spellNewButton = newBtn;
		buttons.add(newBtn);
		if (addWidgetCallback != null) {
			addWidgetCallback.accept(newBtn);
		}
		int deleteX = newX + 20 + BUTTON_SPACING;
		Button deleteBtn = Button.builder(Component.literal("-"), b -> openSpellDeleteConfirm())
				.bounds(deleteX, by, 20, BUTTON_HEIGHT).build();
		deleteBtn.active = !draftMode && canDeleteSpellSupplier.get();
		spellDeleteButton = deleteBtn;
		buttons.add(deleteBtn);
		if (addWidgetCallback != null) {
			addWidgetCallback.accept(deleteBtn);
		}
		if (draftMode) {
			int inputX = deleteX + 20 + 8;
			int inputW = Math.max(120, Math.min(220, w / 3));
			addTextEditBox(inputX, by, inputW,
					"", "New Spell ID", 96,
					s -> !s.contains("\n") && !s.contains("\r") && s.indexOf(' ') < 0,
					renameSpellCallback);
		}
	}

	private int addEditBox(int bx, int by, int bw, String hint, java.util.function.Consumer<String> onSubmit) {
		return addTextEditBox(bx, by, bw, "", hint, 16, s -> s.matches("[0-9.%\\-]*"), onSubmit);
	}

	private int addTextEditBox(int bx, int by, int bw, String value, String hint, int maxLength,
							   java.util.function.Predicate<String> filter,
							   java.util.function.Consumer<String> onSubmit) {
		EditBox box = new EditBox(Minecraft.getInstance().font, bx, by, bw, BUTTON_HEIGHT, Component.empty());
		box.setMaxLength(maxLength);
		box.setValue(value);
		box.setHint(Component.literal(hint).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
		box.setResponder(val -> {}); // no live response
		box.setFilter(filter::test);
		editBoxes.add(box);
		if (addWidgetCallback != null) {
			addWidgetCallback.accept(box);
		}
		editBoxSubmits.put(box, onSubmit);
		return bx + bw + BUTTON_SPACING;
	}

	// Map from EditBox to its submit callback
	private final java.util.Map<EditBox, java.util.function.Consumer<String>> editBoxSubmits = new java.util.HashMap<>();

	public void clearButtons() {
		closeSpellDropdown();
		closeSpellDeleteConfirm();
		if (removeWidgetCallback != null) {
			for (Button btn : buttons) {
				removeWidgetCallback.accept(btn);
			}
			for (EditBox box : editBoxes) {
				removeWidgetCallback.accept(box);
			}
		}
		buttons.clear();
		editBoxes.clear();
		editBoxSubmits.clear();
		spellDropdownButton = null;
		spellNewButton = null;
		spellDeleteButton = null;
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
		graphics.fill(x, y, x + w, y + h, 0xCC000000);
		Font font = Minecraft.getInstance().font;
		int row1Y = y + 4;
		if (isDraftMode()) {
			graphics.drawString(font,
					"Select an existing spell or enter a new spell id and press Enter.",
					x + 4, row1Y + BUTTON_HEIGHT + BUTTON_SPACING + 4, 0xFFCCCCCC, false);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (spellDeleteConfirm != null) {
			if (handleSpellDeleteConfirmClick(mouseX, mouseY)) {
				return true;
			}
			closeSpellDeleteConfirm();
			return true;
		}
		if (spellDropdown != null) {
			if (handleSpellDropdownClick(mouseX, mouseY)) {
				return true;
			}
			closeSpellDropdown();
			return true;
		}
		// 按钮和 EditBox 的点击由 Screen widget 系统处理
		return false;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (spellDeleteConfirm != null) {
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
				closeSpellDeleteConfirm();
			}
			return true;
		}
		if (spellDropdown != null) {
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
				closeSpellDropdown();
			}
			return true;
		}
		// Enter key submits focused EditBox value
		if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
			for (EditBox box : editBoxes) {
				if (box.isFocused()) {
					var submit = editBoxSubmits.get(box);
					if (submit != null) {
						submit.accept(box.getValue());
					}
					box.setFocused(false);
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (spellDeleteConfirm != null) {
			return true;
		}
		if (spellDropdown == null) {
			return false;
		}
		String[] options = spellDropdown.options();
		if (options == null || options.length == 0) {
			return true;
		}
		int[] bounds = computeSpellDropdownBounds();
		int visibleItems = Math.max(1, bounds[4]);
		int maxScroll = Math.max(0, options.length - visibleItems);
		spellDropdownScrollOffset = Math.max(0, Math.min(maxScroll,
				spellDropdownScrollOffset - (int) (delta * 3)));
		return true;
	}

	@Override
	public void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
		doRenderSpellDropdown(graphics, mouseX, mouseY);
		doRenderSpellDeleteConfirm(graphics, mouseX, mouseY);
	}

	@Override
	public void onActivated() {
		for (Button btn : buttons) btn.visible = true;
		for (EditBox box : editBoxes) box.visible = true;
	}

	@Override
	public void onDeactivated() {
		closeSpellDropdown();
		closeSpellDeleteConfirm();
		for (Button btn : buttons) btn.visible = false;
		for (EditBox box : editBoxes) box.visible = false;
	}

	public boolean isDropdownOpen() {
		return spellDropdown != null || spellDeleteConfirm != null;
	}

	private boolean isDraftMode() {
		return spellDraftModeSupplier.get();
	}

	private void openSpellDropdown() {
		List<ResourceLocation> values = spellOptionsSupplier.get();
		if (values == null || values.isEmpty() || spellDropdownButton == null) {
			return;
		}
		closeSpellDeleteConfirm();
		String[] options = new String[values.size()];
		ResourceLocation current = currentSpellIdSupplier.get();
		int selectedIndex = -1;
		for (int i = 0; i < values.size(); i++) {
			ResourceLocation value = values.get(i);
			options[i] = formatSpellOption(value);
			if (selectedIndex < 0 && java.util.Objects.equals(value, current)) {
				selectedIndex = i;
			}
		}
		spellDropdown = new DropdownOverlay(List.copyOf(values), options, selectedIndex);
		spellDropdownHoverIndex = -1;
		int visibleItems = Math.min(options.length, DROPDOWN_MAX_VISIBLE);
		int maxScroll = Math.max(0, options.length - visibleItems);
		if (selectedIndex >= visibleItems) {
			spellDropdownScrollOffset = selectedIndex - visibleItems + 1;
		} else {
			spellDropdownScrollOffset = 0;
		}
		spellDropdownScrollOffset = Math.max(0, Math.min(maxScroll, spellDropdownScrollOffset));
	}

	private void closeSpellDropdown() {
		spellDropdown = null;
		spellDropdownHoverIndex = -1;
		spellDropdownScrollOffset = 0;
	}

	private void openSpellDeleteConfirm() {
		if (spellDeleteButton == null || !canDeleteSpellSupplier.get()) {
			return;
		}
		closeSpellDropdown();
		ResourceLocation currentSpellId = currentSpellIdSupplier.get();
		String label = currentSpellId == null ? "current spell" : fitToWidth(formatSpellOption(currentSpellId), 150);
		spellDeleteConfirm = new ConfirmOverlay(new String[]{
				"Cancel",
				"Delete " + label
		});
		spellDeleteConfirmHoverIndex = -1;
	}

	private void closeSpellDeleteConfirm() {
		spellDeleteConfirm = null;
		spellDeleteConfirmHoverIndex = -1;
	}

	private String formatSpellOption(ResourceLocation spellId) {
		String formatted = spellDisplayFormatter.apply(spellId);
		if (formatted == null || formatted.isBlank()) {
			return spellId.toString();
		}
		return formatted;
	}

	private String fitToWidth(String text, int width) {
		Font font = Minecraft.getInstance().font;
		if (text == null || text.isEmpty() || font.width(text) <= width) {
			return text;
		}
		String ellipsis = "...";
		String clipped = font.plainSubstrByWidth(text, Math.max(0, width - font.width(ellipsis)));
		return clipped + ellipsis;
	}

	private int[] computeSpellDropdownBounds() {
		if (spellDropdown == null || spellDropdownButton == null) {
			return new int[]{0, 0, 0, 0, 0};
		}
		String[] options = spellDropdown.options();
		if (options == null || options.length == 0) {
			return new int[]{0, 0, 0, 0, 0};
		}
		int visibleItems = Math.min(options.length, DROPDOWN_MAX_VISIBLE);
		int totalH = visibleItems * DROPDOWN_ITEM_H;
		int dropdownX = spellDropdownButton.getX();
		int dropdownY = spellDropdownButton.getY() + spellDropdownButton.getHeight();
		int dropdownW = spellDropdownButton.getWidth();
		if (dropdownY + totalH > y + h) {
			dropdownY = spellDropdownButton.getY() - totalH;
		}
		if (dropdownY < y) {
			dropdownY = y;
		}
		if (dropdownY + totalH > y + h) {
			totalH = y + h - dropdownY;
		}
		if (totalH < DROPDOWN_ITEM_H) {
			totalH = DROPDOWN_ITEM_H;
		}
		return new int[]{dropdownX, dropdownY, dropdownW, totalH, Math.max(1, totalH / DROPDOWN_ITEM_H)};
	}

	private int[] computeSpellDeleteConfirmBounds() {
		if (spellDeleteConfirm == null || spellDeleteButton == null) {
			return new int[]{0, 0, 0, 0, 0};
		}
		String[] options = spellDeleteConfirm.options();
		if (options == null || options.length == 0) {
			return new int[]{0, 0, 0, 0, 0};
		}
		Font font = Minecraft.getInstance().font;
		int dropdownW = 120;
		for (String option : options) {
			dropdownW = Math.max(dropdownW, font.width(option) + 18);
		}
		dropdownW = Math.min(dropdownW, Math.max(120, w / 3));
		int totalH = options.length * DROPDOWN_ITEM_H;
		int dropdownX = spellDeleteButton.getX() + spellDeleteButton.getWidth() - dropdownW;
		if (dropdownX < x) {
			dropdownX = x;
		}
		int dropdownY = spellDeleteButton.getY() + spellDeleteButton.getHeight();
		if (dropdownY + totalH > y + h) {
			dropdownY = spellDeleteButton.getY() - totalH;
		}
		if (dropdownY < y) {
			dropdownY = y;
		}
		if (dropdownY + totalH > y + h) {
			totalH = y + h - dropdownY;
		}
		if (totalH < DROPDOWN_ITEM_H) {
			totalH = DROPDOWN_ITEM_H;
		}
		return new int[]{dropdownX, dropdownY, dropdownW, totalH, options.length};
	}

	private void doRenderSpellDropdown(GuiGraphics graphics, int mouseX, int mouseY) {
		if (spellDropdown == null) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		int[] bounds = computeSpellDropdownBounds();
		int dx = bounds[0], dy = bounds[1], dw = bounds[2], dh = bounds[3];
		int visibleItems = bounds[4];
		String[] options = spellDropdown.options();
		if (options == null || options.length == 0) {
			return;
		}

		boolean needsScroll = options.length > visibleItems;
		int scrollbarW = needsScroll ? 6 : 0;

		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 200);
		graphics.fill(dx + 3, dy + 3, dx + dw + 3, dy + dh + 3, 0x88000000);
		graphics.fill(dx, dy, dx + dw, dy + dh, 0xFF1a1a30);
		graphics.fill(dx, dy, dx + dw, dy + 1, 0xFF666688);
		graphics.fill(dx, dy + dh - 1, dx + dw, dy + dh, 0xFF666688);
		graphics.fill(dx, dy, dx + 1, dy + dh, 0xFF666688);
		graphics.fill(dx + dw - 1, dy, dx + dw, dy + dh, 0xFF666688);

		spellDropdownHoverIndex = -1;
		int contentW = dw - scrollbarW;
		if (mouseX >= dx && mouseX < dx + contentW && mouseY >= dy && mouseY < dy + dh) {
			int rawIdx = (mouseY - dy) / DROPDOWN_ITEM_H + spellDropdownScrollOffset;
			if (rawIdx >= 0 && rawIdx < options.length) {
				spellDropdownHoverIndex = rawIdx;
			}
		}

		int visCount = Math.min(options.length, dh / DROPDOWN_ITEM_H);
		for (int i = 0; i < visCount; i++) {
			int optIdx = i + spellDropdownScrollOffset;
			if (optIdx >= options.length) {
				break;
			}
			int itemY = dy + i * DROPDOWN_ITEM_H;
			boolean isHovered = optIdx == spellDropdownHoverIndex;
			boolean isSelected = optIdx == spellDropdown.selectedIndex();
			if (isHovered) {
				graphics.fill(dx + 1, itemY, dx + contentW - 1, itemY + DROPDOWN_ITEM_H, 0x44FFFFFF);
			}
			int textX = dx + 4;
			if (isSelected) {
				graphics.drawString(font, "\u25B6", dx + 3, itemY + 4, 0xFFFFCC44, false);
				textX = dx + 14;
			}
			int textColor = isHovered ? 0xFFFFDD66 : (isSelected ? 0xFFFFCC88 : 0xFFDDDDDD);
			graphics.drawString(font, options[optIdx], textX, itemY + 4, textColor, false);
		}

		if (needsScroll) {
			int sbX = dx + dw - scrollbarW;
			graphics.fill(sbX, dy, sbX + scrollbarW, dy + dh, 0x33FFFFFF);
			int trackH = dh - 2;
			int thumbH = Math.max(10, trackH * visibleItems / options.length);
			int maxScroll = Math.max(1, options.length - visibleItems);
			int thumbTravel = trackH - thumbH;
			if (thumbTravel > 0) {
				int thumbY = dy + 1 + thumbTravel * spellDropdownScrollOffset / maxScroll;
				graphics.fill(sbX + 1, thumbY, sbX + scrollbarW - 1, thumbY + thumbH, 0xAAAAAACC);
			}
		}
		graphics.pose().popPose();
	}

	private void doRenderSpellDeleteConfirm(GuiGraphics graphics, int mouseX, int mouseY) {
		if (spellDeleteConfirm == null) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		String[] options = spellDeleteConfirm.options();
		int[] bounds = computeSpellDeleteConfirmBounds();
		int dx = bounds[0], dy = bounds[1], dw = bounds[2], dh = bounds[3];

		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 200);
		graphics.fill(dx + 3, dy + 3, dx + dw + 3, dy + dh + 3, 0x88000000);
		graphics.fill(dx, dy, dx + dw, dy + dh, 0xFF301818);
		graphics.fill(dx, dy, dx + dw, dy + 1, 0xFFAA6666);
		graphics.fill(dx, dy + dh - 1, dx + dw, dy + dh, 0xFFAA6666);
		graphics.fill(dx, dy, dx + 1, dy + dh, 0xFFAA6666);
		graphics.fill(dx + dw - 1, dy, dx + dw, dy + dh, 0xFFAA6666);

		spellDeleteConfirmHoverIndex = -1;
		if (mouseX >= dx && mouseX < dx + dw && mouseY >= dy && mouseY < dy + dh) {
			int rawIdx = (mouseY - dy) / DROPDOWN_ITEM_H;
			if (rawIdx >= 0 && rawIdx < options.length) {
				spellDeleteConfirmHoverIndex = rawIdx;
			}
		}

		for (int i = 0; i < options.length; i++) {
			int itemY = dy + i * DROPDOWN_ITEM_H;
			boolean isHovered = i == spellDeleteConfirmHoverIndex;
			if (isHovered) {
				graphics.fill(dx + 1, itemY, dx + dw - 1, itemY + DROPDOWN_ITEM_H, 0x44FFFFFF);
			}
			int color = i == 0 ? 0xFFDDDDDD : (isHovered ? 0xFFFF8888 : 0xFFFF6666);
			graphics.drawString(font, options[i], dx + 6, itemY + 4, color, false);
		}
		graphics.pose().popPose();
	}

	private boolean handleSpellDropdownClick(double mouseX, double mouseY) {
		if (spellDropdown == null) {
			return false;
		}
		int[] bounds = computeSpellDropdownBounds();
		int dx = bounds[0], dy = bounds[1], dw = bounds[2], dh = bounds[3];
		int visibleItems = bounds[4];
		boolean needsScroll = spellDropdown.options().length > visibleItems;
		int scrollbarW = needsScroll ? 6 : 0;
		int contentW = dw - scrollbarW;
		if (mouseX >= dx && mouseX < dx + contentW && mouseY >= dy && mouseY < dy + dh) {
			int visIdx = (int) ((mouseY - dy) / DROPDOWN_ITEM_H);
			int optIdx = visIdx + spellDropdownScrollOffset;
			if (optIdx >= 0 && optIdx < spellDropdown.values().size()) {
				ResourceLocation selected = spellDropdown.values().get(optIdx);
				closeSpellDropdown();
				switchSpellCallback.accept(selected);
				return true;
			}
		}
		return false;
	}

	private boolean handleSpellDeleteConfirmClick(double mouseX, double mouseY) {
		if (spellDeleteConfirm == null) {
			return false;
		}
		int[] bounds = computeSpellDeleteConfirmBounds();
		int dx = bounds[0], dy = bounds[1], dw = bounds[2], dh = bounds[3];
		if (mouseX >= dx && mouseX < dx + dw && mouseY >= dy && mouseY < dy + dh) {
			int idx = (int) ((mouseY - dy) / DROPDOWN_ITEM_H);
			closeSpellDeleteConfirm();
			if (idx == 1) {
				deleteSpellCallback.run();
			}
			return true;
		}
		return false;
	}
}
